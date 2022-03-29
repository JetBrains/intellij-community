/*--

 Copyright (C) 2000-2012 Jason Hunter & Brett McLaughlin.
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
import org.jdom.output.support.AbstractXMLOutputProcessor;
import org.jdom.output.support.FormatStack;
import org.jdom.output.support.XMLOutputProcessor;

import java.io.*;
import java.util.List;

/**
 * Outputs a JDOM document as a stream of bytes.
 * <p>
 * The XMLOutputter can manage many styles of document formatting, from
 * untouched to 'pretty' printed. The default is to output the document content
 * exactly as created, but this can be changed by setting a new Format object:
 * <ul>
 * <li>For pretty-print output, use
 * <code>{@link Format#getPrettyFormat()}</code>.
 * <li>For whitespace-normalised output, use
 * <code>{@link Format#getCompactFormat()}</code>.
 * <li>For unmodified-format output, use
 * <code>{@link Format#getRawFormat()}</code>.
 * </ul>
 * <p>
 * There are <code>{@link #output output(...)}</code> methods to print any of
 * the standard JDOM classes to either a Writer or an OutputStream.
 * <p>
 * <b>Warning</b>: When outputting to a Writer, make sure the writer's encoding
 * matches the encoding setting in the Format object. This ensures the encoding
 * in which the content is written (controlled by the Writer configuration)
 * matches the encoding placed in the document's XML declaration (controlled by
 * the XMLOutputter). Because a Writer cannot be queried for its encoding, the
 * information must be passed to the Format manually in its constructor or via
 * the <code>{@link Format#setEncoding}</code> method. The default encoding is
 * UTF-8.
 * <p>
 * The methods <code>{@link #outputString outputString(...)}</code> are for
 * convenience only; for top performance you should call one of the <code>{@link
 * #output output(...)}</code> methods and pass in your own Writer or
 * OutputStream if possible.
 * <p>
 * <b>All</b> of the <code>output*(...)</code> methods will flush the
 * destination Writer or OutputStream before returning, and <b>none</b> of them
 * will <code>close()</code> the destination.
 * <p>
 * XML declarations are always printed on their own line followed by a line
 * separator (this doesn't change the semantics of the document). To omit
 * printing of the declaration use
 * <code>{@link Format#setOmitDeclaration}</code>. To omit printing of the
 * encoding in the declaration use <code>{@link Format#setOmitEncoding}</code>.
 * Unfortunately there is currently no way to know the original encoding of the
 * document.
 * <p>
 * Empty elements are by default printed as &lt;empty/&gt;, but this can be
 * configured with <code>{@link Format#setExpandEmptyElements}</code> to cause
 * them to be expanded to &lt;empty&gt;&lt;/empty&gt;.
 * <p>
 * If changing the {@link Format} settings are insufficient for your output
 * needs you can customise this XMLOutputter further by setting a different
 * {@link XMLOutputProcessor} with the
 * {@link #setXMLOutputProcessor(XMLOutputProcessor)} method or an appropriate
 * constructor. A fully-enabled Abstract class
 * {@link AbstractXMLOutputProcessor} is available to be further extended to
 * your needs if all you want to do is tweak some details.
 *
 * @author Brett McLaughlin
 * @author Jason Hunter
 * @author Jason Reid
 * @author Wolfgang Werner
 * @author Elliotte Rusty Harold
 * @author David &amp; Will (from Post Tool Design)
 * @author Dan Schaffer
 * @author Alex Chaffee
 * @author Bradley S. Huffman
 */

public final class XMLOutputter2 implements Cloneable {

  /*
   * =====================================================================
   * Static content.
   * =====================================================================
   */

  /**
   * Get an OutputStreamWriter, use specified encoding.
   *
   * @param out    The OutputStream to wrap in the writer
   * @param format The format is used to obtain the Character Encoding.
   * @return An Writer (Buffered) that delegates to the specified output steam
   * @throws UnsupportedEncodingException
   */
  private static Writer makeWriter(final OutputStream out,
                                   final Format format)
    throws UnsupportedEncodingException {
    return new BufferedWriter(new OutputStreamWriter(
      new BufferedOutputStream(out), format.getEncoding()));
  }

