/*--

 Copyright (C) 2012 Jason Hunter & Brett McLaughlin.
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions, and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions, and the disclaimer that follows
    these conditions in the documentation and/or other materials
    provided with the distribution.

 3. The name "JDOM" must not be used to endorse or promote products
    derived from this software without prior written permission.  For
    written permission, please contact <request_AT_jdom_DOT_org>.

 4. Products derived from this software may not be called "JDOM", nor
    may "JDOM" appear in their name, without prior written permission
    from the JDOM Project Management <request_AT_jdom_DOT_org>.

 In addition, we request (but do not require) that you include in the
 end-user documentation provided with the redistribution and/or in the
 software itself an acknowledgement equivalent to the following:
     "This product includes software developed by the
      JDOM Project (http://www.jdom.org/)."
 Alternatively, the acknowledgment may be graphical using the logos
 available at http://www.jdom.org/images/logos.

 THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED.  IN NO EVENT SHALL THE JDOM AUTHORS OR THE PROJECT
 CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 SUCH DAMAGE.

 This software consists of voluntary contributions made by many
 individuals on behalf of the JDOM Project and was originally
 created by Jason Hunter <jhunter_AT_jdom_DOT_org> and
 Brett McLaughlin <brett_AT_jdom_DOT_org>.  For more information
 on the JDOM Project, please see <http://www.jdom.org/>.

 */

package org.jdom.xpath;

import org.jdom.Namespace;
import org.jdom.filter2.Filter;

import java.util.List;

/**
 * XPathExpression is a representation of a compiled XPath query and any
 * Namespace or variable references the query may require.
 * <p>
 * Once an XPathExpression is created, the values associated with variable names
 * can be changed. But new variables may not be added.
 * <p>
 * <p>
 * XPathExpression is not thread-safe. XPath's libraries allow variable values to
 * change between calls to their query routines, but require that the variable
 * value is constant for the duration of any particular evaluation. It is easier
 * to simply have separate XPathExpression instances in each thread than it is
 * to manage the synchronization of a single instance. XPathExpression thus
 * supports Cloneable to easily create another XPathExpression instance. It is
 * the responsibility of the JDOM caller to ensure appropriate synchronization
 * of the XPathExpression if it is accessed from multiple threads.
 *
 * @param <T> The generic type of the results of the XPath query after being
 *            processed by the JDOM {@code Filter<T>}
 * @author Rolf Lear
 */
public interface XPathExpression<T> extends Cloneable {

  /**
   * Create a new instance of this XPathExpression that duplicates this
   * instance.
   * <p>
   * The 'cloned' instance will have the same XPath query, namespace
   * declarations, and variables. Changing a value associated with a variable
   * on the cloned instance will not change this instance's values, and it is
   * safe to run the evaluate methods on the cloned copy at the same time as
   * this copy.
   *
   * @return a new XPathExpression instance that shares the same core details
   * as this.
   */
  XPathExpression<T> clone();

  /**
   * Get the XPath expression
   *
   * @return the string representation of the XPath expression
   */
  String getExpression();

  /**
   * Get the Namespace associated with a given prefix.
   *
   * @param prefix The prefix to select the Namespace URI for.
   * @return the URI of the specified Namespace prefix
   * @throws IllegalArgumentException if that prefix is not defined.
   */
  Namespace getNamespace(String prefix);

  /**
   * Get the Namespaces that were used to compile this XPathExpression.
   *
   * @return a potentially empty array of Namespaces (never null).
   */
  Namespace[] getNamespaces();

  /**
   * Change the defined value for a variable to some new value. You may not
   * use this method to add new variables to the compiled XPath, you can only
   * change existing variable values.
   * <p>
   * The value of the variable may be null. Some XPath libraries support a
   * null value, and if the library that this expression is for does not
   * support a null value it should be translated to something meaningful for
   * that library, typically the empty string.
   *
   * @param localname The variable localname to change.
   * @param uri       the Namespace in which the variable name is declared.
   * @param value     The new value to set.
   * @return The value of the variable prior to this change.
   * @throws NullPointerException     if name or uri is null
   * @throws IllegalArgumentException if name is not already a variable.
   */
  Object setVariable(String localname, Namespace uri, Object value);

