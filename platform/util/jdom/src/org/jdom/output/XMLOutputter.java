/*--

 $Id: XMLOutputter.java,v 1.117 2009/07/23 05:54:23 jhunter Exp $

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
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.List;

/**
 * @deprecated Do not use directly. Use JDOMUtil.write instead.
 */
@Deprecated
public class XMLOutputter implements Cloneable {
  // For normal output
  private Format userFormat;

  // For xml:space="preserve"
  private static final Format preserveFormat = Format.getRawFormat();

  @SuppressWarnings("WeakerAccess")
  protected Format currentFormat;

  public XMLOutputter() {
    this((Format)null);
  }

  /**
   * This will create an <code>XMLOutputter</code> with the specified
   * format characteristics.  Note the format object is cloned internally
   * before use.
   */
  public XMLOutputter(@Nullable Format format) {
    userFormat = format == null ? Format.getRawFormat() : format.clone();
    currentFormat = userFormat;
  }

  /**
   * This will create an <code>XMLOutputter</code> with all the
   * options as set in the given <code>XMLOutputter</code>.  Note
   * that <code>XMLOutputter two = (XMLOutputter)one.clone();</code>
   * would work equally well.
   *
   * @param that the XMLOutputter to clone
   */
  public XMLOutputter(XMLOutputter that) {
    this.userFormat = that.userFormat.clone();
    currentFormat = userFormat;
  }

  // * * * * * * * * * * Set parameters methods * * * * * * * * * *
  // * * * * * * * * * * Set parameters methods * * * * * * * * * *

  /**
   * Sets the new format logic for the outputter.  Note the Format
   * object is cloned internally before use.
   *
   * @param newFormat the format to use for output
   */
  public void setFormat(Format newFormat) {
    this.userFormat = newFormat.clone();
    this.currentFormat = userFormat;
  }

  /**
   * Returns the current format in use by the outputter.  Note the
   * Format object returned is a clone of the one used internally.
   */
  public Format getFormat() {
    return userFormat.clone();
  }

  // * * * * * * * * * * Output to a OutputStream * * * * * * * * * *
  // * * * * * * * * * * Output to a OutputStream * * * * * * * * * *

  /**
   * This will print the <code>Document</code> to the given output stream.
   * The characters are printed using the encoding specified in the
   * constructor, or a default of UTF-8.
   *
   * @param doc <code>Document</code> to format.
   * @param out <code>OutputStream</code> to use.
   * @throws IOException - if there's any problem writing.
   */
  public void output(Document doc, OutputStream out)
    throws IOException {
    Writer writer = makeWriter(out);
    output(doc, writer);  // output() flushes
  }

  /**
   * Print out an <code>{@link Element}</code>, including
   * its <code>{@link Attribute}</code>s, and all
   * contained (child) elements, etc.
   *
   * @param element <code>Element</code> to output.
   * @param out     <code>Writer</code> to use.
   */
  public void output(Element element, OutputStream out) throws IOException {
    Writer writer = makeWriter(out);
    output(element, writer);  // output() flushes
  }

  /**
   * Get an OutputStreamWriter, using prefered encoding
   * (see {@link Format#setEncoding}).
   */
  private Writer makeWriter(OutputStream out)
    throws UnsupportedEncodingException {
    return makeWriter(out, userFormat.encoding);
  }

  /**
   * Get an OutputStreamWriter, use specified encoding.
   */
  private static Writer makeWriter(OutputStream out, String enc)
    throws UnsupportedEncodingException {
    // "UTF-8" is not recognized before JDK 1.1.6, so we'll translate
    // into "UTF8" which works with all JDKs.
    if ("UTF-8".equals(enc)) {
      enc = "UTF8";
    }

    return new BufferedWriter(
      (new OutputStreamWriter(
        new BufferedOutputStream(out), enc)
      ));
  }

  // * * * * * * * * * * Output to a Writer * * * * * * * * * *
  // * * * * * * * * * * Output to a Writer * * * * * * * * * *

