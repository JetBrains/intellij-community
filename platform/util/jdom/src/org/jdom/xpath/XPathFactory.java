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

import org.jdom.Element;
import org.jdom.JDOMConstants;
import org.jdom.Namespace;
import org.jdom.filter2.Filter;
import org.jdom.filter2.Filters;
import org.jdom.xpath.jaxen.JaxenXPathFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * XPathFactory allows JDOM users to configure which XPath implementation to use
 * when evaluating XPath expressions.
 * <p>
 * JDOM does not extend the core Java XPath API (javax.xml.xpath.XPath). Instead
 * it creates a new API that is more JDOM and Java friendly leading to neater
 * and more understandable code (in a JDOM context).
 * <p>
 * A JDOM XPathFactory instance is able to create JDOM XPathExpression instances
 * that can be used to evaluate XPath expressions against JDOM Content.
 * <p>
 * Instances of XPathFactory are specified to be thread-safe. You can reuse an
 * XPathFactory in multiple threads. Instances of XPathExpression are
 * <strong>NOT</strong> thread-safe.
 *
 * @author Rolf Lear
 * @since JDOM2
 */
public abstract class XPathFactory {
  private static final Namespace[] EMPTYNS = new Namespace[0];

  /**
   * An atomic reference storing an instance of the default XPathFactory.
   */
  private static final AtomicReference<XPathFactory> defaultreference = new AtomicReference<>();

  /**
   * Obtain an instance of an XPathFactory using the default mechanisms to
   * determine what XPathFactory implementation to use.
   * <p>
   * The exact same XPathFactory instance will be returned from each call.
   * <p>
   * The default mechanism will inspect the system property (only once)
   * {@link JDOMConstants#JDOM2_PROPERTY_XPATH_FACTORY} to determine what
   * class should be used for the XPathFactory. If that property is not set
   * then JDOM will use the {@link JaxenXPathFactory}.
   *
   * @return the default XPathFactory instance
   */
  public static XPathFactory instance() {
    final XPathFactory ret = defaultreference.get();
    if (ret != null) {
      return ret;
    }
    XPathFactory fac = new JaxenXPathFactory();
    if (defaultreference.compareAndSet(null, fac)) {
      return fac;
    }
    // someone else installed a different instance before we added ours.
    // return that other instance.
    return defaultreference.get();
  }

  /**
   * Create a Compiled XPathExpression&lt;&gt; instance from this factory. This
   * is the only abstract method on this class. All other compile and evaluate
   * methods prepare the data in some way to call this compile method.
   * <p>
   * XPathFactory implementations override this method to implement support
   * for the JDOM/XPath API.
   * <p>
   * A Filter is used to coerce resulting XPath data in to a suitable JDOM generic
   * type. Note that the {@link Filters} class has a number of predefined, useful
   * filters.
   * <p>
   * <h2>Namespace</h2> XPath expressions are always namespace aware, and
   * expect to be able to resolve prefixes to namespace URIs. In XPath
   * expressions the prefix "" always resolves to the empty Namespace URI "".
   * A prefix in an XPath query is expected to resolve to exactly one URI.
   * Multiple different prefixes in the expression may resolve to the same
   * URI.
   * <p>
   * This compile method ensures that these XPath/Namespace rules are followed
   * and thus this method will throw IllegalArgumentException if:
   * <ul>
   * <li>a namespace has the empty-string prefix but has a non-empty URI.
   * <li>more than one Namespace has any one prefix.
   * </ul>
   * <p>
   * <h2>Variables</h2>
   * <p>
   * Variables are referenced from XPath expressions using a
   * <code>$varname</code> syntax. The variable name may be a Namespace
   * qualified variable name of the form <code>$pfx:localname</code>.
   * Variables <code>$pa:var</code> and <code>$pb:var</code> are the identical
   * variables if the namespace URI for prefix 'pa' is the same URI as for
   * prefix 'pb'.
   * <p>
   * This compile method expects all variable names to be expressed in a
   * prefix-qualified format, where all prefixes have to be available in one
   * of the specified Namespace instances.
   * <p>
   * e.g. if you specify a variable name "ns:var" with value "value", you also
   * need to have some namespace provided with the prefix "ns" such as
   * <code>Namespace.getNamespace("ns", "http://example.com/nsuri");</code>
   * <p>
   * Some XPath libraries allow null variable values (Jaxen), some do not
   * (native Java). This compile method will silently convert any null
   * Variable value to an empty string <code>""</code>.
   * <p>
   * Variables are provided in the form of a Map where the key is the variable
   * name and the mapped value is the variable value. If the entire map is
   * null then the compile Method assumes there are no variables.
   * <p>
   * In light of the above, this compile method will throw an
   * IllegalArgumentException if:
   * <ul>
   * <li>a variable name is not a valid XML QName.
   * <li>The prefix associated with a variable name is not available as a
   * Namespace.
   * </ul>
   * A NullPointerException will be thrown if the map contains a null variable
   * name
   *
   * @param <T>        The generic type of the results that the XPathExpression will
   *                   produce.
   * @param expression The XPath expression.
   * @param filter     The Filter that is used to coerce the XPath result data in to the
   *                   generic-typed results.
   *                   Note that the {@link Filters} class has a number of predefined, useful
   *                   filters.
   * @param variables  Any variable values that may be referenced from the query. A null
   *                   value indicates that there are no variables.
   * @param namespaces Any namespaces that may be referenced from the query
   * @return an XPathExpression&lt;&gt; instance.
   * @throws NullPointerException     if the query, filter, any namespace, any variable name or any
   *                                  variable value is null (although the entire variables value may
   *                                  be null).
   * @throws IllegalArgumentException if any two Namespace values share the same prefix, or if there is
   *                                  any other reason that the XPath query cannot be compiled.
   * @see Filters
   */
  public abstract <T> XPathExpression<T> compile(String expression,
                                                 Filter<T> filter, Map<String, Object> variables,
                                                 Namespace... namespaces);

