/*-- 

 Copyright (C) 2000-2011 Jason Hunter & Brett McLaughlin.
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

package org.jdom.output;

import org.jdom.Comment;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Text;
import org.jdom.*;
import org.jdom.adapters.DOMAdapter;
import org.jdom.adapters.JAXPDOMAdapter;
import org.w3c.dom.*;

import java.util.List;

/**
 * Outputs a JDOM {@link Document org.jdom.Document} as a DOM
 * {@link org.w3c.dom.Document org.w3c.dom.Document}. Also provides methods to
 * output other types of JDOM Content in the equivalent DOM nodes.
 * <p>
 * There are two versions of most functions, one that creates an independent DOM
 * node using the DOMAdapter to create a new org.w3c.dom.Document. The other
 * version creates the new DOM Nodes using the supplied org.w3c.dom.Document
 * instance.
 *
 * @author Brett McLaughlin
 * @author Jason Hunter
 * @author Matthew Merlo
 * @author Dan Schaffer
 * @author Yusuf Goolamabbas
 * @author Bradley S. Huffman
 * @author Rolf lear
 */
public final class DOMOutputter {
  /**
   * Create a final/concrete instance of the AbstractDOMOutputProcessor.
   * Making it final improves performance.
   *
   * @author Rolf Lear
   */
  private static final class DefaultDOMOutputProcessor extends AbstractDOMOutputProcessor {
    // add nothing except make it final.
  }

  /**
   * Default adapter class
   */
  private static final DOMAdapter DEFAULT_ADAPTER = new JAXPDOMAdapter();

  private static final DOMOutputProcessor DEFAULT_PROCESSOR = new DefaultDOMOutputProcessor();

  /**
   * Adapter to use for interfacing with the DOM implementation
   */
  private final DOMAdapter adapter;

  private Format format;

  private final DOMOutputProcessor processor;

  /**
   * This creates a new DOMOutputter which will attempt to first locate a DOM
   * implementation to use via JAXP, and if JAXP does not exist or there's a
   * problem, will fall back to the default parser.
   */
  @SuppressWarnings("unused")
  public DOMOutputter() {
    this(null, null, null);
  }

  /**
   * The complete constructor for specifying a custom DOMAdaptor, Format, and
   * DOMOutputProcessor.
   *
   * @param adapter   The adapter to use to create the base Document instance (null
   *                  implies the default).
   * @param format    The output Format to use (null implies the default).
   * @param processor The custom mechanism for doing the output (null implies the
   *                  default).
   * @since JDOM2
   */
  @SuppressWarnings("WeakerAccess")
  public DOMOutputter(DOMAdapter adapter, Format format,
                      DOMOutputProcessor processor) {
    this.adapter = adapter == null ? DEFAULT_ADAPTER : adapter;
    this.format = format == null ? Format.getRawFormat() : format;
    this.processor = processor == null ? DEFAULT_PROCESSOR : processor;
  }

  /**
   * Get the Format instance currently used by this DOMOutputter.
   *
   * @return the current Format instance
   * @since JDOM2
   */
  public Format getFormat() {
    return format;
  }

  /**
   * Set a new Format instance for this DOMOutputter
   *
   * @param format the new Format instance to use (null implies the default)
   * @since JDOM2
   */
  public void setFormat(Format format) {
    this.format = format == null ? Format.getRawFormat() : format;
  }

  /**
   * Returns whether DOMs will be constructed with namespaces even when the
   * source document has elements all in the empty namespace.
   *
   * @return the forceNamespaceAware flag value
   * @deprecated All DOMOutputters are always NamesapceAware. Always true.
   */
  @Deprecated
  public boolean getForceNamespaceAware() {
    return true;
  }

  /**
   * This converts the JDOM <code>Document</code> parameter to a DOM Document,
   * returning the DOM version. The DOM implementation is the one supplied by
   * the current DOMAdapter.
   *
   * @param document <code>Document</code> to output.
   * @return an <code>org.w3c.dom.Document</code> version
   * @throws JDOMException if output failed.
   */
  public org.w3c.dom.Document output(Document document) throws JDOMException {
    return processor.process(adapter.createDocument(document.getDocType()),
                             format, document);
  }