  /**
   * This will print the <code>Document</code> to the given Writer.
   *
   * <p>
   * Warning: using your own Writer may cause the outputter's
   * preferred character encoding to be ignored.  If you use
   * encodings other than UTF-8, we recommend using the method that
   * takes an OutputStream instead.
   * </p>
   *
   * @param doc <code>Document</code> to format.
   * @param out <code>Writer</code> to use.
   * @throws IOException - if there's any problem writing.
   */
  public void output(Document doc, Writer out) throws IOException {
    printDeclaration(out, userFormat.encoding);

    // Print out root element, as well as any root level
    // comments and processing instructions,
    // starting with no indentation
    List<Content> content = doc.getContent();
    int size = content.size();
    for (int i = 0; i < size; i++) {
      Content obj = content.get(i);
      if (obj instanceof Element) {
        printElement(out, doc.getRootElement(), 0, new NamespaceStack());
      }
      else if (obj instanceof DocType) {
        printDocType(out, doc.getDocType());
        // Always print line separator after declaration, helps the
        // output look better and is semantically inconsequential
        writeLineSeparator(out);
      }

      newline(out);
      indent(out, 0);
    }

    // Output final line separator
    // We output this no matter what the newline flags say
    writeLineSeparator(out);

    out.flush();
  }

  private void writeLineSeparator(Writer out) throws IOException {
    String lineSeparator = currentFormat.getLineSeparator();
    if (lineSeparator != null) {
      out.write(lineSeparator);
    }
  }

  /**
   * Print out the <code>{@link DocType}</code>.
   *
   * @param doctype <code>DocType</code> to output.
   * @param out     <code>Writer</code> to use.
   */
  public void output(DocType doctype, Writer out) throws IOException {
    printDocType(out, doctype);
    out.flush();
  }

  /**
   * Print out an <code>{@link Element}</code>, including
   * its <code>{@link Attribute}</code>s, and all
   * contained (child) elements, etc.
   *
   * @param element <code>Element</code> to output.
   * @param out     <code>Writer</code> to use.
   */
  public void output(Element element, Writer out) throws IOException {
    // If this is the root element, we could pre-initialize the namespace stack with the namespaces
    printElement(out, element, 0, new NamespaceStack());
    out.flush();
  }

  /**
   * Print out a <code>{@link CDATA}</code> node.
   *
   * @param cdata <code>CDATA</code> to output.
   * @param out   <code>Writer</code> to use.
   */
  public void output(CDATA cdata, Writer out) throws IOException {
    printCDATA(out, cdata);
    out.flush();
  }

  /**
   * Print out a <code>{@link Text}</code> node.  Perfoms
   * the necessary entity escaping and whitespace stripping.
   *
   * @param text <code>Text</code> to output.
   * @param out  <code>Writer</code> to use.
   */
  public void output(Text text, Writer out) throws IOException {
    printText(out, text);
    out.flush();
  }

  /**
   * Print out a <code>{@link Comment}</code>.
   *
   * @param comment <code>Comment</code> to output.
   * @param out     <code>Writer</code> to use.
   */
  public void output(Comment comment, Writer out) throws IOException {
    printComment(out, comment);
    out.flush();
  }

  /**
   * Print out a <code>{@link ProcessingInstruction}</code>.
   *
   * @param pi  <code>ProcessingInstruction</code> to output.
   * @param out <code>Writer</code> to use.
   */
  public void output(ProcessingInstruction pi, Writer out) throws IOException {
  }

  /**
   * Print out a <code>{@link EntityRef}</code>.
   *
   * @param entity <code>EntityRef</code> to output.
   * @param out    <code>Writer</code> to use.
   */
  public void output(EntityRef entity, Writer out) throws IOException {
    printEntityRef(out, entity);
    out.flush();
  }

  /**
   * Return a string representing a document.  Uses an internal
   * StringWriter. Warning: a String is Unicode, which may not match
   * the outputter's specified encoding.
   *
   * @param doc <code>Document</code> to format.
   */
  public String outputString(Document doc) {
    StringWriter out = new StringWriter();
    try {
      // output() flushes
      output(doc, out);
    }
    catch (IOException ignored) {
    }
    return out.toString();
  }

  /**
   * Return a string representing a DocType. Warning: a String is
   * Unicode, which may not match the outputter's specified
   * encoding.
   *
   * @param doctype <code>DocType</code> to format.
   */
  public String outputString(DocType doctype) {
    StringWriter out = new StringWriter();
    try {
      printDocType(out, doctype);
    }
    catch (IOException ignored) {
    }
    return out.toString();
  }

  /**
   * Return a string representing an element. Warning: a String is
   * Unicode, which may not match the outputter's specified
   * encoding.
   *
   * @param element <code>Element</code> to format.
   */
  public String outputString(Element element) {
    StringWriter out = new StringWriter();
    try {
      // If this is the root element, we could pre-initialize the namespace stack with the namespaces
      printElement(out, element, 0, new NamespaceStack());
    }
    catch (IOException ignored) {
    }
    return out.toString();
  }