  /**
   * Change the defined value for a variable to some new value. You may not
   * use this method to add new variables to the compiled XPath, you can only
   * change existing variable values.
   * <p>
   * The value of the variable may be null. Some XPath libraries support a
   * null value, and if the library that this expression is for does not
   * support a null value it should be translated to something meaningful for
   * that library, typically the empty string.
   * <p>
   * qname must consist of an optional namespace prefix and colon, followed
   * by a mandatory variable localname. If the prefix is not specified, then
   * the Namespace is assumed to be the {@link Namespace#NO_NAMESPACE}. If
   * the prefix is specified, it must match with one of the declared
   * Namespaces for this XPathExpression
   *
   * @param qname The variable qname to change.
   * @param value The new value to set.
   * @return The value of the variable prior to this change.
   * @throws NullPointerException     if qname is null
   * @throws IllegalArgumentException if name is not already a variable.
   */
  Object setVariable(String qname, Object value);

  /**
   * Get the variable value associated to the given variable name.
   *
   * @param localname the variable localname to retrieve the value for.
   * @param uri       the Namespace in which the variable name was declared.
   * @return the value associated to a Variable name.
   * @throws NullPointerException     if name or uri is null
   * @throws IllegalArgumentException if that variable name is not defined.
   */
  Object getVariable(String localname, Namespace uri);

  /**
   * Get the variable value associated to the given variable qname.
   * <p>
   * qname must consist of an optional namespace prefix and colon, followed
   * by a mandatory variable localname. If the prefix is not specified, then
   * the Namespace is assumed to be the {@link Namespace#NO_NAMESPACE}. If
   * the prefix is specified, it must match with one of the declared
   * Namespaces for this XPathExpression
   *
   * @param qname the variable qname to retrieve the value for.
   * @return the value associated to a Variable name.
   * @throws NullPointerException     if qname is null
   * @throws IllegalArgumentException if that variable name is not defined.
   */
  Object getVariable(String qname);

  /**
   * Get the {@code Filter<T>} used to coerce the raw XPath results in to
   * the correct Generic type.
   *
   * @return the {@code Filter<T>} used to coerce the raw XPath results in to
   * the correct Generic type.
   */
  Filter<T> getFilter();

  /**
   * Process the compiled XPathExpression against the specified context.
   * <p>
   * In the JDOM2 XPath API the results of the raw XPath query are processed
   * by the attached {@code Filter<T>} instance to coerce the results in to
   * the correct generic type for this XPathExpression. The Filter process may
   * cause some XPath results to be removed from the final results. You may
   * instead want to call the {@link #diagnose(Object, boolean)} method to
   * have access to both the raw XPath results as well as the filtered and
   * generically typed results.
   *
   * @param context The context against which to process the query.
   * @return a list of the XPath results.
   * @throws NullPointerException  if the context is null
   * @throws IllegalStateException if the expression is not runnable or if the context node is not
   *                               appropriate for the expression.
   */
  List<T> evaluate(Object context);

  /**
   * Return the first value in the XPath query result set type-cast to the
   * return type of this XPathExpression.
   * <p>
   * The concept of the 'first' result is applied before any JDOM Filter is
   * applied. Thus, if the underlying XPath query has some results, the first
   * result is sent through the filter. If it matches it is returned, if it
   * does not match, then null is returned (even if some subsequent result
   * underlying XPath result would pass the filter).
   * <p>
   * This allows the XPath implementation to optimise the evaluateFirst method
   * by potentially using 'short-circuit' conditions in the evaluation.
   * <p>
   *
   * @param context The context against which to evaluate the expression. This will
   *                typically be a Document, Element, or some other JDOM object.
   * @return The first XPath result (if there is any) coerced to the generic
   * type of this XPathExpression, or null if it cannot be coerced.
   * @throws NullPointerException  if the context is null
   * @throws IllegalStateException if the expression is not runnable or if the context node is not
   *                               appropriate for the expression.
   */
  T evaluateFirst(Object context);

  /**
   * Evaluate the XPath query against the supplied context, but return
   * additional data which may be useful for diagnosing problems with XPath
   * queries.
   *
   * @param context   The context against which to run the query.
   * @param firstonly Indicate whether the XPath expression can be terminated after the
   *                  first successful result value.
   * @return an {@link XPathDiagnostic} instance.
   * @throws NullPointerException  if the context is null
   * @throws IllegalStateException if the expression is not runnable or if the context node is not
   *                               appropriate for the expression.
   */
  XPathDiagnostic<T> diagnose(Object context, boolean firstonly);
}
