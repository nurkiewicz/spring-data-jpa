/*
 * Copyright 2008-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.jpa.repository.query;

import static org.springframework.data.jpa.repository.query.QueryUtils.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.ParameterExpression;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.data.domain.Sort;
import org.springframework.data.mapping.PropertyPath;
import org.springframework.data.repository.query.Parameter;
import org.springframework.data.repository.query.Parameters;
import org.springframework.data.repository.query.parser.AbstractQueryCreator;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.Part.Type;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Query creator to create a {@link CriteriaQuery} from a {@link PartTree}.
 * 
 * @author Oliver Gierke
 */
public class JpaQueryCreator extends AbstractQueryCreator<CriteriaQuery<Object>, Predicate> {

	private final CriteriaBuilder builder;
	private final Root<?> root;
	private final CriteriaQuery<Object> query;
	private final ParameterExpressionProvider provider;

	/**
	 * Create a new {@link JpaQueryCreator}.
	 * 
	 * @param tree
	 * @param domainClass
	 * @param accessor
	 * @param em
	 */
	public JpaQueryCreator(PartTree tree, Class<?> domainClass, Parameters parameters, EntityManager em) {

		super(tree);

		this.builder = em.getCriteriaBuilder();
		this.query = builder.createQuery().distinct(tree.isDistinct());
		this.root = query.from(domainClass);
		this.provider = new ParameterExpressionProvider(builder, parameters.getBindableParameters());
	}

