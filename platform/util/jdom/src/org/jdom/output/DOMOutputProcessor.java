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

package org.jdom.output;

import org.jdom.*;
import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.EntityReference;
import org.w3c.dom.Node;

import java.util.List;

/**
 * This interface provides a base support for the {@link DOMOutputter}.
 * <p>
 * People who want to create a custom DOMOutputProcessor for DOMOutputter are
 * able to implement this interface with the following notes and restrictions:
 * <ol>
 * <li>The DOMOutputter will call one, and only one of the
 * <code>process(Format,*)</code> methods each time the DOMOutputter is
 * requested to output some JDOM content. It is thus safe to assume that a
 * <code>process(Format,*)</code> method can set up any infrastructure needed to
 * process the content, and that the DOMOutputter will not re-call that method,
 * or some other <code>process(Format,*)</code> method for the same output
 * sequence.
 * <li>The process methods should be thread-safe and reentrant: The same
 * <code>process(Format,*)</code> method may (will) be called concurrently from
 * different threads.
 * </ol>
 * <p>
 * The {@link AbstractDOMOutputProcessor} class is a full implementation of this
 * interface and is fully customisable. People who want a custom DOMOutputter
 * are encouraged to extend the AbstractDOMOutputProcessor rather than do a full
 * re-implementation of this interface.
 *
 * @author Rolf Lear
 * @see DOMOutputter
 * @see AbstractDOMOutputProcessor
 * @since JDOM2
 */
public interface DOMOutputProcessor {

  /**
   * This will convert the <code>{@link Document}</code> to the given DOM
   * Document.
   * <p>
   *
   * @param basedoc The DOM document to use for the conversion
   * @param format  <code>Format</code> instance specifying output style
   * @param doc     <code>Document</code> to format.
   * @return The same DOM Document as the input document, but with the JDOM
   * content converted and added.
   */
  org.w3c.dom.Document process(org.w3c.dom.Document basedoc,
                               Format format, Document doc);

  /**
   * This will convert the <code>{@link Element}</code> using the given DOM
   * Document to create the resulting DOM Element.
   *
   * @param basedoc The DOM document to use for the conversion
   * @param format  <code>Format</code> instance specifying output style
   * @param element <code>Element</code> to format.
   * @return The input JDOM Element converted to a DOM Element
   */
  org.w3c.dom.Element process(org.w3c.dom.Document basedoc,
                              Format format, Element element);

  /**
   * This will convert the list of JDOM <code>{@link Content}</code> using the
   * given DOM Document to create the resulting list of DOM Nodes.
   *
   * @param basedoc The DOM document to use for the conversion
   * @param format  <code>Format</code> instance specifying output style
   * @param list    JDOM <code>Content</code> to convert.
   * @return The input JDOM Content List converted to a List of DOM Nodes
   */
  List<Node> process(org.w3c.dom.Document basedoc,
                     Format format, List<? extends Content> list);

  /**
   * This will convert the <code>{@link CDATA}</code> using the given DOM
   * Document to create the resulting DOM CDATASection.
   *
   * @param basedoc The DOM document to use for the conversion
   * @param format  <code>Format</code> instance specifying output style
   * @param cdata   <code>CDATA</code> to format.
   * @return The input JDOM CDATA converted to a DOM CDATASection
   */
  CDATASection process(org.w3c.dom.Document basedoc,
                       Format format, CDATA cdata);

  /**
   * This will convert the <code>{@link Text}</code> using the given DOM
   * Document to create the resulting DOM Text.
   *
   * @param basedoc The DOM document to use for the conversion
   * @param format  <code>Format</code> instance specifying output style
   * @param text    <code>Text</code> to format.
   * @return The input JDOM Text converted to a DOM Text
   */
  org.w3c.dom.Text process(org.w3c.dom.Document basedoc,
                           Format format, Text text);

  /**
   * This will convert the <code>{@link Comment}</code> using the given DOM
   * Document to create the resulting DOM Comment.
   *
   * @param basedoc The DOM document to use for the conversion
   * @param format  <code>Format</code> instance specifying output style
   * @param comment <code>Comment</code> to format.
   * @return The input JDOM Comment converted to a DOM Comment
   */
  org.w3c.dom.Comment process(org.w3c.dom.Document basedoc,
                              Format format, Comment comment);

  /**
   * This will convert the <code>{@link EntityRef}</code> using the given DOM
   * Document to create the resulting DOM EntityReference.
   *
   * @param basedoc The DOM document to use for the conversion
   * @param format  <code>Format</code> instance specifying output style
   * @param entity  <code>EntityRef</code> to format.
   * @return The input JDOM EntityRef converted to a DOM EntityReference
   */
  EntityReference process(org.w3c.dom.Document basedoc,
                          Format format, EntityRef entity);

  /**
   * This will convert the <code>{@link Attribute}</code> using the given DOM
   * Document to create the resulting DOM Attr.
   *
   * @param basedoc   The DOM document to use for the conversion
   * @param format    <code>Format</code> instance specifying output style
   * @param attribute <code>Attribute</code> to format.
   * @return The input JDOM Attribute converted to a DOM Attr
   */
  Attr process(org.w3c.dom.Document basedoc,
               Format format, Attribute attribute);
}