  /**
   * Return a string representing a list of nodes.  The list is assumed to contain legal JDOM nodes.
   *
   * @param list <code>List</code> to format.
   */
  public String outputString(List list) {
    StringWriter out = new StringWriter();
    try {
      printContentRange(out, list, 0, list.size(), 0, new NamespaceStack());
    }
    catch (IOException ignored) {
    }
    return out.toString();
  }

  /**
   * Return a string representing a CDATA node. Warning: a String is Unicode, which may not match the outputter's specified encoding.
   *
   * @param cdata <code>CDATA</code> to format.
   */
  public String outputString(CDATA cdata) {
    StringWriter out = new StringWriter();
    try {
      printCDATA(out, cdata);
    }
    catch (IOException ignored) {
    }
    return out.toString();
  }

  /**
   * Return a string representing a Text node. Warning: a String is
   * Unicode, which may not match the outputter's specified
   * encoding.
   *
   * @param text <code>Text</code> to format.
   */
  public String outputString(Text text) {
    StringWriter out = new StringWriter();
    try {
      printText(out, text);
    }
    catch (IOException ignored) {
    }
    return out.toString();
  }

  /**
   * Return a string representing an entity. Warning: a String is
   * Unicode, which may not match the outputter's specified
   * encoding.
   *
   * @param entity <code>EntityRef</code> to format.
   */
  public static String outputString(EntityRef entity) {
    StringWriter out = new StringWriter();
    try {
      printEntityRef(out, entity);
    }
    catch (IOException ignored) {
    }
    return out.toString();
  }

  // * * * * * * * * * * Internal printing methods * * * * * * * * * *
  // * * * * * * * * * * Internal printing methods * * * * * * * * * *

  /**
   * This will handle printing of the declaration.
   * Assumes XML version 1.0 since we don't directly know.
   *
   * @param out      <code>Writer</code> to use.
   * @param encoding The encoding to add to the declaration
   */
  private void printDeclaration(Writer out, String encoding) throws IOException {
    // Only print the declaration if it's not being omitted
    if (!userFormat.omitDeclaration) {
      // assume 1.0 version
      out.write("<?xml version=\"1.0\"");
      if (!userFormat.omitEncoding) {
        out.write(" encoding=\"" + encoding + "\"");
      }
      out.write("?>");

      // Print new line after decl always, even if no other new lines
      // Helps the output look better and is semantically
      // inconsequential
      writeLineSeparator(out);
    }
  }

  /**
   * This handle printing the DOCTYPE declaration if one exists.
   *
   * @param docType <code>Document</code> whose declaration to write.
   * @param out     <code>Writer</code> to use.
   */
  private void printDocType(Writer out, DocType docType) throws IOException {

    String publicID = docType.getPublicID();
    String systemID = docType.getSystemID();
    String internalSubset = docType.getInternalSubset();
    boolean hasPublic = false;

    out.write("<!DOCTYPE ");
    out.write(docType.getElementName());
    if (publicID != null) {
      out.write(" PUBLIC \"");
      out.write(publicID);
      out.write("\"");
      hasPublic = true;
    }
    if (systemID != null) {
      if (!hasPublic) {
        out.write(" SYSTEM");
      }
      out.write(" \"");
      out.write(systemID);
      out.write("\"");
    }
    if ((internalSubset != null) && (!internalSubset.isEmpty())) {
      out.write(" [");
      writeLineSeparator(out);
      out.write(docType.getInternalSubset());
      out.write("]");
    }
    out.write(">");
  }

  /**
   * This will handle printing of comments.
   *
   * @param comment <code>Comment</code> to write.
   * @param out     <code>Writer</code> to use.
   */
  private static void printComment(Writer out, Comment comment) throws IOException {
    out.write("<!--");
    out.write(comment.getText());
    out.write("-->");
  }

  /**
   * This will handle printing a <code>{@link EntityRef}</code>.
   * Only the entity reference such as <code>&amp;entity;</code>
   * will be printed. However, subclasses are free to override
   * this method to print the contents of the entity instead.
   *
   * @param entity <code>EntityRef</code> to output.
   * @param out    <code>Writer</code> to use.
   */
  private static void printEntityRef(Writer out, EntityRef entity) throws IOException {
    out.write("&");
    out.write(entity.getName());
    out.write(";");
  }