  /**
   * Create a final and static instance of the AbstractXMLOutputProcessor The
   * final part is important because it improves performance.
   * <p>
   * The JDOM user can change the actual XMLOutputProcessor with the
   * {@link XMLOutputter2#setXMLOutputProcessor(XMLOutputProcessor)} method.
   *
   * @author rolf
   */
  private static final class DefaultXMLProcessor
    extends AbstractXMLOutputProcessor {

    /**
     * A helper method to implement backward-compatibility with JDOM1
     *
     * @param str    The String to output.
     * @param format The format details to use.
     * @return The input String escaped as an attribute value.
     * @see XMLOutputter2#escapeAttributeEntities(String)
     */
    public String escapeAttributeEntities(String str, Format format) {
      StringWriter sw = new StringWriter();
      try {
        super.attributeEscapedEntitiesFilter(sw, new FormatStack(format), str);
      }
      catch (IOException e) {
        // no IOException on StringWriter....
      }
      return sw.toString();
    }

    /**
     * A helper method to implement backward-compatibility with JDOM1
     *
     * @param str    The String to output.
     * @param format The format details to use.
     * @return The input String escaped as an element text value.
     * @see XMLOutputter2#escapeElementEntities(String)
     */
    public String escapeElementEntities(final String str,
                                        final Format format) {
      return Format.escapeText(format.getEscapeStrategy(),
                               format.getLineSeparator(), str);
    }
  }

  /**
   * This constant XMLOutputProcessor is used for all non-customised
   * XMLOutputters
   */
  private static final DefaultXMLProcessor DEFAULTPROCESSOR =
    new DefaultXMLProcessor();

  /*
   * =====================================================================
   * Instance content.
   * =====================================================================
   */

  // For normal output
  private Format myFormat;

  // The actual XMLOutputProcessor to delegate to.
  private XMLOutputProcessor myProcessor;

  /*
   * =====================================================================
   * Constructors
   * =====================================================================
   */

  /**
   * This will create an <code>XMLOutputter</code> with the specified format
   * characteristics.
   * <p>
   * <b>Note:</b> the format object is cloned internally before use. If you
   * want to modify the Format after constructing the XMLOutputter you can
   * modify the Format instance {@link #getFormat()} returns.
   *
   * @param format    The Format instance to use. This instance will be cloned() and as
   *                  a consequence, changes made to the specified format instance
   *                  <b>will not</b> be reflected in this XMLOutputter. A null input
   *                  format indicates that XMLOutputter should use the default
   *                  {@link Format#getRawFormat()}
   * @param processor The XMLOutputProcessor to delegate output to. If null the
   *                  XMLOutputter will use the default XMLOutputProcessor.
   */
  public XMLOutputter2(Format format, XMLOutputProcessor processor) {
    myFormat = format == null ? Format.getRawFormat() : format.clone();
    myProcessor = processor == null ? DEFAULTPROCESSOR : processor;
  }

  /**
   * This will create an <code>XMLOutputter</code> with a default
   * {@link Format} and {@link XMLOutputProcessor}.
   */
  public XMLOutputter2() {
    this(null, null);
  }

  /**
   * This will create an <code>XMLOutputter</code> with the same
   * customisations set in the given <code>XMLOutputter</code> instance. Note
   * that <code>XMLOutputter two = one.clone();</code> would work equally
   * well.
   *
   * @param that the XMLOutputter to clone
   */
  public XMLOutputter2(XMLOutputter2 that) {
    this(that.myFormat, null);
  }

  /**
   * This will create an <code>XMLOutputter</code> with the specified format
   * characteristics.
   * <p>
   * <b>Note:</b> the format object is cloned internally before use.
   *
   * @param format The Format instance to use. This instance will be cloned() and as
   *               a consequence, changes made to the specified format instance
   *               <b>will not</b> be reflected in this XMLOutputter. A null input
   *               format indicates that XMLOutputter should use the default
   *               {@link Format#getRawFormat()}
   */
  public XMLOutputter2(Format format) {
    this(format, null);
  }

  /**
   * This will create an <code>XMLOutputter</code> with the specified
   * XMLOutputProcessor.
   *
   * @param processor The XMLOutputProcessor to delegate output to. If null the
   *                  XMLOutputter will use the default XMLOutputProcessor.
   */
  public XMLOutputter2(XMLOutputProcessor processor) {
    this(null, processor);
  }

