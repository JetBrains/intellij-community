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
package com.intellij.configurationStore;

import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.application.PathMacroFilter;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.*;
import org.jdom.output.Format;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.transform.Result;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;

@SuppressWarnings("Duplicates")
public final class JbXmlOutputter {
  private static final Format DEFAULT_FORMAT = JDOMUtil.createFormat("\n");

  // For normal output
  private final Format format;
  @Nullable
  private final JDOMUtil.ElementOutputFilter elementFilter;

  @Nullable
  private final ReplacePathToMacroMap macroMap;
  @Nullable
  private final PathMacroFilter macroFilter;

  public JbXmlOutputter(@NotNull String lineSeparator,
                        @Nullable JDOMUtil.ElementOutputFilter elementFilter,
                        @Nullable ReplacePathToMacroMap macroMap,
                        @Nullable PathMacroFilter macroFilter) {
    this.format = DEFAULT_FORMAT.getLineSeparator().equals(lineSeparator) ? DEFAULT_FORMAT : JDOMUtil.createFormat(lineSeparator);
    this.elementFilter = elementFilter;
    this.macroMap = macroMap;
    this.macroFilter = macroFilter;
  }

  public static void collapseMacrosAndWrite(@NotNull Element element, @NotNull ComponentManager project, @NotNull Writer writer) throws IOException {
    PathMacroManager macroManager = PathMacroManager.getInstance(project);
    JbXmlOutputter xmlWriter = new JbXmlOutputter("\n", null, macroManager.getReplacePathMap(), macroManager.getMacroFilter());
    xmlWriter.output(element, writer);
  }

  @NotNull
  public static String collapseMacrosAndWrite(@NotNull Element element, @NotNull ComponentManager project) throws IOException {
    StringWriter writer = new StringWriter();
    collapseMacrosAndWrite(element, project, writer);
    return writer.toString();
  }

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
    printDeclaration(out, format.getEncoding());

