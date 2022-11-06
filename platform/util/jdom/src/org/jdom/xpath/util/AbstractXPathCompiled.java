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

package org.jdom.xpath.util;

import org.jdom.Namespace;
import org.jdom.Verifier;
import org.jdom.filter2.Filter;
import org.jdom.xpath.XPathDiagnostic;
import org.jdom.xpath.XPathExpression;

import java.util.*;

/**
 * A mostly-implemented XPathExpression that only needs two methods to be
 * implemented in order to satisfy the complete API. Subclasses of this
 * <strong>MUST</strong> correctly override the clone() method which in turn
 * should call <code>super.clone();</code>
 *
 * @param <T> The generic type of the returned values.
 * @author Rolf Lear
 */
public abstract class AbstractXPathCompiled<T> implements XPathExpression<T> {
  private static final class NamespaceComparator implements Comparator<Namespace> {
    @Override
    public int compare(Namespace ns1, Namespace ns2) {
      return ns1.getPrefix().compareTo(ns2.getPrefix());
    }
  }

  private static final NamespaceComparator NSSORT = new NamespaceComparator();

  /**
   * Utility method to find a Namespace that has a given URI, and return the prefix.
   *
   * @param uri the URI to search for
   * @param nsa the array of namespaces to search through
   * @return the prefix of the namespace
   */
  private static String getPrefixForURI(final String uri, final Namespace[] nsa) {
    for (final Namespace ns : nsa) {
      if (ns.getURI().equals(uri)) {
        return ns.getPrefix();
      }
    }
    throw new IllegalStateException("No namespace defined with URI " + uri);
  }

  private final Map<String, Namespace> xnamespaces = new HashMap<>();
  // Not final to support cloning.
  private Map<String, Map<String, Object>> xvariables = new HashMap<>();
  private final String xquery;
  private final Filter<T> xfilter;

  /**
   * Construct an XPathExpression.
   *
   * @param query      The XPath query
   * @param filter     The coercion filter.
   * @param variables  A map of variables.
   * @param namespaces The namespaces referenced from the query.
   * @see XPathExpression for conditions which throw
   * {@link NullPointerException} or {@link IllegalArgumentException}.
   */
  public AbstractXPathCompiled(final String query, final Filter<T> filter,
                               final Map<String, Object> variables, final Namespace[] namespaces) {
    if (query == null) {
      throw new NullPointerException("Null query");
    }
    if (filter == null) {
      throw new NullPointerException("Null filter");
    }
    xnamespaces.put(Namespace.NO_NAMESPACE.getPrefix(),
                    Namespace.NO_NAMESPACE);
    if (namespaces != null) {
      for (Namespace ns : namespaces) {
        if (ns == null) {
          throw new NullPointerException("Null namespace");
        }
        final Namespace oldns = xnamespaces.put(ns.getPrefix(), ns);
        if (oldns != null && oldns != ns) {
          if (oldns == Namespace.NO_NAMESPACE) {
            throw new IllegalArgumentException(
              "The default (no prefix) Namespace URI for XPath queries is always" +
              " '' and it cannot be redefined to '" + ns.getURI() + "'.");
          }
          throw new IllegalArgumentException(
            "A Namespace with the prefix '" + ns.getPrefix()
            + "' has already been declared.");
        }
      }
    }

    if (variables != null) {
      for (Map.Entry<String, Object> me : variables.entrySet()) {
        final String qname = me.getKey();
        if (qname == null) {
          throw new NullPointerException("Variable with a null name");
        }
        final int p = qname.indexOf(':');
        final String pfx = p < 0 ? "" : qname.substring(0, p);
        final String lname = p < 0 ? qname : qname.substring(p + 1);

        final String vpfxmsg = Verifier.checkNamespacePrefix(pfx);
        if (vpfxmsg != null) {
          throw new IllegalArgumentException("Prefix '" + pfx
                                             + "' for variable " + qname + " is illegal: "
                                             + vpfxmsg);
        }
        final String vnamemsg = Verifier.checkXMLName(lname);
        if (vnamemsg != null) {
          throw new IllegalArgumentException("Variable name '"
                                             + lname + "' for variable " + qname
                                             + " is illegal: " + vnamemsg);
        }

        final Namespace ns = xnamespaces.get(pfx);
        if (ns == null) {
          throw new IllegalArgumentException("Prefix '" + pfx
                                             + "' for variable " + qname
                                             + " has not been assigned a Namespace.");
        }

        Map<String, Object> vmap = xvariables.get(ns.getURI());
        if (vmap == null) {
          vmap = new HashMap<>();
          xvariables.put(ns.getURI(), vmap);
        }

        if (vmap.put(lname, me.getValue()) != null) {
          throw new IllegalArgumentException("Variable with name "
                                             + me.getKey() + "' has already been defined.");
        }
      }
    }
    xquery = query;
    xfilter = filter;
  }

  /**
   * Subclasses of this AbstractXPathCompile class must call super.clone() in
   * their clone methods!
   * <p>
   * This would be a sample clone method from a subclass:
   * <p>
   *
   * <code><pre>
   * 		public XPathExpression&lt;T&gt; clone() {
   *      {@literal @}SuppressWarnings("unchecked")
   * 			final MyXPathCompiled&lt;T&gt; ret = (MyXPathCompiled&lt;T&gt;)super.clone();
   * 			// change any fields that need to be cloned.
   * 			....
   * 			return ret;
   *    }
   * </pre></code>
   * <p>
   * Here's the documentation from {@link XPathExpression#clone()}
   * <p>
   * {@inheritDoc}
   */
  @Override
  public XPathExpression<T> clone() {
    AbstractXPathCompiled<T> ret;
    try {
      @SuppressWarnings("unchecked") final AbstractXPathCompiled<T> c = (AbstractXPathCompiled<T>)super
        .clone();
      ret = c;
    }
    catch (CloneNotSupportedException cnse) {
      throw new IllegalStateException(
        "Should never be getting a CloneNotSupportedException!",
        cnse);
    }
    Map<String, Map<String, Object>> vmt = new HashMap<>();
    for (Map.Entry<String, Map<String, Object>> me : xvariables.entrySet()) {
      final Map<String, Object> cmap = new HashMap<>(me.getValue());
      vmt.put(me.getKey(), cmap);
    }
    ret.xvariables = vmt;
    return ret;
  }