  /*
   * =======================================================================
   * API - Settings...
   * =======================================================================
   */

  /**
   * Sets the new format logic for the XMLOutputter. Note the Format object is
   * cloned internally before use.
   *
   * @param newFormat the format to use for subsequent output
   * @see #getFormat()
   */
  public void setFormat(Format newFormat) {
    this.myFormat = newFormat.clone();
  }

  /**
   * Returns the current format in use by the XMLOutputter. Note the Format
   * object returned is <b>not</b> a clone of the one used internally, thus,
   * an XMLOutputter instance is able to have it's Format changed by changing
   * the settings on the Format instance returned by this method.
   *
   * @return the current Format instance used by this XMLOutputter.
   */
  public Format getFormat() {
    return myFormat;
  }

  /**
   * Returns the current XMLOutputProcessor instance in use by the
   * XMLOutputter.
   *
   * @return the current XMLOutputProcessor instance.
   */
  public XMLOutputProcessor getXMLOutputProcessor() {
    return myProcessor;
  }

  /**
   * Sets a new XMLOutputProcessor instance for this XMLOutputter. Note the
   * processor object is expected to be thread-safe.
   *
   * @param processor the new XMLOutputProcesor to use for output
   */
  public void setXMLOutputProcessor(XMLOutputProcessor processor) {
    this.myProcessor = processor;
  }

  /*
   * =======================================================================
   * API - Output to STREAM Methods ... All methods defer to the WRITER
   * equivalents
   * =======================================================================
   */

  /**
   * This will print the <code>{@link Document}</code> to the given
   * OutputStream. The characters are printed using the encoding specified in
   * the constructor, or a default of UTF-8.
   *
   * @param doc <code>Document</code> to format.
   * @param out <code>OutputStream</code> to use.
   * @throws IOException          if there's any problem writing.
   * @throws NullPointerException if the specified content is null.
   */
  public void output(Document doc, OutputStream out)
    throws IOException {
    output(doc, makeWriter(out, myFormat));
  }

  /**
   * This will print the <code>{@link DocType}</code> to the given
   * OutputStream.
   *
   * @param doctype <code>DocType</code> to output.
   * @param out     <code>OutputStream</code> to use.
   * @throws IOException          if there's any problem writing.
   * @throws NullPointerException if the specified content is null.
   */
  public void output(DocType doctype, OutputStream out) throws IOException {
    output(doctype, makeWriter(out, myFormat));
  }

  /**
   * Print out an <code>{@link Element}</code>, including its
   * <code>{@link Attribute}</code>s, and all contained (child) elements, etc.
   *
   * @param element <code>Element</code> to output.
   * @param out     <code>OutputStream</code> to use.
   * @throws IOException          if there's any problem writing.
   * @throws NullPointerException if the specified content is null.
   */
  public void output(Element element, OutputStream out) throws IOException {
    output(element, makeWriter(out, myFormat));
  }

  /**
   * This will handle printing out an <code>{@link
   * Element}</code>'s content only, not including its tag, and attributes.
   * This can be useful for printing the content of an element that contains
   * HTML, like "&lt;description&gt;JDOM is
   * &lt;b&gt;fun&gt;!&lt;/description&gt;".
   *
   * @param element <code>Element</code> to output.
   * @param out     <code>OutputStream</code> to use.
   * @throws IOException          if there's any problem writing.
   * @throws NullPointerException if the specified content is null.
   */
  public void outputElementContent(Element element, OutputStream out)
    throws IOException {
    outputElementContent(element, makeWriter(out, myFormat));
  }

  /**
   * This will handle printing out a list of nodes. This can be useful for
   * printing the content of an element that contains HTML, like
   * "&lt;description&gt;JDOM is &lt;b&gt;fun&gt;!&lt;/description&gt;".
   * <p>
   * The list is assumed to contain legal JDOM nodes. If other content is
   * coerced on to the list it will cause ClassCastExceptions, and null Lists
   * or null list members will cause NullPointerException.
   *
   * @param list <code>List</code> of nodes.
   * @param out  <code>OutputStream</code> to use.
   * @throws IOException          if there's any problem writing.
   * @throws ClassCastException   if non-{@link Content} is forced in to the list
   * @throws NullPointerException if the List is null or contains null members.
   */
  public void output(List<? extends Content> list, OutputStream out)
    throws IOException {
    output(list, makeWriter(out, myFormat)); // output() flushes
  }

