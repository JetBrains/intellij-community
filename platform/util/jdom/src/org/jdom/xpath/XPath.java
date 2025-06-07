/*--

 Copyright (C) 2000-2007 Jason Hunter & Brett McLaughlin.
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


import org.jdom.*;
import org.jdom.xpath.jaxen.JDOMXPath;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.List;


/**
 * A utility class for performing XPath calls on JDOM nodes, with a factory
 * interface for obtaining a first XPath instance. Users operate against this
 * class while XPath vendors can plug-in implementations underneath.  Users
 * can choose an implementation using either {@link #setXPathClass} or
 * the system property "org.jdom.xpath.class".
 *
 * @author Laurent Bihanic
 * @deprecated Use XPathFactory/XPathExpression/XPathBuilder instead.
 */
@Deprecated
public abstract class XPath implements Serializable {

  /**
   * Creates a new XPath wrapper object, compiling the specified
   * XPath expression.
   *
   * @param path the XPath expression to wrap.
   * @return an XPath instance representing the input path
   * @throws JDOMException if the XPath expression is invalid.
   */
  public static XPath newInstance(String path) throws JDOMException {
    try {
      return new JDOMXPath(path);
    }
    catch (JDOMException ex1) {
      throw ex1;
    }
    catch (Exception ex3) {
      // Any reflection error (probably due to a configuration mistake).
      throw new JDOMException(ex3.toString(), ex3);
    }
  }

  /**
   * Evaluates the wrapped XPath expression and returns the list
   * of selected items.
   *
   * @param context the node to use as context for evaluating
   *                the XPath expression.
   * @return the list of selected items, which may be of types: {@link Element},
   * {@link Attribute}, {@link Text}, {@link CDATA},
   * Double, or String.
   * @throws JDOMException if the evaluation of the XPath
   *                       expression on the specified context
   *                       failed.
   */
  public abstract List selectNodes(Object context) throws JDOMException;

  /**
   * Evaluates the wrapped XPath expression and returns the first
   * entry in the list of selected nodes (or atomics).
   *
   * @param context the node to use as context for evaluating
   *                the XPath expression.
   * @return the first selected item, which may be of types: {@link Element},
   * {@link Attribute}, {@link Text}, {@link CDATA},
   * Double, String, or <code>null</code> if no item was selected.
   * @throws JDOMException if the evaluation of the XPath
   *                       expression on the specified context
   *                       failed.
   */
  public abstract Object selectSingleNode(Object context) throws JDOMException;

  /**
   * Returns the string value of the first node selected by applying
   * the wrapped XPath expression to the given context.
   *
   * @param context the element to use as context for evaluating
   *                the XPath expression.
   * @return the string value of the first node selected by applying
   * the wrapped XPath expression to the given context.
   * @throws JDOMException if the XPath expression is invalid or
   *                       its evaluation on the specified context
   *                       failed.
   */
  public abstract String valueOf(Object context) throws JDOMException;

  /**
   * Adds a namespace definition to the list of namespaces known of
   * this XPath expression.
   * <p>
   * <strong>Note</strong>: In XPath, there is no such thing as a
   * 'default namespace'.  The empty prefix <b>always</b> resolves
   * to the empty namespace URI.</p>
   *
   * @param namespace the namespace.
   */
  public abstract void addNamespace(Namespace namespace);

  /**
   * Adds a namespace definition (prefix and URI) to the list of
   * namespaces known of this XPath expression.
   * <p>
   * <strong>Note</strong>: In XPath, there is no such thing as a
   * 'default namespace'.  The empty prefix <b>always</b> resolves
   * to the empty namespace URI.</p>
   *
   * @param prefix the namespace prefix.
   * @param uri    the namespace URI.
   * @throws IllegalNameException if the prefix or uri are null or
   *                              empty strings or if they contain
   *                              illegal characters.
   */
  public void addNamespace(String prefix, String uri) {
    addNamespace(Namespace.getNamespace(prefix, uri));
  }

  /**
   * Returns the wrapped XPath expression as a string.
   *
   * @return the wrapped XPath expression as a string.
   */
  public abstract String getXPath();