  /**
   * This converts the JDOM <code>DocType</code> parameter to a DOM DocumentType,
   * returning the DOM version. The DOM implementation is the one supplied by
   * the current DOMAdapter.
   * <p>
   * Unlike the other DOM Nodes, you cannot use a DOM Document to simply create a DOM DocumentType Node,
   * it has to be created at the same time as the DOM Document instance. As a result, there is no
   * version of this method that takes a DOM Document instance.
   *
   * @param doctype <code>DocType</code> to output.
   * @return an <code>org.w3c.dom.DocumentType</code> version
   * @throws JDOMException if output failed.
   * @since JDOM2
   */
  public DocumentType output(DocType doctype) throws JDOMException {
    return adapter.createDocument(doctype).getDoctype();
  }

  /**
   * This converts the JDOM <code>Element</code> parameter to a DOM Element,
   * returning the DOM version. The DOM Node will be linked to an independent
   * DOM Document instance supplied by the current DOMAdapter
   *
   * @param element <code>Element</code> to output.
   * @return an <code>org.w3c.dom.Element</code> version
   * @throws JDOMException if output failed.
   */
  public org.w3c.dom.Element output(Element element) throws JDOMException {
    return processor.process(adapter.createDocument(), format, element);
  }

  /**
   * This converts the JDOM <code>Text</code> parameter to a DOM Text Node,
   * returning the DOM version. The DOM Node will be linked to an independent
   * DOM Document instance supplied by the current DOMAdapter
   *
   * @param text <code>Text</code> to output.
   * @return an <code>org.w3c.dom.Text</code> version
   * @throws JDOMException if output failed.
   * @since JDOM2
   */
  public org.w3c.dom.Text output(Text text) throws JDOMException {
    return processor.process(adapter.createDocument(), format, text);
  }

  /**
   * This converts the JDOM <code>CDATA</code> parameter to a DOM CDATASection
   * Node, returning the DOM version. The DOM Node will be linked to an
   * independent DOM Document instance supplied by the current DOMAdapter
   *
   * @param cdata <code>CDATA</code> to output.
   * @return an <code>org.w3c.dom.CDATASection</code> version
   * @throws JDOMException if output failed.
   * @since JDOM2
   */
  public CDATASection output(CDATA cdata) throws JDOMException {
    return processor.process(adapter.createDocument(), format, cdata);
  }

  /**
   * This converts the JDOM <code>ProcessingInstruction</code> parameter to a
   * DOM ProcessingInstruction, returning the DOM version. The DOM Node will
   * be linked to an independent DOM Document instance supplied by the current
   * DOMAdapter
   *
   * @param comment <code>Comment</code> to output.
   * @return an <code>org.w3c.dom.Comment</code> version
   * @throws JDOMException if output failed.
   * @since JDOM2
   */
  public org.w3c.dom.Comment output(Comment comment) throws JDOMException {
    return processor.process(adapter.createDocument(), format, comment);
  }

  /**
   * This converts the JDOM <code>EntityRef</code> parameter to a DOM
   * EntityReference Node, returning the DOM version. The DOM Node will be
   * linked to an independent DOM Document instance supplied by the current
   * DOMAdapter
   *
   * @param entity <code>EntityRef</code> to output.
   * @return an <code>org.w3c.dom.EntityReference</code> version
   * @throws JDOMException if output failed.
   * @since JDOM2
   */
  public EntityReference output(EntityRef entity)
    throws JDOMException {
    return processor.process(adapter.createDocument(), format, entity);
  }

  /**
   * This converts the JDOM <code>Attribute</code> parameter to a DOM Attr
   * Node, returning the DOM version. The DOM Node will be linked to an
   * independent DOM Document instance supplied by the current DOMAdapter
   *
   * @param attribute <code>Attribute</code> to output.
   * @return an <code>org.w3c.dom.Attr</code> version
   * @throws JDOMException if output failed.
   * @since JDOM2
   */
  public Attr output(Attribute attribute) throws JDOMException {
    return processor.process(adapter.createDocument(), format, attribute);
  }

  /**
   * This converts the JDOM <code>Attribute</code> parameter to a DOM Attr
   * Node, returning the DOM version. The DOM Node will be linked to an
   * independent DOM Document instance supplied by the current DOMAdapter
   *
   * @param list <code>Attribute</code> to output.
   * @return an <code>org.w3c.dom.Attr</code> version
   * @throws JDOMException if output failed.
   * @since JDOM2
   */
  public List<Node> output(List<? extends Content> list)
    throws JDOMException {
    return processor.process(adapter.createDocument(), format, list);
  }