  /**
   * Print out a <code>{@link CDATA}</code> node.
   *
   * @param cdata <code>CDATA</code> to output.
   * @param out   <code>OutputStream</code> to use.
   * @throws IOException          if there's any problem writing.
   * @throws NullPointerException if the specified content is null.
   */
  public void output(CDATA cdata, OutputStream out) throws IOException {
    output(cdata, makeWriter(out, myFormat)); // output() flushes
  }

  /**
   * Print out a <code>{@link Text}</code> node. Perfoms the necessary entity
   * escaping and whitespace stripping.
   *
   * @param text <code>Text</code> to output.
   * @param out  <code>OutputStream</code> to use.
   * @throws IOException          if there's any problem writing.
   * @throws NullPointerException if the specified content is null.
   */
  public void output(Text text, OutputStream out) throws IOException {
    output(text, makeWriter(out, myFormat)); // output() flushes
  }

  /**
   * Print out a <code>{@link Comment}</code>.
   *
   * @param comment <code>Comment</code> to output.
   * @param out     <code>OutputStream</code> to use.
   * @throws IOException          if there's any problem writing.
   * @throws NullPointerException if the specified content is null.
   */
  public void output(Comment comment, OutputStream out) throws IOException {
    output(comment, makeWriter(out, myFormat)); // output() flushes
  }

  /**
   * Print out a <code>{@link ProcessingInstruction}</code>.
   *
   * @param pi  <code>ProcessingInstruction</code> to output.
   * @param out <code>OutputStream</code> to use.
   * @throws IOException          if there's any problem writing.
   * @throws NullPointerException if the specified content is null.
   */
  public void output(ProcessingInstruction pi, OutputStream out)
    throws IOException {
    output(pi, makeWriter(out, myFormat)); // output() flushes
  }

  /**
   * Print out a <code>{@link EntityRef}</code>.
   *
   * @param entity <code>EntityRef</code> to output.
   * @param out    <code>OutputStream</code> to use.
   * @throws IOException          if there's any problem writing.
   * @throws NullPointerException if the specified content is null.
   */
  public void output(EntityRef entity, OutputStream out) throws IOException {
    output(entity, makeWriter(out, myFormat)); // output() flushes
  }

  /*
   * =======================================================================
   * API - Output to STRING Methods ... All methods defer to the WRITER
   * equivalents
   * =======================================================================
   */

  /**
   * Return a string representing a {@link Document}. Uses an internal
   * StringWriter.
   * <p>
   * <b>Warning</b>: a String is Unicode, which may not match the outputter's
   * specified encoding.
   *
   * @param doc <code>Document</code> to format.
   * @return the input content formatted as an XML String.
   * @throws NullPointerException if the specified content is null.
   */
  public String outputString(Document doc) {
    StringWriter out = new StringWriter();
    try {
      output(doc, out); // output() flushes
    }
    catch (IOException e) {
      // swallow - will never happen.
    }
    return out.toString();
  }

  /**
   * Return a string representing a {@link DocType}.
   * <p>
   * <b>Warning</b>: a String is Unicode, which may not match the outputter's
   * specified encoding.
   *
   * @param doctype <code>DocType</code> to format.
   * @return the input content formatted as an XML String.
   * @throws NullPointerException if the specified content is null.
   */
  public String outputString(DocType doctype) {
    StringWriter out = new StringWriter();
    try {
      output(doctype, out); // output() flushes
    }
    catch (IOException e) {
      // swallow - will never happen.
    }
    return out.toString();
  }

  /**
   * Return a string representing an {@link Element}.
   * <p>
   * <b>Warning</b>: a String is Unicode, which may not match the outputter's
   * specified encoding.
   *
   * @param element <code>Element</code> to format.
   * @return the input content formatted as an XML String.
   * @throws NullPointerException if the specified content is null.
   */
  public String outputString(Element element) {
    StringWriter out = new StringWriter();
    try {
      output(element, out); // output() flushes
    }
    catch (IOException e) {
      // swallow - will never happen.
    }
    return out.toString();
  }