  /**
   * Evaluates an XPath expression and returns the list of selected
   * items.
   * <p>
   * <strong>Note</strong>: This method should not be used when the
   * same XPath expression needs to be applied several times (on the
   * same or different contexts) as it requires the expression to be
   * compiled before being evaluated.  In such cases,
   * {@link #newInstance allocating} an XPath wrapper instance and
   * {@link #selectNodes(Object) evaluating} it several
   * times is way more efficient.
   * </p>
   *
   * @param context the node to use as context for evaluating
   *                the XPath expression.
   * @param path    the XPath expression to evaluate.
   * @return the list of selected items, which may be of types: {@link Element},
   * {@link Attribute}, {@link Text}, {@link CDATA},
   * Double, or String.
   * @throws JDOMException if the XPath expression is invalid or
   *                       its evaluation on the specified context
   *                       failed.
   */
  public static List selectNodes(Object context, String path)
    throws JDOMException {
    return newInstance(path).selectNodes(context);
  }

  /**
   * Evaluates the wrapped XPath expression and returns the first
   * entry in the list of selected nodes (or atomics).
   * <p>
   * <strong>Note</strong>: This method should not be used when the
   * same XPath expression needs to be applied several times (on the
   * same or different contexts) as it requires the expression to be
   * compiled before being evaluated.  In such cases,
   * {@link #newInstance allocating} an XPath wrapper instance and
   * {@link #selectSingleNode(Object) evaluating} it
   * several times is way more efficient.
   * </p>
   *
   * @param context the element to use as context for evaluating
   *                the XPath expression.
   * @param path    the XPath expression to evaluate.
   * @return the first selected item, which may be of types: {@link Element},
   * {@link Attribute}, {@link Text}, {@link CDATA},
   * Double, String, or <code>null</code> if no item was selected.
   * @throws JDOMException if the XPath expression is invalid or
   *                       its evaluation on the specified context
   *                       failed.
   */
  public static Object selectSingleNode(Object context, String path)
    throws JDOMException {
    return newInstance(path).selectSingleNode(context);
  }


  //-------------------------------------------------------------------------
  // Serialization support
  //-------------------------------------------------------------------------

  /**
   * <i>[Serialization support]</i> Returns the alternative object
   * to write to the stream when serializing this object.  This
   * method returns an instance of a dedicated nested class to
   * serialize XPath expressions independently of the concrete
   * implementation being used.
   * <p>
   * <strong>Note</strong>: Subclasses are not allowed to override
   * this method to ensure valid serialization of all
   * implementations.</p>
   *
   * @return an XPathString instance configured with the wrapped
   * XPath expression.
   * @throws ObjectStreamException never.
   */
  protected final Object writeReplace() throws ObjectStreamException {
    return new XPathString(this.getXPath());
  }

  /**
   * The XPathString is dedicated to serialize instances of
   * XPath subclasses in a implementation-independent manner.
   * <p>
   * XPathString ensures that only string data are serialized.  Upon
   * deserialization, XPathString relies on XPath factory method to
   * to create instances of the concrete XPath wrapper currently
   * configured.</p>
   */
  private static final class XPathString implements Serializable {
    /**
     * Standard JDOM2 Serialization. Default mechanism.
     */
    private static final long serialVersionUID = 200L;

    /**
     * The XPath expression as a string.
     */
    private final String xPath;

    /**
     * Creates a new XPathString instance from the specified
     * XPath expression.
     *
     * @param xpath the XPath expression.
     */
    XPathString(String xpath) {
      super();

      this.xPath = xpath;
    }

    /**
     * <i>[Serialization support]</i> Resolves the read XPathString
     * objects into XPath implementations.
     *
     * @return an instance of a concrete implementation of
     * XPath.
     * @throws ObjectStreamException if no XPath could be built
     *                               from the read object.
     */
    private Object readResolve() throws ObjectStreamException {
      try {
        return newInstance(this.xPath);
      }
      catch (JDOMException ex1) {
        throw new InvalidObjectException(
          "Can't create XPath object for expression \"" +
          this.xPath + "\": " + ex1);
      }
    }
  }
}