	/**
	 * Returns all {@link ParameterExpression} created when creating the query.
	 * 
	 * @return the parameterExpressions
	 */
	public List<ParameterExpression<?>> getParameterExpressions() {

		return provider.getExpressions();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.springframework.data.repository.query.parser.AbstractQueryCreator
	 * #create(org.springframework.data.repository.query.parser.Part,
	 * java.util.Iterator)
	 */
	@Override
	protected Predicate create(Part part, Iterator<Object> iterator) {

		return toPredicate(part, root);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.springframework.data.repository.query.parser.AbstractQueryCreator
	 * #and(org.springframework.data.repository.query.parser.Part,
	 * java.lang.Object, java.util.Iterator)
	 */
	@Override
	protected Predicate and(Part part, Predicate base, Iterator<Object> iterator) {

		return builder.and(base, toPredicate(part, root));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.springframework.data.repository.query.parser.AbstractQueryCreator
	 * #or(java.lang.Object, java.lang.Object)
	 */
	@Override
	protected Predicate or(Predicate base, Predicate predicate) {

		return builder.or(base, predicate);
	}

	/**
	 * Finalizes the given {@link Predicate} and applies the given sort. Delegates to
	 * {@link #complete(Predicate, Sort, CriteriaQuery, CriteriaBuilder)} and hands it the current {@link CriteriaQuery}
	 * and {@link CriteriaBuilder}.
	 */
	@Override
	protected final CriteriaQuery<Object> complete(Predicate predicate, Sort sort) {

		return complete(predicate, sort, query, builder, root);
	}

	/**
	 * Template method to finalize the given {@link Predicate} using the given {@link CriteriaQuery} and
	 * {@link CriteriaBuilder}.
	 * 
	 * @param predicate
	 * @param sort
	 * @param query
	 * @param builder
	 * @return
	 */
	protected CriteriaQuery<Object> complete(Predicate predicate, Sort sort, CriteriaQuery<Object> query,
			CriteriaBuilder builder, Root<?> root) {

		return this.query.select(root).where(predicate).orderBy(QueryUtils.toOrders(sort, root, builder));
	}

	/**
	 * Creates a {@link Predicate} from the given {@link Part}.
	 * 
	 * @param part
	 * @param root
	 * @param iterator
	 * @return
	 */
	private Predicate toPredicate(Part part, Root<?> root) {

		return new PredicateBuilder(part, root).build();
	}

	/**
	 * Returns a path to a {@link Comparable}.
	 * 
	 * @param root
	 * @param part
	 * @return
	 */
	@SuppressWarnings({ "rawtypes" })
	private Expression<? extends Comparable> getComparablePath(Root<?> root, Part part) {

		return getTypedPath(root, part, Comparable.class);
	}

	private <T> Expression<? extends T> getTypedPath(Root<?> root, Part part, Class<T> type) {

		return toExpressionRecursively(root, part.getProperty());
	}

	/**
	 * Helper class to allow easy creation of {@link ParameterExpression}s.
	 * 
	 * @author Oliver Gierke
	 */
	static class ParameterExpressionProvider {

		private final CriteriaBuilder builder;
		private final Iterator<Parameter> parameters;
		private final List<ParameterExpression<?>> expressions;

		/**
		 * Creates a new {@link ParameterExpressionProvider} from the given {@link CriteriaBuilder} and {@link Parameters}.
		 * 
		 * @param builder
		 * @param parameters
		 */
		public ParameterExpressionProvider(CriteriaBuilder builder, Parameters parameters) {

			Assert.notNull(builder);
			Assert.notNull(parameters);

			this.builder = builder;
			this.parameters = parameters.iterator();
			this.expressions = new ArrayList<ParameterExpression<?>>();
		}

		/**
		 * Returns all {@link ParameterExpression}s built.
		 * 
		 * @return the expressions
		 */
		public List<ParameterExpression<?>> getExpressions() {

			return Collections.unmodifiableList(expressions);
		}

		/**
		 * Builds a new {@link ParameterExpression} for the next {@link Parameter}.
		 * 
		 * @param <T>
		 * @return
		 */
		@SuppressWarnings("unchecked")
		public <T> ParameterExpression<T> next() {

			Parameter parameter = parameters.next();
			return (ParameterExpression<T>) next(parameter.getType(), parameter.getName());
		}

		/**
		 * Builds a new {@link ParameterExpression} of the given type. Forwards the underlying {@link Parameters} as well.
		 * 
		 * @param <T>
		 * @param type must not be {@literal null}.
		 * @return
		 */
		@SuppressWarnings("unchecked")
		public <T> ParameterExpression<? extends T> next(Class<T> type) {

			Parameter parameter = parameters.next();
			Class<?> typeToUse = ClassUtils.isAssignable(type, parameter.getType()) ? parameter.getType() : type;
			return (ParameterExpression<? extends T>) next(typeToUse, null);
		}

		/**
		 * Builds a new {@link ParameterExpression} for the given type and name.
		 * 
		 * @param <T>
		 * @param type must not be {@literal null}.
		 * @param name
		 * @return
		 */
		private <T> ParameterExpression<T> next(Class<T> type, String name) {

			Assert.notNull(type);

			ParameterExpression<T> expression = name == null ? builder.parameter(type) : builder.parameter(type, name);
			expressions.add(expression);
			return expression;
		}
	}

	/**
	 * Simple builder to contain logic to create JPA {@link Predicate}s from {@link Part}s.
	 * 
	 * @author Phil Webb
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private class PredicateBuilder {

		private final Part part;
		private final Root<?> root;

		/**
		 * Creates a new {@link PredicateBuilder} for the given {@link Part} and {@link Root}.
		 * 
		 * @param part must not be {@literal null}.
		 * @param root must not be {@literal null}.
		 */
		public PredicateBuilder(Part part, Root<?> root) {

			Assert.notNull(part);
			Assert.notNull(root);
			this.part = part;
			this.root = root;
		}

		/**
		 * Builds a JPA {@link Predicate} from the underlying {@link Part}.
		 * 
		 * @return
		 */
		public Predicate build() {

			PropertyPath property = part.getProperty();
			Expression<Object> path = toExpressionRecursively(root, property);

			switch (part.getType()) {
			case BETWEEN:
				ParameterExpression<Comparable> first = provider.next();
				ParameterExpression<Comparable> second = provider.next();
				return builder.between(getComparablePath(root, part), first, second);
			case GREATER_THAN:
				return builder.greaterThan(getComparablePath(root, part), provider.next(Comparable.class));
			case GREATER_THAN_EQUAL:
				return builder.greaterThanOrEqualTo(getComparablePath(root, part), provider.next(Comparable.class));
			case LESS_THAN:
				return builder.lessThan(getComparablePath(root, part), provider.next(Comparable.class));
			case LESS_THAN_EQUAL:
				return builder.lessThanOrEqualTo(getComparablePath(root, part), provider.next(Comparable.class));
			case IS_NULL:
				return path.isNull();
			case IS_NOT_NULL:
				return path.isNotNull();
			case NOT_IN:
				return path.in(provider.next(Collection.class)).not();
			case IN:
				return path.in(provider.next(Collection.class));
			case LIKE:
			case NOT_LIKE:
				Expression<String> propertyExpression = upperIfIgnoreCase(getTypedPath(root, part, String.class));
				Expression<String> parameterExpression = upperIfIgnoreCase(provider.next(String.class));
				Predicate like = builder.like(propertyExpression, parameterExpression);
				return part.getType() == Type.LIKE ? like : like.not();
			case SIMPLE_PROPERTY:
				return builder.equal(upperIfIgnoreCase(path), upperIfIgnoreCase(provider.next()));
			case NEGATING_SIMPLE_PROPERTY:
				return builder.notEqual(upperIfIgnoreCase(path), upperIfIgnoreCase(provider.next()));
			default:
				throw new IllegalArgumentException("Unsupported keyword + " + part.getType());
			}
		}

		/**
		 * Applies an {@code UPPERCASE} conversion to the given {@link Expression} in case the underlying {@link Part}
		 * requires ignoring case.
		 * 
		 * @param expression must not be {@literal null}.
		 * @return
		 */
		private <T> Expression<T> upperIfIgnoreCase(Expression<? extends T> expression) {

			switch (part.shouldIgnoreCase()) {
			case ALWAYS:
				Assert.state(canUpperCase(expression), "Unable to ignore case of " + expression.getJavaType().getName()
						+ " types, the property '" + part.getProperty().getSegment() + "' must reference a String");
				return (Expression<T>) builder.upper((Expression<String>) expression);
			case WHEN_POSSIBLE:
				if (canUpperCase(expression)) {
					return (Expression<T>) builder.upper((Expression<String>) expression);
				}
			}
			return (Expression<T>) expression;
		}

		private boolean canUpperCase(Expression<?> expression) {
			return String.class.equals(expression.getJavaType());
		}
	}
}