  /**
   * Return a string representing a List of {@link Content} nodes. <br>
   * The list is assumed to contain legal JDOM nodes. If other content is
   * coerced on to the list it will cause ClassCastExceptions, and null List
   * members will cause NullPointerException.
   * <p>
   * <b>Warning</b>: a String is Unicode, which may not match the outputter's
   * specified encoding.
   *
   * @param list <code>List</code> to format.
   * @return the input content formatted as an XML String.
   * @throws ClassCastException   if non-{@link Content} is forced in to the list
   * @throws NullPointerException if the List is null or contains null members.
   */
  public String outputString(List<? extends Content> list) {
    StringWriter out = new StringWriter();
    try {
      output(list, out); // output() flushes
    }
    catch (IOException e) {
      // swallow - will never happen.
    }
    return out.toString();
  }

  /**
   * Return a string representing a {@link CDATA} node.
   * <p>
   * <b>Warning</b>: a String is Unicode, which may not match the outputter's
   * specified encoding.
   *
   * @param cdata <code>CDATA</code> to format.
   * @return the input content formatted as an XML String.
   * @throws NullPointerException if the specified content is null.
   */
  public String outputString(CDATA cdata) {
    StringWriter out = new StringWriter();
    try {
      output(cdata, out); // output() flushes
    }
    catch (IOException e) {
      // swallow - will never happen.
    }
    return out.toString();
  }

  /**
   * Return a string representing a {@link Text} node.
   * <p>
   * <b>Warning</b>: a String is Unicode, which may not match the outputter's
   * specified encoding.
   *
   * @param text <code>Text</code> to format.
   * @return the input content formatted as an XML String.
   * @throws NullPointerException if the specified content is null.
   */
  public String outputString(Text text) {
    StringWriter out = new StringWriter();
    try {
      output(text, out); // output() flushes
    }
    catch (IOException e) {
      // swallow - will never happen.
    }
    return out.toString();
  }

  /**
   * Return a string representing a {@link Comment}.
   * <p>
   * <b>Warning</b>: a String is Unicode, which may not match the outputter's
   * specified encoding.
   *
   * @param comment <code>Comment</code> to format.
   * @return the input content formatted as an XML String.
   * @throws NullPointerException if the specified content is null.
   */
  public String outputString(Comment comment) {
    StringWriter out = new StringWriter();
    try {
      output(comment, out); // output() flushes
    }
    catch (IOException e) {
      // swallow - will never happen.
    }
    return out.toString();
  }

  /**
   * Return a string representing a {@link ProcessingInstruction}.
   * <p>
   * <b>Warning</b>: a String is Unicode, which may not match the outputter's
   * specified encoding.
   *
   * @param pi <code>ProcessingInstruction</code> to format.
   * @return the input content formatted as an XML String.
   * @throws NullPointerException if the specified content is null.
   */
  public String outputString(ProcessingInstruction pi) {
    StringWriter out = new StringWriter();
    try {
      output(pi, out); // output() flushes
    }
    catch (IOException e) {
      // swallow - will never happen.
    }
    return out.toString();
  }

  /**
   * Return a string representing an {@link EntityRef}.
   * <p>
   * <b>Warning</b>: a String is Unicode, which may not match the outputter's
   * specified encoding.
   *
   * @param entity <code>EntityRef</code> to format.
   * @return the input content formatted as an XML String.
   * @throws NullPointerException if the specified content is null.
   */
  public String outputString(EntityRef entity) {
    StringWriter out = new StringWriter();
    try {
      output(entity, out); // output() flushes
    }
    catch (IOException e) {
      // swallow - will never happen.
    }
    return out.toString();
  }

  /**
   * This will handle printing out an <code>{@link
   * Element}</code>'s content only, not including its tag, and attributes.
   * This can be useful for printing the content of an element that contains
   * HTML, like "&lt;description&gt;JDOM is
   * &lt;b&gt;fun&gt;!&lt;/description&gt;".
   * <p>
   * <b>Warning</b>: a String is Unicode, which may not match the outputter's
   * specified encoding.
   *
   * @param element <code>Element</code> to output.
   * @return the input content formatted as an XML String.
   * @throws NullPointerException if the specified content is null.
   */
  public String outputElementContentString(Element element) {
    StringWriter out = new StringWriter();
    try {
      outputElementContent(element, out); // output() flushes
    }
    catch (IOException e) {
      // swallow - will never happen.
    }
    return out.toString();
  }