    // Print out root element, as well as any root level comments and processing instructions, starting with no indentation
    List<Content> content = doc.getContent();
    for (Content obj : content) {
      if (obj instanceof Element) {
        printElement(out, doc.getRootElement(), 0);
      }
      else if (obj instanceof Comment) {
        printComment(out, (Comment)obj);
      }
      else if (obj instanceof ProcessingInstruction) {
        printProcessingInstruction(out, (ProcessingInstruction)obj);
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
    if (format.getLineSeparator() != null) {
      out.write(format.getLineSeparator());
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

  public void output(@NotNull Element element, @NotNull Writer out) throws IOException {
    printElement(out, element, 0);
  }

  /**
   * This will handle printing out a list of nodes.
   * This can be useful for printing the content of an element that
   * contains HTML, like "&lt;description&gt;JDOM is
   * &lt;b&gt;fun&gt;!&lt;/description&gt;".
   *
   * @param list <code>List</code> of nodes.
   * @param out  <code>Writer</code> to use.
   */
  public void output(List<Content> list, Writer out) throws IOException {
    printContentRange(out, list, 0, list.size(), 0);
    out.flush();
  }

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
    if (!format.getOmitDeclaration()) {
      // Assume 1.0 version
      out.write("<?xml version=\"1.0\"");
      if (!format.getOmitEncoding()) {
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
    if (internalSubset != null && !internalSubset.isEmpty()) {
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
  private static void printComment(Writer out, Comment comment)
    throws IOException {
    out.write("<!--");
    out.write(comment.getText());
    out.write("-->");
  }

  /**
   * This will handle printing of processing instructions.
   *
   * @param pi  <code>ProcessingInstruction</code> to write.
   * @param out <code>Writer</code> to use.
   */
  private void printProcessingInstruction(Writer out, ProcessingInstruction pi) throws IOException {
    String target = pi.getTarget();
    boolean piProcessed = false;

    if (!format.getIgnoreTrAXEscapingPIs()) {
      if (target.equals(Result.PI_DISABLE_OUTPUT_ESCAPING)) {
        piProcessed = true;
      }
      else if (target.equals(Result.PI_ENABLE_OUTPUT_ESCAPING)) {
        piProcessed = true;
      }
    }
    if (!piProcessed) {
      String rawData = pi.getData();

      // Write <?target data?> or if no data then just <?target?>
      if (rawData != null && !rawData.isEmpty()) {
        out.write("<?");
        out.write(target);
        out.write(" ");
        out.write(rawData);
        out.write("?>");
      }
      else {
        out.write("<?");
        out.write(target);
        out.write("?>");
      }
    }
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
  private static void printEntityRef(Writer out, EntityRef entity)
    throws IOException {
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
    String str;
    if (format.getTextMode() == Format.TextMode.NORMALIZE) {
      str = cdata.getTextNormalize();
    }
    else {
      str = format.getTextMode() == Format.TextMode.TRIM ? cdata.getText().trim() : cdata.getText();
    }
    out.write("<![CDATA[");
    out.write(str);
    out.write("]]>");
  }

  /**
   * This will handle printing a string.  Escapes the element entities,
   * trims interior whitespace, etc. if necessary.
   */
  private void printString(Writer out, String str) throws IOException {
    if (format.getTextMode() == Format.TextMode.NORMALIZE) {
      str = Text.normalizeString(str);
    }
    else if (format.getTextMode() == Format.TextMode.TRIM) {
      str = str.trim();
    }

    if (macroMap != null) {
      str = macroMap.substitute(str, SystemInfoRt.isFileSystemCaseSensitive);
    }

    out.write(escapeElementEntities(str));
  }

  /**
   * This will handle printing of a <code>{@link Element}</code>,
   * its <code>{@link Attribute}</code>s, and all contained (child)
   * elements, etc.
   *
   * @param element <code>Element</code> to output.
   * @param out     <code>Writer</code> to use.
   * @param level   <code>int</code> level of indention.
   */
  public void printElement(Writer out, Element element, int level) throws IOException {
    if (elementFilter != null && !elementFilter.accept(element, level)) {
      return;
    }

    // Print the beginning of the tag plus attributes and any
    // necessary namespace declarations
    out.write('<');
    printQualifiedName(out, element);

    if (element.hasAttributes()) {
      printAttributes(out, element.getAttributes());
    }

    // Depending on the settings (newlines, textNormalize, etc), we may
    // or may not want to print all of the content, so determine the
    // index of the start of the content we're interested in based on the current settings.
    List<Content> content = element.getContent();
    int start = skipLeadingWhite(content, 0);
    int size = content.size();
    if (start >= size) {
      // Case content is empty or all insignificant whitespace
      if (format.getExpandEmptyElements()) {
        out.write("></");
        printQualifiedName(out, element);
        out.write('>');
      }
      else {
        out.write(" />");
      }
    }
    else {
      out.write('>');

      // for a special case where the content is only CDATA or Text we don't want to indent after the start or before the end tag
      if (nextNonText(content, start) < size) {
        // case Mixed Content - normal indentation
        newline(out);
        printContentRange(out, content, start, size, level + 1);
        newline(out);
        indent(out, level);
      }
      else {
        // case all CDATA or Text - no indentation
        printTextRange(out, content, start, size);
      }
      out.write("</");
      printQualifiedName(out, element);
      out.write('>');
    }
  }

  /**
   * This will handle printing of content within a given range.
   * The range to print is specified in typical Java fashion; the
   * starting index is inclusive, while the ending index is
   * exclusive.
   *
   * @param content <code>List</code> of content to output
   * @param start   index of first content node (inclusive.
   * @param end     index of last content node (exclusive).
   * @param out     <code>Writer</code> to use.
   * @param level   <code>int</code> level of indentation.
   */
  private void printContentRange(Writer out, List<Content> content, int start, int end, int level) throws IOException {
    boolean firstNode; // Flag for 1st node in content
    Object next;       // Node we're about to print
    int first, index;  // Indexes into the list of content

    index = start;
    while (index < end) {
      firstNode = index == start;
      next = content.get(index);

      // Handle consecutive CDATA, Text, and EntityRef nodes all at once
      if (next instanceof Text || next instanceof EntityRef) {
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

      // Handle other nodes
      if (!firstNode) {
        newline(out);
      }

      indent(out, level);

      if (next instanceof Comment) {
        printComment(out, (Comment)next);
      }
      else if (next instanceof Element) {
        printElement(out, (Element)next, level);
      }
      else if (next instanceof ProcessingInstruction) {
        printProcessingInstruction(out, (ProcessingInstruction)next);
      }
      else {
        // XXX if we get here then we have a illegal content, for now we'll just ignore it (probably should throw a exception)
      }

      index++;
    }
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
  private void printTextRange(Writer out, List<Content> content, int start, int end) throws IOException {
    String previous; // Previous text printed
    Object node;     // Next node to print
    String next;     // Next text to print

    previous = null;

    // remove leading whitespace-only nodes
    start = skipLeadingWhite(content, start);

    int size = content.size();
    if (start < size) {
      // and remove trialing whitespace-only nodes
      end = skipTrailingWhite(content, end);

      for (int i = start; i < end; i++) {
        node = content.get(i);

        // get the unmangled version of the text we are about to print
        if (node instanceof Text) {
          next = ((Text)node).getText();
        }
        else if (node instanceof EntityRef) {
          next = "&" + ((EntityRef)node).getValue() + ";";
        }
        else {
          throw new IllegalStateException("Should see only CDATA, Text, or EntityRef");
        }

        // this may save a little time
        if (next == null || next.isEmpty()) {
          continue;
        }

        // determine if we need to pad the output (padding is only need in trim or normalizing mode)
        if (previous != null) { // Not 1st node
          if (format.getTextMode() == Format.TextMode.NORMALIZE || format.getTextMode() == Format.TextMode.TRIM) {
            if (endsWithWhite(previous) || startsWithWhite(next)) {
              out.write(' ');
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
   * This will handle printing of a <code>{@link Attribute}</code> list.
   *
   * @param attributes <code>List</code> of Attribute objects
   * @param out        <code>Writer</code> to use
   */
  private void printAttributes(Writer out, List<Attribute> attributes) throws IOException {
    for (Attribute attribute : attributes) {
      out.write(' ');
      printQualifiedName(out, attribute);
      out.write('=');
      out.write('"');

      String value;
      if (macroMap != null && (macroFilter == null || !macroFilter.skipPathMacros(attribute))) {
        value = macroMap.getAttributeValue(attribute, macroFilter, SystemInfoRt.isFileSystemCaseSensitive, false);
      }
      else {
        value = attribute.getValue();
      }

      out.write(escapeAttributeEntities(value));
      out.write('"');
    }
  }

  /**
   * This will print a newline only if indent is not null.
   *
   * @param out <code>Writer</code> to use
   */
  private void newline(Writer out) throws IOException {
    if (format.getIndent() != null) {
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
    if (StringUtil.isEmpty(format.getIndent())) {
      return;
    }

    for (int i = 0; i < level; i++) {
      out.write(format.getIndent());
    }
  }

  // Returns the index of the first non-all-whitespace CDATA or Text,
  // index = content.size() is returned if content contains
  // all whitespace.
  // @param start index to begin search (inclusive)
  private int skipLeadingWhite(List<Content> content, int start) {
    if (start < 0) {
      start = 0;
    }

    int index = start;
    int size = content.size();
    if (format.getTextMode() == Format.TextMode.TRIM_FULL_WHITE
        || format.getTextMode() == Format.TextMode.NORMALIZE
        || format.getTextMode() == Format.TextMode.TRIM) {
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
  private int skipTrailingWhite(List<Content> content, int start) {
    int size = content.size();
    if (start > size) {
      start = size;
    }

    int index = start;
    if (format.getTextMode() == Format.TextMode.TRIM_FULL_WHITE
        || format.getTextMode() == Format.TextMode.NORMALIZE
        || format.getTextMode() == Format.TextMode.TRIM) {
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
  // non-Text, or non-EntryRef nodes
  // @param start index to begin search (inclusive)
  private static int nextNonText(List<Content> content, int start) {
    if (start < 0) {
      start = 0;
    }

    int index = start;
    int size = content.size();
    while (index < size) {
      Object node = content.get(index);
      if (!(node instanceof Text || node instanceof EntityRef)) {
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
    return !StringUtil.isEmpty(str) && Verifier.isXMLWhitespace(str.charAt(0));
  }

  // Determine if a string ends with a XML whitespace.
  private static boolean endsWithWhite(String str) {
    return !StringUtil.isEmpty(str) && Verifier.isXMLWhitespace(str.charAt(str.length() - 1));
  }

  private static String escapeAttributeEntities(String str) {
    return JDOMUtil.escapeText(str, false, true);
  }

  private static String escapeElementEntities(String str) {
    return JDOMUtil.escapeText(str, false, false);
  }

  // Support method to print a name without using elt.getQualifiedName()
  // and thus avoiding a StringBuffer creation and memory churn
  private static void printQualifiedName(Writer out, Element e) throws IOException {
    if (!e.getNamespace().getPrefix().isEmpty()) {
      out.write(e.getNamespace().getPrefix());
      out.write(':');
    }
    out.write(e.getName());
  }

  // Support method to print a name without using att.getQualifiedName()
  // and thus avoiding a StringBuffer creation and memory churn
  private static void printQualifiedName(Writer out, Attribute a) throws IOException {
    String prefix = a.getNamespace().getPrefix();
    if (!StringUtil.isEmpty(prefix)) {
      out.write(prefix);
      out.write(':');
    }
    out.write(a.getName());
  }
}
