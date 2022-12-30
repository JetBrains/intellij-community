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

package org.jdom.xpath.jaxen;

import org.jaxen.*;
import org.jdom.Namespace;
import org.jdom.filter2.Filter;
import org.jdom.xpath.util.AbstractXPathCompiled;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Jaxen specific code for XPath management.
 *
 * @param <T> The generic type of returned data.
 * @author Rolf Lear
 */
final class JaxenCompiled<T> extends AbstractXPathCompiled<T> implements NamespaceContext, VariableContext {

  /**
   * Same story, need to be able to strip NamespaceContainer instances from
   * Namespace content.
   *
   * @param o A result object which could potentially be a NamespaceContainer
   * @return The input parameter unless it is a NamespaceContainer in which
   * case return the wrapped Namespace
   */
  private static Object unWrapNS(Object o) {
    if (o instanceof NamespaceContainer) {
      return ((NamespaceContainer)o).getNamespace();
    }
    return o;
  }

  /**
   * Same story, need to be able to replace NamespaceContainer instances with
   * Namespace content.
   *
   * @param results A list potentially containing NamespaceContainer instances
   * @return The parameter list with NamespaceContainer instances replaced by
   * the wrapped Namespace instances.
   */
  private static List<Object> unWrap(List<?> results) {
    final ArrayList<Object> ret = new ArrayList<>(results.size());
    for (Object result : results) {
      ret.add(unWrapNS(result));
    }
    return ret;
  }

  /**
   * The compiled XPath object to select nodes. This attribute can not be made
   * final as it needs to be set upon object deserialization.
   */
  private final XPath xPath;

  /**
   * @param expression The XPath expression
   * @param filter     The coercion filter
   * @param variables  The XPath variable context
   * @param namespaces The XPath namespace context
   */
  JaxenCompiled(String expression, Filter<T> filter,
                Map<String, Object> variables, Namespace[] namespaces) {
    super(expression, filter, variables, namespaces);
    try {
      /*
        The current context for XPath expression evaluation. The navigator is
        responsible for exposing JDOM content to Jaxen, including the wrapping of
        Namespace instances in NamespaceContainer
        <p>
        Because of the need to wrap Namespace, we also need to unwrap namespace.
        Further, we can't re-use the details from one 'selectNodes' to another
        because the Document tree may have been modfied between, and also, we do
        not want to be holding on to memory.
        <p>
        Finally, we want to pre-load the NamespaceContext with the namespaces
        that are in scope for the contextNode being searched.
        <p>
        So, we need to reset the Navigator before and after each use. try{}
        finally {} to the rescue.
       */
      JDOMCoreNavigator navigator = new JDOMCoreNavigator();
      xPath = new BaseXPath(expression, navigator);
    }
    catch (JaxenException e) {
      throw new IllegalArgumentException("Unable to compile '" + expression
                                         + "'. See Cause.", e);
    }
    xPath.setNamespaceContext(this);
    xPath.setVariableContext(this);
  }

  /**
   * Make a copy-constructor available to the clone() method.
   * This is simpler than trying to do a deep clone anyway.
   *
   * @param toclone The JaxenCompiled instance to clone
   */
  private JaxenCompiled(JaxenCompiled<T> toclone) {
    this(toclone.getExpression(), toclone.getFilter(), toclone.getVariables(), toclone.getNamespaces());
  }

  @Override
  public String translateNamespacePrefixToUri(String prefix) {
    return getNamespace(prefix).getURI();
  }

  @Override
  public Object getVariableValue(String namespaceURI, String prefix,
                                 String localName) throws UnresolvableException {
    if (namespaceURI == null) {
      namespaceURI = "";
    }
    if (prefix == null) {
      prefix = "";
    }
    try {
      if (namespaceURI.isEmpty()) {
        namespaceURI = getNamespace(prefix).getURI();
      }
      return getVariable(localName, Namespace.getNamespace(namespaceURI));
    }
    catch (IllegalArgumentException e) {
      throw new UnresolvableException("Unable to resolve variable " +
                                      localName + " in namespace '" + namespaceURI +
                                      "' to a vaulue.");
    }
  }

  @Override
  protected List<?> evaluateRawAll(Object context) {
    try {
      return unWrap(xPath.selectNodes(context));
    }
    catch (JaxenException e) {
      throw new IllegalStateException(
        "Unable to evaluate expression. See cause", e);
    }
  }

  @Override
  protected Object evaluateRawFirst(Object context) {
    try {
      return unWrapNS(xPath.selectSingleNode(context));
    }
    catch (JaxenException e) {
      throw new IllegalStateException(
        "Unable to evaluate expression. See cause", e);
    }
  }

  @Override
  public JaxenCompiled<T> clone() {
    // Use a copy-constructor instead of a deep clone.
    // we have a couple of final variables on this class that we cannot share
    // between instances, and the Jaxen xpath variable is pretty complicated to reconstruct
    // anyway. Easier to just reconstruct it.
    return new JaxenCompiled<>(this);
  }
}