  /*
   * ========================================================================
   * API - Output to WRITER Methods ... These are the core methods that the
   * Stream and String output methods call. On the other hand, these methods
   * defer to the protected/override methods. These methods flush the writer.
   * ========================================================================
   */

  /**
   * This will print the <code>Document</code> to the given Writer.
   * <p>
   * Warning: using your own Writer may cause the outputter's preferred
   * character encoding to be ignored. If you use encodings other than UTF-8,
   * we recommend using the method that takes an OutputStream instead.
   * </p>
   *
   * @param doc <code>Document</code> to format.
   * @param out <code>Writer</code> to use.
   * @throws IOException          - if there's any problem writing.
   * @throws NullPointerException if the specified content is null.
   */
  public void output(Document doc, Writer out) throws IOException {
    myProcessor.process(out, myFormat, doc);
    out.flush();
  }

  /**
   * Print out the <code>{@link DocType}</code>.
   *
   * @param doctype <code>DocType</code> to output.
   * @param out     <code>Writer</code> to use.
   * @throws IOException          - if there's any problem writing.
   * @throws NullPointerException if the specified content is null.
   */
  public void output(DocType doctype, Writer out) throws IOException {
    myProcessor.process(out, myFormat, doctype);
    out.flush();
  }

  /**
   * Print out an <code>{@link Element}</code>, including its
   * <code>{@link Attribute}</code>s, and all contained (child) elements, etc.
   *
   * @param element <code>Element</code> to output.
   * @param out     <code>Writer</code> to use.
   * @throws IOException          - if there's any problem writing.
   * @throws NullPointerException if the specified content is null.
   */
  public void output(Element element, Writer out) throws IOException {
    // If this is the root element we could pre-initialize the
    // namespace stack with the namespaces
    myProcessor.process(out, myFormat, element);
    out.flush();
  }

  /**
   * This will handle printing out an <code>{@link
   * Element}</code>'s content only, not including its tag, and attributes.
   * This can be useful for printing the content of an element that contains
   * HTML, like "&lt;description&gt;JDOM is
   * &lt;b&gt;fun&gt;!&lt;/description&gt;".
   *
   * @param element <code>Element</code> to output.
   * @param out     <code>Writer</code> to use.
   * @throws IOException          - if there's any problem writing.
   * @throws NullPointerException if the specified content is null.
   */
  public void outputElementContent(Element element, Writer out)
    throws IOException {
    myProcessor.process(out, myFormat, element.getContent());
    out.flush();
  }

  /**
   * This will handle printing out a list of nodes. This can be useful for
   * printing the content of an element that contains HTML, like
   * "&lt;description&gt;JDOM is &lt;b&gt;fun&gt;!&lt;/description&gt;".
   *
   * @param list <code>List</code> of nodes.
   * @param out  <code>Writer</code> to use.
   * @throws IOException          - if there's any problem writing.
   * @throws NullPointerException if the specified content is null.
   */
  public void output(List<? extends Content> list, Writer out)
    throws IOException {
    myProcessor.process(out, myFormat, list);
    out.flush();
  }

  /**
   * Print out a <code>{@link CDATA}</code> node.
   *
   * @param cdata <code>CDATA</code> to output.
   * @param out   <code>Writer</code> to use.
   * @throws IOException          - if there's any problem writing.
   * @throws NullPointerException if the specified content is null.
   */
  public void output(CDATA cdata, Writer out) throws IOException {
    myProcessor.process(out, myFormat, cdata);
    out.flush();
  }

  /**
   * Print out a <code>{@link Text}</code> node. Perfoms the necessary entity
   * escaping and whitespace stripping.
   *
   * @param text <code>Text</code> to output.
   * @param out  <code>Writer</code> to use.
   * @throws IOException          - if there's any problem writing.
   * @throws NullPointerException if the specified content is null.
   */
  public void output(Text text, Writer out) throws IOException {
    myProcessor.process(out, myFormat, text);
    out.flush();
  }