  /**
   * This will handle printing of <code>{@link CDATA}</code> text.
   *
   * @param cdata <code>CDATA</code> to output.
   * @param out   <code>Writer</code> to use.
   */
  private void printCDATA(Writer out, CDATA cdata) throws IOException {
    String str = (currentFormat.mode == Format.TextMode.NORMALIZE)
                 ? cdata.getTextNormalize()
                 : ((currentFormat.mode == Format.TextMode.TRIM) ?
                    cdata.getText().trim() : cdata.getText());
    out.write("<![CDATA[");
    out.write(str);
    out.write("]]>");
  }

  /**
   * This will handle printing of <code>{@link Text}</code> strings.
   *
   * @param text <code>Text</code> to write.
   * @param out  <code>Writer</code> to use.
   */
  protected void printText(Writer out, Text text) throws IOException {
    String str = (currentFormat.mode == Format.TextMode.NORMALIZE)
                 ? text.getTextNormalize()
                 : ((currentFormat.mode == Format.TextMode.TRIM) ?
                    text.getText().trim() : text.getText());
    out.write(escapeElementEntities(str));
  }

  /**
   * This will handle printing a string.  Escapes the element entities,
   * trims interior whitespace, etc. if necessary.
   */
  private void printString(Writer out, String str) throws IOException {
    if (currentFormat.mode == Format.TextMode.NORMALIZE) {
      str = Text.normalizeString(str);
    }
    else if (currentFormat.mode == Format.TextMode.TRIM) {
      str = str.trim();
    }
    out.write(escapeElementEntities(str));
  }

  /**
   * This will handle printing of a <code>{@link Element}</code>,
   * its <code>{@link Attribute}</code>s, and all contained (child)
   * elements, etc.
   *
   * @param element    <code>Element</code> to output.
   * @param out        <code>Writer</code> to use.
   * @param level      <code>int</code> level of indention.
   * @param namespaces <code>List</code> stack of Namespaces in scope.
   */
  private void printElement(Writer out, Element element, int level, NamespaceStack namespaces) throws IOException {
    List<Attribute> attributes = element.getAttributes();
    List<Content> content = element.getContent();

    // Check for xml:space and adjust format settings
    String space = null;
    if (attributes != null) {
      space = element.getAttributeValue("space", Namespace.XML_NAMESPACE);
    }

    Format previousFormat = currentFormat;

    if ("default".equals(space)) {
      currentFormat = userFormat;
    }
    else if ("preserve".equals(space)) {
      currentFormat = preserveFormat;
    }

    // Print the beginning of the tag plus attributes and any
    // necessary namespace declarations
    out.write("<");
    printQualifiedName(out, element);

    // Mark our namespace starting point
    int previouslyDeclaredNamespaces = namespaces.size();

    // Print the element's namespace, if appropriate
    printElementNamespace(out, element, namespaces);

    // Print out additional namespace declarations
    printAdditionalNamespaces(out, element, namespaces);

    // Print out attributes
    if (attributes != null) {
      printAttributes(out, attributes, namespaces);
    }

    // Depending on the settings (newlines, textNormalize, etc), we may
    // or may not want to print all of the content, so determine the
    // index of the start of the content we're interested
    // in based on the current settings.

    int start = skipLeadingWhite(content, 0);
    int size = content.size();
    if (start >= size) {
      // Case content is empty or all insignificant whitespace
      if (currentFormat.expandEmptyElements) {
        out.write("></");
        printQualifiedName(out, element);
        out.write(">");
      }
      else {
        out.write(" />");
      }
    }
    else {
      out.write(">");

      // For a special case where the content is only CDATA
      // or Text we don't want to indent after the start or
      // before the end tag.

      if (nextNonText(content, start) < size) {
        // Case Mixed Content - normal indentation
        newline(out);
        printContentRange(out, content, start, size,
                          level + 1, namespaces);
        newline(out);
        indent(out, level);
      }
      else {
        // Case all CDATA or Text - no indentation
        printTextRange(out, content, start, size);
      }
      out.write("</");
      printQualifiedName(out, element);
      out.write(">");
    }

    // remove declared namespaces from stack
    while (namespaces.size() > previouslyDeclaredNamespaces) {
      namespaces.pop();
    }

    // Restore our format settings
    currentFormat = previousFormat;
  }

