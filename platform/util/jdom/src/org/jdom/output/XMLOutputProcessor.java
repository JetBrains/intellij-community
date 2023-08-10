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
import org.jdom.output.Format;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * This interface provides a base support for the {@link XMLOutputter2}.
 * <p>
 * People who want to create a custom XMLOutputProcessor for XMLOutputter are
 * able to implement this interface with the following notes and restrictions:
 * <ol>
 * <li>The XMLOutputter will call one, and only one of the <code>process(Writer,Format,*)</code> methods each
 * time the XMLOutputter is requested to output some JDOM content. It is thus
 * safe to assume that a <code>process(Writer,Format,*)</code> method can set up any
 * infrastructure needed to process the content, and that the XMLOutputter will
 * not re-call that method, or some other <code>process(Writer,Format,*)</code> method for the same output
 * sequence.
 * <li>The process methods should be thread-safe and reentrant: The same
 * <code>process(Writer,Format,*)</code> method may (will) be called concurrently from different threads.
 * </ol>
 * <p>
 *
 * @author Rolf Lear
 * @since JDOM2
 */
public interface XMLOutputProcessor {

  /**
   * This will print the <code>{@link Document}</code> to the given Writer.
   * <p>
   * Warning: using your own Writer may cause the outputter's preferred
   * character encoding to be ignored. If you use encodings other than UTF-8,
   * we recommend using the method that takes an OutputStream instead.
   * </p>
   *
   * @param out    <code>Writer</code> to use.
   * @param format <code>Format</code> instance specifying output style
   * @param doc    <code>Document</code> to format.
   * @throws IOException          if there's any problem writing.
   * @throws NullPointerException if the input content is null
   */
  void process(Writer out, Format format, Document doc) throws IOException;

  /**
   * Print out the <code>{@link DocType}</code>.
   *
   * @param out     <code>Writer</code> to use.
   * @param format  <code>Format</code> instance specifying output style
   * @param doctype <code>DocType</code> to output.
   * @throws IOException          if there's any problem writing.
   * @throws NullPointerException if the input content is null
   */
  void process(Writer out, Format format, DocType doctype) throws IOException;

  /**
   * Print out an <code>{@link Element}</code>, including its
   * <code>{@link Attribute}</code>s, and all contained (child) elements, etc.
   *
   * @param out     <code>Writer</code> to use.
   * @param format  <code>Format</code> instance specifying output style
   * @param element <code>Element</code> to output.
   * @throws IOException          if there's any problem writing.
   * @throws NullPointerException if the input content is null
   */
  void process(Writer out, Format format, Element element) throws IOException;

  /**
   * This will handle printing out a list of nodes. This can be useful for
   * printing the content of an element that contains HTML, like
   * "&lt;description&gt;JDOM is &lt;b&gt;fun&gt;!&lt;/description&gt;".
   *
   * @param out    <code>Writer</code> to use.
   * @param format <code>Format</code> instance specifying output style
   * @param list   <code>List</code> of nodes.
   * @throws IOException          if there's any problem writing.
   * @throws NullPointerException if the input list is null or contains null members
   * @throws ClassCastException   if any of the list members are not {@link Content}
   */
  void process(Writer out, Format format, List<? extends Content> list)
    throws IOException;

  /**
   * Print out a <code>{@link CDATA}</code> node.
   *
   * @param out    <code>Writer</code> to use.
   * @param format <code>Format</code> instance specifying output style
   * @param cdata  <code>CDATA</code> to output.
   * @throws IOException          if there's any problem writing.
   * @throws NullPointerException if the input content is null
   */
  void process(Writer out, Format format, CDATA cdata) throws IOException;

  /**
   * Print out a <code>{@link Text}</code> node. Perfoms the necessary entity
   * escaping and whitespace stripping.
   *
   * @param out    <code>Writer</code> to use.
   * @param format <code>Format</code> instance specifying output style
   * @param text   <code>Text</code> to output.
   * @throws IOException          if there's any problem writing.
   * @throws NullPointerException if the input content is null
   */
  void process(Writer out, Format format, Text text) throws IOException;

  /**
   * Print out a <code>{@link Comment}</code>.
   *
   * @param out     <code>Writer</code> to use.
   * @param format  <code>Format</code> instance specifying output style
   * @param comment <code>Comment</code> to output.
   * @throws IOException          if there's any problem writing.
   * @throws NullPointerException if the input content is null
   */
  void process(Writer out, Format format, Comment comment) throws IOException;

  /**
   * Print out a <code>{@link EntityRef}</code>.
   *
   * @param out    <code>Writer</code> to use.
   * @param format <code>Format</code> instance specifying output style
   * @param entity <code>EntityRef</code> to output.
   * @throws IOException          if there's any problem writing.
   * @throws NullPointerException if the input content is null
   */
  void process(Writer out, Format format, EntityRef entity) throws IOException;
}