  /**
   * Print out a <code>{@link Comment}</code>.
   *
   * @param comment <code>Comment</code> to output.
   * @param out     <code>Writer</code> to use.
   * @throws IOException          - if there's any problem writing.
   * @throws NullPointerException if the specified content is null.
   */
  public void output(Comment comment, Writer out) throws IOException {
    myProcessor.process(out, myFormat, comment);
    out.flush();
  }

  /**
   * Print out a <code>{@link ProcessingInstruction}</code>.
   *
   * @param pi  <code>ProcessingInstruction</code> to output.
   * @param out <code>Writer</code> to use.
   * @throws IOException          - if there's any problem writing.
   * @throws NullPointerException if the specified content is null.
   */
  public void output(ProcessingInstruction pi, Writer out)
    throws IOException {
    myProcessor.process(out, myFormat, pi);
    out.flush();
  }

  /**
   * Print out an <code>{@link EntityRef}</code>.
   *
   * @param entity <code>EntityRef</code> to output.
   * @param out    <code>Writer</code> to use.
   * @throws IOException          - if there's any problem writing.
   * @throws NullPointerException if the specified content is null.
   */
  public void output(EntityRef entity, Writer out) throws IOException {
    myProcessor.process(out, myFormat, entity);
    out.flush();
  }

  /*
   * ========================================================================
   * SpecialCaseMethods for maintaining API Compatibility
   * ========================================================================
   */

  /**
   * Escape any characters in the input string in such a way that the returned
   * value is valid as output in an XML Attribute value.
   *
   * @param str the input String to escape
   * @return the escaped version of the input String
   */
  public String escapeAttributeEntities(String str) {
    return DEFAULTPROCESSOR.escapeAttributeEntities(str, myFormat);
  }

  /**
   * Escape any characters in the input string in such a way that the returned
   * value is valid as output in an XML Element text.
   *
   * @param str the input String to escape
   * @return the escaped version of the input String
   */
  public String escapeElementEntities(String str) {
    return DEFAULTPROCESSOR.escapeElementEntities(str, myFormat);
  }

  /*
   * ========================================================================
   * Basic Support methods.
   * ========================================================================
   */

  /**
   * Returns a cloned copy of this XMLOutputter.
   */
  @Override
  public XMLOutputter2 clone() {
    // Implementation notes: Since all state of an XMLOutputter is
    // embodied in simple private instance variables, Object.clone
    // can be used. Note that since Object.clone is totally
    // broken, we must catch an exception that will never be
    // thrown.
    try {
      return (XMLOutputter2)super.clone();
    }
    catch (CloneNotSupportedException e) {
      // even though this should never ever happen, it's still
      // possible to fool Java into throwing a
      // CloneNotSupportedException. If that happens, we
      // shouldn't swallow it.
      throw new RuntimeException(e.toString());
    }
  }

  /**
   * Return a string listing of the settings for this XMLOutputter instance.
   *
   * @return a string listing the settings for this XMLOutputter instance
   */
  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    buffer.append("XMLOutputter[omitDeclaration = ");
    buffer.append(myFormat.omitDeclaration);
    buffer.append(", ");
    buffer.append("encoding = ");
    buffer.append(myFormat.encoding);
    buffer.append(", ");
    buffer.append("omitEncoding = ");
    buffer.append(myFormat.omitEncoding);
    buffer.append(", ");
    buffer.append("indent = '");
    buffer.append(myFormat.indent);
    buffer.append("'");
    buffer.append(", ");
    buffer.append("expandEmptyElements = ");
    buffer.append(myFormat.expandEmptyElements);
    buffer.append(", ");
    buffer.append("lineSeparator = '");
    for (char ch : myFormat.lineSeparator.toCharArray()) {
      switch (ch) {
        case '\r':
          buffer.append("\\r");
          break;
        case '\n':
          buffer.append("\\n");
          break;
        case '\t':
          buffer.append("\\t");
          break;
        default:
          buffer.append("[" + ((int)ch) + "]");
          break;
      }
    }
    buffer.append("', ");
    buffer.append("textMode = ");
    buffer.append(myFormat.mode + "]");
    return buffer.toString();
  }
}