  /**
   * Create a XPathExpression&lt;&gt; instance from this factory.
   *
   * @param <T>        The generic type of the results that the XPathExpression will
   *                   produce.
   * @param expression The XPath expression.
   * @param filter     The Filter that is used to coerce the xpath result data in to the
   *                   generic-typed results.
   *                   Note that the {@link Filters} class has a number of predefined, useful
   *                   filters.
   * @param variables  Any variable values that may be referenced from the query. A null
   *                   value indicates that there are no variables.
   * @param namespaces List of all namespaces that may be referenced from the query
   * @return an XPathExpression&lt;T&gt; instance.
   * @throws NullPointerException     if the query, filter, namespaces, any variable name or any
   *                                  variable value is null (although the entire variables value may
   *                                  be null).
   * @throws IllegalArgumentException if any two Namespace values share the same prefix, or if there is
   *                                  any other reason that the XPath query cannot be compiled.
   */
  public <T> XPathExpression<T> compile(String expression, Filter<T> filter,
                                        Map<String, Object> variables, Collection<Namespace> namespaces) {
    return compile(expression, filter, variables, namespaces.toArray(EMPTYNS));
  }

  /**
   * Create a XPathExpression&lt;T&gt; instance from this factory.
   *
   * @param <T>        The generic type of the results that the XPathExpression will
   *                   produce.
   * @param expression The XPath expression.
   * @param filter     The Filter that is used to coerce the xpath result data in to the
   *                   generic-typed results.
   *                   Note that the {@link Filters} class has a number of predefined, useful
   *                   filters.
   * @return an XPathExpression&lt;T&gt; instance.
   * @throws NullPointerException     if the query or filter is null
   * @throws IllegalArgumentException if there is any reason that the XPath query cannot be compiled.
   */
  public <T> XPathExpression<T> compile(String expression, Filter<T> filter) {
    return compile(expression, filter, null, EMPTYNS);
  }

  public XPathExpression<Element> elements(String expression) {
    return compile(expression, Filters.element(), null, EMPTYNS);
  }

  /**
   * Create a XPathExpression&lt;Object&gt; instance from this factory.
   *
   * @param expression The XPath expression.
   * @return an XPathExpression&lt;Object&gt; instance.
   * @throws NullPointerException     if the query or filter is null
   * @throws IllegalArgumentException if there is any reason that the XPath query cannot be compiled.
   */
  public XPathExpression<Object> compile(String expression) {
    return compile(expression, Filters.fpassthrough(), null, EMPTYNS);
  }
}