  /**
   * This will handle printing of content within a given range.
   * The range to print is specified in typical Java fashion; the
   * starting index is inclusive, while the ending index is
   * exclusive.
   *
   * @param content    <code>List</code> of content to output
   * @param start      index of first content node (inclusive.
   * @param end        index of last content node (exclusive).
   * @param out        <code>Writer</code> to use.
   * @param level      <code>int</code> level of indentation.
   * @param namespaces <code>List</code> stack of Namespaces in scope.
   */
  private void printContentRange(Writer out, List<Content> content, int start, int end, int level, NamespaceStack namespaces)
    throws IOException {
    boolean firstNode; // Flag for 1st node in content
    Object next;       // Node we're about to print
    int first, index;  // Indexes into the list of content

    index = start;
    while (index < end) {
      firstNode = index == start;
      next = content.get(index);

      //
      // Handle consecutive CDATA, Text, and EntityRef nodes all at once
      //
      if ((next instanceof Text) || (next instanceof EntityRef)) {
        first = skipLeadingWhite(content, index);
        // Set index to next node for loop
        index = nextNonText(content, first);

        // If it's not all whitespace - print it!
        if (first < index) {
          if (!firstNode) {
            newline(out);
          }
          indent(out, level);
          printTextRange(out, content, first, index);
        }
        continue;
      }

      //
      // Handle other nodes
      //
      if (!firstNode) {
        newline(out);
      }

      indent(out, level);

      if (next instanceof Element) {
        printElement(out, (Element)next, level, namespaces);
      }
      else {
        // XXX if we get here then we have a illegal content, for
        //     now we'll just ignore it (probably should throw
        //     a exception)
      }

      index++;
    } /* while */
  }

  /**
   * This will handle printing of a sequence of <code>{@link CDATA}</code>
   * or <code>{@link Text}</code> nodes.  It is an error to have any other
   * pass this method any other type of node.
   *
   * @param content <code>List</code> of content to output
   * @param start   index of first content node (inclusive).
   * @param end     index of last content node (exclusive).
   * @param out     <code>Writer</code> to use.
   */
  private void printTextRange(Writer out, List content, int start, int end) throws IOException {
    String previous; // Previous text printed
    Object node;     // Next node to print
    String next;     // Next text to print

    previous = null;

    // Remove leading whitespace-only nodes
    start = skipLeadingWhite(content, start);

    int size = content.size();
    if (start < size) {
      // And remove trialing whitespace-only nodes
      end = skipTrailingWhite(content, end);

      for (int i = start; i < end; i++) {
        node = content.get(i);

        // Get the unmangled version of the text
        // we are about to print
        if (node instanceof Text) {
          next = ((Text)node).getText();
        }
        else if (node instanceof EntityRef) {
          next = "&" + ((EntityRef)node).getValue() + ";";
        }
        else {
          throw new IllegalStateException("Should see only CDATA, Text, or EntityRef");
        }

        // This may save a little time
        if (next == null || next.isEmpty()) {
          continue;
        }

        // Determine if we need to pad the output (padding is
        // only need in trim or normalizing mode)
        if (previous != null) { // Not 1st node
          if (currentFormat.mode == Format.TextMode.NORMALIZE ||
              currentFormat.mode == Format.TextMode.TRIM) {
            if ((endsWithWhite(previous)) ||
                (startsWithWhite(next))) {
              out.write(" ");
            }
          }
        }

        // Print the node
        if (node instanceof CDATA) {
          printCDATA(out, (CDATA)node);
        }
        else if (node instanceof EntityRef) {
          printEntityRef(out, (EntityRef)node);
        }
        else {
          printString(out, next);
        }

        previous = next;
      }
    }
  }

  /**
   * This will handle printing of any needed <code>{@link Namespace}</code>
   * declarations.
   *
   * @param ns  <code>Namespace</code> to print definition of
   * @param out <code>Writer</code> to use.
   */
  private void printNamespace(Writer out, Namespace ns,
                              NamespaceStack namespaces)
    throws IOException {
    String prefix = ns.getPrefix();
    String uri = ns.getURI();

    // Already printed namespace decl?
    if (uri.equals(namespaces.getURI(prefix))) {
      return;
    }

    out.write(" xmlns");
    if (!prefix.isEmpty()) {
      out.write(":");
      out.write(prefix);
    }
    out.write("=\"");
    out.write(escapeAttributeEntities(uri));
    out.write("\"");
    namespaces.push(ns);
  }