  @Override
  public final String getExpression() {
    return xquery;
  }

  @Override
  public final Namespace getNamespace(final String prefix) {
    final Namespace ns = xnamespaces.get(prefix);
    if (ns == null) {
      throw new IllegalArgumentException("Namespace with prefix '"
                                         + prefix + "' has not been declared.");
    }
    return ns;
  }

  @Override
  public Namespace[] getNamespaces() {
    final Namespace[] nsa = xnamespaces.values().toArray(new Namespace[0]);
    Arrays.sort(nsa, NSSORT);
    return nsa;
  }

  @Override
  public final Object getVariable(final String name, Namespace uri) {
    final Map<String, Object> vmap =
      xvariables.get(uri == null ? "" : uri.getURI());
    if (vmap == null) {
      throw new IllegalArgumentException("Variable with name '" + name
                                         + "' in namespace '" + uri.getURI() + "' has not been declared.");
    }
    final Object ret = vmap.get(name);
    if (ret == null) {
      if (!vmap.containsKey(name)) {
        throw new IllegalArgumentException("Variable with name '"
                                           + name + "' in namespace '" + uri.getURI()
                                           + "' has not been declared.");
      }
      // leave translating null variable values to the implementation.
      return null;
    }
    return ret;
  }

  @Override
  public Object getVariable(String qname) {
    if (qname == null) {
      throw new NullPointerException(
        "Cannot get variable value for null qname");
    }
    final int pos = qname.indexOf(':');
    if (pos >= 0) {
      return getVariable(qname.substring(pos + 1),
                         getNamespace(qname.substring(0, pos)));
    }
    return getVariable(qname, Namespace.NO_NAMESPACE);
  }

  @Override
  public Object setVariable(String name, Namespace uri, Object value) {
    final Object ret = getVariable(name, uri);
    // if that succeeded then we have it easy....
    xvariables.get(uri.getURI()).put(name, value);
    return ret;
  }

  @Override
  public Object setVariable(String qname, Object value) {
    if (qname == null) {
      throw new NullPointerException(
        "Cannot get variable value for null qname");
    }
    final int pos = qname.indexOf(':');
    if (pos >= 0) {
      return setVariable(qname.substring(pos + 1),
                         getNamespace(qname.substring(0, pos)), value);
    }
    return setVariable(qname, Namespace.NO_NAMESPACE, value);
  }

  /**
   * utility method that allows descendant classes to access the variables
   * that were set on this expression, in a format that can be used in a constructor (qname/value).
   *
   * @return the variables set on this instance.
   */
  protected Map<String, Object> getVariables() {
    HashMap<String, Object> vars = new HashMap<>();
    Namespace[] nsa = getNamespaces();
    for (Map.Entry<String, Map<String, Object>> ue : xvariables.entrySet()) {
      final String uri = ue.getKey();
      final String pfx = getPrefixForURI(uri, nsa);
      for (Map.Entry<String, Object> ve : ue.getValue().entrySet()) {
        if ("".equals(pfx)) {
          vars.put(ve.getKey(), ve.getValue());
        }
        else {
          vars.put(pfx + ":" + ve.getKey(), ve.getValue());
        }
      }
    }
    return vars;
  }

  @Override
  public final Filter<T> getFilter() {
    return xfilter;
  }

  @Override
  public List<T> evaluate(Object context) {
    return xfilter.filter(evaluateRawAll(context));
  }

  /**
   *
   */
  @Override
  public T evaluateFirst(Object context) {
    Object raw = evaluateRawFirst(context);
    if (raw == null) {
      return null;
    }
    return xfilter.filter(raw);
  }

  @Override
  public XPathDiagnostic<T> diagnose(Object context, boolean firstonly) {
    final List<?> result = firstonly ? Collections
      .singletonList(evaluateRawFirst(context))
                                     : evaluateRawAll(context);
    return new XPathDiagnosticImpl<>(context, this, result, firstonly);
  }

  @Override
  public String toString() {
    int nscnt = xnamespaces.size();
    int vcnt = 0;
    for (Map<String, Object> cmap : xvariables.values()) {
      vcnt += cmap.size();
    }
    return String.format(
      "[XPathExpression: %d namespaces and %d variables for query %s]",
      nscnt, vcnt, getExpression());
  }

  /**
   * This is the raw expression evaluator to be implemented by the back-end
   * XPath library.
   *
   * @param context The context against which to evaluate the query
   * @return A list of XPath results.
   */
  protected abstract List<?> evaluateRawAll(Object context);

  /**
   * This is the raw expression evaluator to be implemented by the back-end
   * XPath library. When this method is processed the implementing library is
   * free to stop processing when the result that would be the first result is
   * retrieved.
   * <p>
   * Only the first value in the result will be processed (if any).
   *
   * @param context The context against which to evaluate the query
   * @return The first item in the XPath results, or null if there are no
   * results.
   */
  protected abstract Object evaluateRawFirst(Object context);
}