  /**
   * This converts the JDOM <code>Element</code> parameter to a DOM Element,
   * returning the DOM version. The DOM Node will be linked to an independent
   * DOM Document instance supplied by the current DOMAdapter
   *
   * @param basedoc The DOM Document to use for creating DOM Nodes.
   * @param element <code>Element</code> to output.
   * @return an <code>org.w3c.dom.Element</code> version
   * @throws JDOMException if output failed.
   * @since JDOM2
   */
  public org.w3c.dom.Element output(org.w3c.dom.Document basedoc,
                                    Element element) throws JDOMException {
    return processor.process(basedoc, format, element);
  }

  /**
   * This converts the JDOM <code>Text</code> parameter to a DOM Text Node,
   * returning the DOM version. The DOM Node will be linked to an independent
   * DOM Document instance supplied by the current DOMAdapter
   *
   * @param basedoc The DOM Document to use for creating DOM Nodes.
   * @param text    <code>Text</code> to output.
   * @return an <code>org.w3c.dom.Text</code> version
   * @throws JDOMException if output failed.
   * @since JDOM2
   */
  public org.w3c.dom.Text output(org.w3c.dom.Document basedoc, Text text)
    throws JDOMException {
    return processor.process(basedoc, format, text);
  }

  /**
   * This converts the JDOM <code>CDATA</code> parameter to a DOM CDATASection
   * Node, returning the DOM version. The DOM Node will be linked to an
   * independent DOM Document instance supplied by the current DOMAdapter
   *
   * @param basedoc The DOM Document to use for creating DOM Nodes.
   * @param cdata   <code>CDATA</code> to output.
   * @return an <code>org.w3c.dom.CDATASection</code> version
   * @throws JDOMException if output failed.
   * @since JDOM2
   */
  public CDATASection output(org.w3c.dom.Document basedoc,
                             CDATA cdata) throws JDOMException {
    return processor.process(basedoc, format, cdata);
  }

  /**
   * This converts the JDOM <code>ProcessingInstruction</code> parameter to a
   * DOM ProcessingInstruction, returning the DOM version. The DOM Node will
   * be linked to an independent DOM Document instance supplied by the current
   * DOMAdapter
   *
   * @param basedoc The DOM Document to use for creating DOM Nodes.
   * @param comment <code>Comment</code> to output.
   * @return an <code>org.w3c.dom.Comment</code> version
   * @throws JDOMException if output failed.
   * @since JDOM2
   */
  public org.w3c.dom.Comment output(org.w3c.dom.Document basedoc,
                                    Comment comment) throws JDOMException {
    return processor.process(basedoc, format, comment);
  }

  /**
   * This converts the JDOM <code>EntityRef</code> parameter to a DOM
   * EntityReference Node, returning the DOM version. The DOM Node will be
   * linked to an independent DOM Document instance supplied by the current
   * DOMAdapter
   *
   * @param basedoc The DOM Document to use for creating DOM Nodes.
   * @param entity  <code>EntityRef</code> to output.
   * @return an <code>org.w3c.dom.EntityReference</code> version
   * @throws JDOMException if output failed.
   * @since JDOM2
   */
  public EntityReference output(org.w3c.dom.Document basedoc,
                                EntityRef entity) throws JDOMException {
    return processor.process(basedoc, format, entity);
  }

  /**
   * This converts the JDOM <code>Attribute</code> parameter to a DOM Attr
   * Node, returning the DOM version. The DOM Node will be linked to an
   * independent DOM Document instance supplied by the current DOMAdapter
   *
   * @param basedoc   The DOM Document to use for creating DOM Nodes.
   * @param attribute <code>Attribute</code> to output.
   * @return an <code>org.w3c.dom.Attr</code> version
   * @throws JDOMException if output failed.
   * @since JDOM2
   */
  public Attr output(org.w3c.dom.Document basedoc,
                     Attribute attribute) throws JDOMException {
    return processor.process(basedoc, format, attribute);
  }

  /**
   * This converts the list of JDOM <code>Content</code> in to a list of DOM
   * Nodes, returning the DOM version. The DOM Node will be linked to an
   * independent DOM Document instance supplied by the current DOMAdapter
   *
   * @param basedoc The DOM Document to use for creating DOM Nodes.
   * @param list    of JDOM Content to output.
   * @return a List of <code>org.w3c.dom.Node</code>
   * @throws JDOMException if output failed.
   * @since JDOM2
   */
  public List<Node> output(org.w3c.dom.Document basedoc,
                           List<? extends Content> list) throws JDOMException {
    return processor.process(basedoc, format, list);
  }
}