  /**
   * This will handle printing of a <code>{@link Attribute}</code> list.
   *
   * @param attributes <code>List</code> of Attribute objcts
   * @param out        <code>Writer</code> to use
   */
  private void printAttributes(Writer out, List<? extends Attribute> attributes, NamespaceStack namespaces)
    throws IOException {

    // I do not yet handle the case where the same prefix maps to
    // two different URIs. For attributes on the same element
    // this is illegal; but as yet we don't throw an exception
    // if someone tries to do this
    // Set prefixes = new HashSet();
    for (int i = 0; i < attributes.size(); i++) {
      Attribute attribute = attributes.get(i);
      Namespace ns = attribute.getNamespace();
      if ((ns != Namespace.NO_NAMESPACE) && (ns != Namespace.XML_NAMESPACE)) {
        printNamespace(out, ns, namespaces);
      }

      out.write(" ");
      printQualifiedName(out, attribute);
      out.write("=");

      out.write("\"");
      out.write(escapeAttributeEntities(attribute.getValue()));
      out.write("\"");
    }
  }

  private void printElementNamespace(Writer out, Element element,
                                     NamespaceStack namespaces)
    throws IOException {
    // Add namespace decl only if it's not the XML namespace and it's
    // not the NO_NAMESPACE with the prefix "" not yet mapped
    // (we do output xmlns="" if the "" prefix was already used and we
    // need to reclaim it for the NO_NAMESPACE)
    Namespace ns = element.getNamespace();
    if (ns == Namespace.XML_NAMESPACE) {
      return;
    }
    if (!((ns == Namespace.NO_NAMESPACE) &&
          (namespaces.getURI("") == null))) {
      printNamespace(out, ns, namespaces);
    }
  }

  private void printAdditionalNamespaces(Writer out, Element element,
                                         NamespaceStack namespaces)
    throws IOException {
    List list = element.getAdditionalNamespaces();
    if (list != null) {
      for (int i = 0; i < list.size(); i++) {
        Namespace additional = (Namespace)list.get(i);
        printNamespace(out, additional, namespaces);
      }
    }
  }

  // * * * * * * * * * * Support methods * * * * * * * * * *
  // * * * * * * * * * * Support methods * * * * * * * * * *

  /**
   * This will print a newline only if indent is not null.
   *
   * @param out <code>Writer</code> to use
   */
  private void newline(Writer out) throws IOException {
    if (currentFormat.indent != null) {
      writeLineSeparator(out);
    }
  }

  /**
   * This will print indents only if indent is not null or the empty string.
   *
   * @param out   <code>Writer</code> to use
   * @param level current indent level
   */
  private void indent(Writer out, int level) throws IOException {
    if (currentFormat.indent == null ||
        currentFormat.indent.isEmpty()) {
      return;
    }

    for (int i = 0; i < level; i++) {
      out.write(currentFormat.indent);
    }
  }

  // Returns the index of the first non-all-whitespace CDATA or Text,
  // index = content.size() is returned if content contains
  // all whitespace.
  // @param start index to begin search (inclusive)
  private int skipLeadingWhite(List content, int start) {
    if (start < 0) {
      start = 0;
    }

    int index = start;
    int size = content.size();
    if (currentFormat.mode == Format.TextMode.TRIM_FULL_WHITE
        || currentFormat.mode == Format.TextMode.NORMALIZE
        || currentFormat.mode == Format.TextMode.TRIM) {
      while (index < size) {
        if (!isAllWhitespace(content.get(index))) {
          return index;
        }
        index++;
      }
    }
    return index;
  }

  // Return the index + 1 of the last non-all-whitespace CDATA or
  // Text node,  index < 0 is returned
  // if content contains all whitespace.
  // @param start index to begin search (exclusive)
  private int skipTrailingWhite(List content, int start) {
    int size = content.size();
    if (start > size) {
      start = size;
    }

    int index = start;
    if (currentFormat.mode == Format.TextMode.TRIM_FULL_WHITE
        || currentFormat.mode == Format.TextMode.NORMALIZE
        || currentFormat.mode == Format.TextMode.TRIM) {
      while (index >= 0) {
        if (!isAllWhitespace(content.get(index - 1))) {
          break;
        }
        --index;
      }
    }
    return index;
  }

  // Return the next non-CDATA, non-Text, or non-EntityRef node,
  // index = content.size() is returned if there is no more non-CDATA,
  // non-Text, or non-EntiryRef nodes
  // @param start index to begin search (inclusive)
  private static int nextNonText(List content, int start) {
    if (start < 0) {
      start = 0;
    }

    int index = start;
    int size = content.size();
    while (index < size) {
      Object node = content.get(index);
      if (!((node instanceof Text) || (node instanceof EntityRef))) {
        return index;
      }
      index++;
    }
    return size;
  }

  // Determine if a Object is all whitespace
  private static boolean isAllWhitespace(Object obj) {
    String str;
    if (obj instanceof String) {
      str = (String)obj;
    }
    else if (obj instanceof Text) {
      str = ((Text)obj).getText();
    }
    else if (obj instanceof EntityRef) {
      return false;
    }
    else {
      return false;
    }

    for (int i = 0; i < str.length(); i++) {
      if (!Verifier.isXMLWhitespace(str.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  // Determine if a string starts with a XML whitespace.
  private static boolean startsWithWhite(String str) {
    if ((str != null) &&
        (!str.isEmpty()) &&
        Verifier.isXMLWhitespace(str.charAt(0))) {
      return true;
    }
    return false;
  }

  // Determine if a string ends with a XML whitespace.
  private static boolean endsWithWhite(String str) {
    if ((str != null) && (!str.isEmpty()) && Verifier.isXMLWhitespace(str.charAt(str.length() - 1))) {
      return true;
    }
    return false;
  }

  /**
   * This will take the pre-defined entities in XML 1.0 and
   * convert their character representation to the appropriate
   * entity reference, suitable for XML attributes.  It does not convert
   * the single quote (') because it's not necessary as the outputter
   * writes attributes surrounded by double-quotes.
   *
   * @param str <code>String</code> input to escape.
   * @return <code>String</code> with escaped content.
   * @throws IllegalArgumentException if an entity can not be escaped
   */
  public String escapeAttributeEntities(String str) {
    StringBuilder buffer;
    int ch, pos;
    String entity;
    EscapeStrategy strategy = currentFormat.escapeStrategy;

    buffer = null;
    for (int i = 0; i < str.length(); i++) {
      ch = str.charAt(i);
      pos = i;
      switch (ch) {
        case '<':
          entity = "&lt;";
          break;
        case '>':
          entity = "&gt;";
          break;
/*
                case '\'' :
                    entity = "&apos;";
                    break;
*/
        case '\"':
          entity = "&quot;";
          break;
        case '&':
          entity = "&amp;";
          break;
        case '\r':
          entity = "&#xD;";
          break;
        case '\t':
          entity = "&#x9;";
          break;
        case '\n':
          entity = "&#xA;";
          break;
        default:

          if (strategy.shouldEscape((char)ch)) {
            // Make sure what we are escaping is not the
            // Beginning of a multi-byte character.
            if (Verifier.isHighSurrogate((char)ch)) {
              // This is the high of a surrogate pair
              i++;
              if (i < str.length()) {
                char low = str.charAt(i);
                if (!Verifier.isLowSurrogate(low)) {
                  throw new IllegalDataException("Could not decode surrogate pair 0x" +
                                                 Integer.toHexString(ch) + " / 0x" + Integer.toHexString(low));
                }
                ch = Verifier.decodeSurrogatePair((char)ch, low);
              }
              else {
                throw new IllegalDataException("Surrogate pair 0x" +
                                               Integer.toHexString(ch) + " truncated");
              }
            }
            entity = "&#x" + Integer.toHexString(ch) + ";";
          }
          else {
            entity = null;
          }
          break;
      }
      if (buffer == null) {
        if (entity != null) {
          // An entity occurred, so we'll have to use StringBuffer
          // (allocate room for it plus a few more entities).
          buffer = new StringBuilder(str.length() + 20);
          // Copy previous skipped characters and fall through
          // to pickup current character
          buffer.append(str, 0, pos);
          buffer.append(entity);
        }
      }
      else {
        if (entity == null) {
          buffer.append((char)ch);
        }
        else {
          buffer.append(entity);
        }
      }
    }

    // If there were any entities, return the escaped characters
    // that we put in the StringBuffer. Otherwise, just return
    // the unmodified input string.
    return (buffer == null) ? str : buffer.toString();
  }


  /**
   * This will take the three pre-defined entities in XML 1.0
   * (used specifically in XML elements) and convert their character
   * representation to the appropriate entity reference, suitable for
   * XML element content.
   *
   * @param str <code>String</code> input to escape.
   * @return <code>String</code> with escaped content.
   * @throws IllegalArgumentException if an entity can not be escaped
   */
  public String escapeElementEntities(String str) {
    /*
     * Whether output escaping is enabled for the being processed
     * Element - default is <code>true</code>
     */
    boolean escapeOutput = true;
    if (!escapeOutput) return str;

    StringBuilder buffer;
    int ch, pos;
    String entity;
    EscapeStrategy strategy = currentFormat.escapeStrategy;

    buffer = null;
    for (int i = 0; i < str.length(); i++) {
      ch = str.charAt(i);
      pos = i;
      switch (ch) {
        case '<':
          entity = "&lt;";
          break;
        case '>':
          entity = "&gt;";
          break;
        case '&':
          entity = "&amp;";
          break;
        case '\r':
          entity = "&#xD;";
          break;
        case '\n':
          entity = currentFormat.getLineSeparator();
          break;
        default:

          if (strategy.shouldEscape((char)ch)) {

            //make sure what we are escaping is not the
            //beginning of a multi-byte character.
            if (Verifier.isHighSurrogate((char)ch)) {
              //this is a the high of a surrogate pair
              i++;
              if (i < str.length()) {
                char low = str.charAt(i);
                if (!Verifier.isLowSurrogate(low)) {
                  throw new IllegalDataException("Could not decode surrogate pair 0x" +
                                                 Integer.toHexString(ch) + " / 0x" + Integer.toHexString(low));
                }
                ch = Verifier.decodeSurrogatePair((char)ch, low);
              }
              else {
                throw new IllegalDataException("Surrogate pair 0x" +
                                               Integer.toHexString(ch) + " truncated");
              }
            }
            entity = "&#x" + Integer.toHexString(ch) + ";";
          }
          else {
            entity = null;
          }
          break;
      }
      if (buffer == null) {
        if (entity != null) {
          // An entity occurred, so we'll have to use StringBuffer
          // (allocate room for it plus a few more entities).
          buffer = new StringBuilder(str.length() + 20);
          // Copy previous skipped characters and fall through
          // to pickup current character
          buffer.append(str, 0, pos);
          buffer.append(entity);
        }
      }
      else {
        if (entity == null) {
          buffer.append((char)ch);
        }
        else {
          buffer.append(entity);
        }
      }
    }

    // If there were any entities, return the escaped characters
    // that we put in the StringBuffer. Otherwise, just return
    // the unmodified input string.
    return (buffer == null) ? str : buffer.toString();
  }

  /**
   * Returns a copy of this XMLOutputter.
   */
  @Override
  public Object clone() {
    // Implementation notes: Since all state of an XMLOutputter is
    // embodied in simple private instance variables, Object.clone
    // can be used.  Note that since Object.clone is totally
    // broken, we must catch an exception that will never be
    // thrown.
    try {
      return super.clone();
    }
    catch (CloneNotSupportedException e) {
      // even though this should never ever happen, it's still
      // possible to fool Java into throwing a
      // CloneNotSupportedException.  If that happens, we
      // shouldn't swallow it.
      throw new RuntimeException(e.toString());
    }
  }

  /**
   * Return a string listing of the settings for this
   * XMLOutputter instance.
   *
   * @return a string listing the settings for this XMLOutputter instance
   */
  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    String lineSeparator = userFormat.getLineSeparator();
    if (lineSeparator != null) {
      for (int i = 0; i < lineSeparator.length(); i++) {
        char ch = lineSeparator.charAt(i);
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
            buffer.append("[").append((int)ch).append("]");
            break;
        }
      }
    }
    else {
      buffer.append("null");
    }

    return (
      "XMLOutputter[omitDeclaration = " + userFormat.omitDeclaration + ", " +
      "encoding = " + userFormat.encoding + ", " +
      "omitEncoding = " + userFormat.omitEncoding + ", " +
      "indent = '" + userFormat.indent + "'" + ", " +
      "expandEmptyElements = " + userFormat.expandEmptyElements + ", " +
      "lineSeparator = '" + buffer + "', " +
      "textMode = " + userFormat.mode + "]"
    );
  }

  // Support method to print a name without using elt.getQualifiedName()
  // and thus avoiding a StringBuffer creation and memory churn
  private static void printQualifiedName(Writer out, Element e) throws IOException {
    if (e.getNamespace().getPrefix().isEmpty()) {
      out.write(e.getName());
    }
    else {
      out.write(e.getNamespace().getPrefix());
      out.write(':');
      out.write(e.getName());
    }
  }

  // Support method to print a name without using att.getQualifiedName()
  // and thus avoiding a StringBuffer creation and memory churn
  private static void printQualifiedName(Writer out, Attribute a) throws IOException {
    String prefix = a.getNamespace().getPrefix();
    if ((prefix != null) && (!prefix.isEmpty())) {
      out.write(prefix);
      out.write(':');
      out.write(a.getName());
    }
    else {
      out.write(a.getName());
    }
  }
}
