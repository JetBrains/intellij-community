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

package org.jdom.output.support;

import org.jdom.*;
import org.jdom.output.Format;
import org.jdom.output.Format.TextMode;
import org.jdom.util.NamespaceStack;

import javax.xml.transform.Result;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <h2>Overview</h2>
 * <p>
 * This class is marked abstract even though all methods are fully implemented.
 * The <code>process*(...)</code> methods are public because they match the
 * XMLOutputProcessor interface but the remaining methods are all protected.
 * <p>
 * People who want to create a custom XMLOutputProcessor for XMLOutputter are
 * able to extend this class and modify any functionality they want. Before
 * sub-classing this you should first check to see if the {@link Format} class
 * can get you the results you want.
 * <p>
 * <b><i>Subclasses of this should have reentrant methods.</i></b> This is
 * easiest to accomplish simply by not allowing any instance fields. If your
 * sub-class has an instance field/variable, then it's probably broken.
 * <p>
 * <h2>The Stacks</h2>
 * <p>
 * One significant feature of this implementation is that it creates and
 * maintains both a {@link NamespaceStack} and {@link FormatStack} that are
 * managed in the
 * {@link #printElement(Writer, FormatStack, NamespaceStack, Element)} method.
 * The stacks are pushed and popped in that method only. They significantly
 * improve the performance and readability of the code.
 * <p>
 * The NamespaceStack is only sent through to the
 * {@link #printElement(Writer, FormatStack, NamespaceStack, Element)} and
 * {@link #printContent(Writer, FormatStack, NamespaceStack, Walker)} methods,
 * but the FormatStack is pushed through to all print* Methods.
 * <p>
 * <h2>Text Processing</h2>
 * <p>
 * In XML the concept of 'Text' can be loosely defined as anything that can be
 * found between an Element's start and end tags, excluding Comments and
 * Processing Instructions. When considered from a JDOM perspective, this means
 * {@link Text}, {@link CDATA} and {@link EntityRef} content. This will be
 * referred to as 'Text-like content'
 * <p>
 * XMLOutputter delegates the management and formatting of Content to a
 * Walker instance. See {@link Walker} and its various implementations for
 * details on how the Element content is processed.
 * <p>
 * Because the Walker interface specifies that Text/CDATA content may be
 * returned as either Text/CDATA instances or as formatted String values
 * this class sometimes uses printCDATA(...) and printText(...), and sometimes
 * uses the more direct {@link #textCDATA(Writer, String)} or
 * {@link #textRaw(Writer, String)} as
 * appropriate. In other words, subclasses should probably override these second
 * methods instead of the print methods.
 * <p>
 * <h2>Non-Text Content</h2>
 * <p>
 * Non-text content is processed via the respective print* methods. The usage
 * should be logical based on the method name.
 * <p>
 * The general observations are:
 * <ul>
 * <li>printElement - maintains the Stacks, prints the element open tags, with
 * attributes and namespaces. It checks to see whether the Element is text-only,
 * or has non-text content. If it is text-only there is no indent/newline
 * handling and it delegates to the correct text-type print method, otherwise it
 * delegates to printContent.
 * <li>printContent is called to output all lists of Content. It assumes that
 * all whitespace indentation/newlines are appropriate before it is called, but
 * it will ensure that padding is appropriate between the items in the list.
 * </ul>
 * <p>
 * <h2>Final Notes</h2> No methods actually write to the destination Writer
 * except the <code>write(...)</code> methods. Thus, all other methods do their
 * respective processing and delegate the actual destination output to the
 * {@link #write(Writer, char)} or {@link #write(Writer, String)} methods.
 * <p>
 * All Text-like content (printCDATA, printText, and printEntityRef) will
 * ultimately be output through the the text* methods (and no other content).
 * <p>
 *
 * @author Rolf Lear
 * @see XMLOutputter2
 * @see XMLOutputProcessor
 * @since JDOM2
 */
public final class XmlOutputProcessorImpl extends AbstractOutputProcessor implements XMLOutputProcessor {
  /**
   * Simple constant for an open-CDATA
   */
  private static final String CDATAPRE = "<![CDATA[";
  /**
   * Simple constant for a close-CDATA
   */
  private static final String CDATAPOST = "]]>";

  @Override
  public void process(final Writer out, final Format format,
                      final Document doc) throws IOException {
    printDocument(out, new FormatStack(format), new NamespaceStack(), doc);
    out.flush();
  }

  /*
   * (non-Javadoc)
   *
   * @see org.jdom.output.XMLOutputProcessor#process(java.io.Writer,
   * org.jdom.DocType, org.jdom.output.Format)
   */
  @Override
  public void process(final Writer out, final Format format,
                      final DocType doctype) throws IOException {
    printDocType(out, new FormatStack(format), doctype);
    out.flush();
  }

  /*
   * (non-Javadoc)
   *
   * @see org.jdom.output.XMLOutputProcessor#process(java.io.Writer,
   * org.jdom.Element, org.jdom.output.Format)
   */
  @Override
  public void process(final Writer out, final Format format,
                      final Element element) throws IOException {
    // If this is the root element we could pre-initialize the
    // namespace stack with the namespaces
    printElement(out, new FormatStack(format), new NamespaceStack(),
                 element);
    out.flush();
  }

  /*
   * (non-Javadoc)
   *
   * @see org.jdom.output.XMLOutputProcessor#process(java.io.Writer,
   * java.util.List, org.jdom.output.Format)
   */
  @Override
  public void process(final Writer out, final Format format,
                      final List<? extends Content> list)
    throws IOException {
    FormatStack fstack = new FormatStack(format);
    Walker walker = buildWalker(fstack, list, true);
    printContent(out, fstack, new NamespaceStack(), walker);
    out.flush();
  }

  /*
   * (non-Javadoc)
   *
   * @see org.jdom.output.XMLOutputProcessor#process(java.io.Writer,
   * org.jdom.CDATA, org.jdom.output.Format)
   */
  @Override
  public void process(final Writer out, final Format format,
                      final CDATA cdata) throws IOException {
    // we use the powers of the Walker to manage text-like content.
    final List<CDATA> list = Collections.singletonList(cdata);
    FormatStack fstack = new FormatStack(format);
    final Walker walker = buildWalker(fstack, list, true);
    if (walker.hasNext()) {
      printContent(out, fstack, new NamespaceStack(), walker);
    }
    out.flush();
  }

  /*
   * (non-Javadoc)
   *
   * @see org.jdom.output.XMLOutputProcessor#process(java.io.Writer,
   * org.jdom.Text, org.jdom.output.Format)
   */
  @Override
  public void process(final Writer out, final Format format,
                      final Text text) throws IOException {
    // we use the powers of the Walker to manage text-like content.
    final List<Text> list = Collections.singletonList(text);
    FormatStack fstack = new FormatStack(format);
    final Walker walker = buildWalker(fstack, list, true);
    if (walker.hasNext()) {
      printContent(out, fstack, new NamespaceStack(), walker);
    }
    out.flush();
  }

  /*
   * (non-Javadoc)
   *
   * @see org.jdom.output.XMLOutputProcessor#process(java.io.Writer,
   * org.jdom.Comment, org.jdom.output.Format)
   */
  @Override
  public void process(final Writer out, final Format format,
                      final Comment comment) throws IOException {
    printComment(out, comment);
    out.flush();
  }

  /*
   * (non-Javadoc)
   *
   * @see org.jdom.output.XMLOutputProcessor#process(java.io.Writer,
   * org.jdom.ProcessingInstruction, org.jdom.output.Format)
   */
  @Override
  public void process(Writer out, Format format, ProcessingInstruction pi) throws IOException {
    FormatStack stack = new FormatStack(format);
    // Output PI verbatim, disregarding TrAX escaping PIs.
    stack.setIgnoreTrAXEscapingPIs(true);
    printProcessingInstruction(out, stack, pi);
    out.flush();
  }

  /*
   * (non-Javadoc)
   *
   * @see org.jdom.output.XMLOutputProcessor#process(java.io.Writer,
   * org.jdom.EntityRef, org.jdom.output.Format)
   */
  @Override
  public void process(final Writer out, final Format format,
                      final EntityRef entity) throws IOException {
    printEntityRef(out, entity);
    out.flush();
  }

  /*
   * ========================================================================
   * Methods that actually write data to output. None of the other methods
   * should directly write to the output unless they use these methods.
   * ========================================================================
   */

  /**
   * Print some string value to the output. Null values are ignored. This
   * ignore-null property is used for a few tricks.
   *
   * @param out The Writer to write to.
   * @param str The String to write (can be null).
   * @throws IOException if the out Writer fails.
   */
  private void write(final Writer out, final String str) throws IOException {
    if (str == null) {
      return;
    }
    out.write(str);
  }

  /**
   * Write a single character to the output Writer.
   *
   * @param out The Writer to write to.
   * @param c   The char to write.
   * @throws IOException if the Writer fails.
   */
  private void write(final Writer out, final char c) throws IOException {
    out.write(c);
  }

  /*
   * ========================================================================
   * Support methods for Text-content formatting. Should all be protected. The
   * following are used when printing Text-based data. Because of complicated
   * multi-sequential text sometimes the requirements are odd. All Text
   * content will be output using these methods, which is why there is the None
   * version.
   * ========================================================================
   */

  /**
   * This will take the three pre-defined entities in XML 1.0 ('&lt;', '&gt;',
   * and '&amp;' - used specifically in XML elements) as well as CR/NL and
   * Quote characters which require escaping inside Attribute values and
   * convert their character representation to the appropriate entity
   * reference suitable for XML attribute content. Further, some special
   * characters (e.g. characters that are not valid in the current encoding)
   * are converted to escaped representations.
   * <p>
   * <b>Note:</b> If {@link FormatStack#getEscapeOutput()} is false then no
   * escaping will happen.
   *
   * @param out    The destination Writer
   * @param fstack The {@link FormatStack}
   * @param value  <code>String</code> Attribute value to escape.
   * @throws IOException          if the destination Writer fails.
   * @throws IllegalDataException if an entity can not be escaped
   */
  private void attributeEscapedEntitiesFilter(final Writer out, final FormatStack fstack, final String value) throws IOException {

    if (!fstack.getEscapeOutput()) {
      // no escaping...
      write(out, value);
      return;
    }

    write(out, Format.escapeAttribute(fstack.getEscapeStrategy(), value));
  }

  /**
   * Convenience method that simply passes the input str to
   * {@link #write(Writer, String)}. This could be useful for subclasses to
   * hook in to. All text-type output will come through this or the
   * {@link #textRaw(Writer, char)} method.
   *
   * @param out the destination writer.
   * @param str the String to write.
   * @throws IOException if the Writer fails.
   */
  private void textRaw(final Writer out, final String str) throws IOException {
    write(out, str);
  }

  /**
   * Convenience method that simply passes the input char to
   * {@link #write(Writer, char)}. This could be useful for subclasses to hook
   * in to. All text-type output will come through this or the
   * {@link #textRaw(Writer, String)} method.
   *
   * @param out the destination Writer.
   * @param ch  the char to write.
   * @throws IOException if the Writer fails.
   */
  private void textRaw(final Writer out, final char ch) throws IOException {
    write(out, ch);
  }

  /**
   * Write an {@link EntityRef} to the destination.
   *
   * @param out  the destination Writer.
   * @param name the EntityRef's name.
   * @throws IOException if the Writer fails.
   */
  private void textEntityRef(final Writer out, final String name) throws IOException {
    textRaw(out, '&');
    textRaw(out, name);
    textRaw(out, ';');
  }

  /**
   * Write a {@link CDATA} to the destination
   *
   * @param out  the destination Writer
   * @param text the CDATA text
   * @throws IOException if the Writer fails.
   */
  private void textCDATA(final Writer out, final String text) throws IOException {
    textRaw(out, CDATAPRE);
    textRaw(out, text);
    textRaw(out, CDATAPOST);
  }

  /* *******************************************
   * Support methods for output. Should all be protected. All content-type
   * print methods have a FormatStack. Only printContent is responsible for
   * outputting appropriate indenting and newlines, which are easily available
   * using the FormatStack.getLevelIndent() and FormatStack.getLevelEOL().
   * *******************************************
   */

  /**
   * This will handle printing of a {@link Document}.
   *
   * @param out    <code>Writer</code> to use.
   * @param fstack the FormatStack
   * @param nstack the NamespaceStack
   * @param doc    <code>Document</code> to write.
   * @throws IOException if the destination Writer fails
   */
  private void printDocument(final Writer out, final FormatStack fstack,
                             final NamespaceStack nstack, final Document doc) throws IOException {


    // If there is no root element then we cannot use the normal ways to
    // access the ContentList because Document throws an exception.
    // so we hack it and just access it by index.
    List<Content> list = doc.hasRootElement() ? doc.getContent() :
                         new ArrayList<>(doc.getContentSize());
    if (list.isEmpty()) {
      final int sz = doc.getContentSize();
      for (int i = 0; i < sz; i++) {
        list.add(doc.getContent(i));
      }
    }

    printDeclaration(out, fstack);

    Walker walker = buildWalker(fstack, list, true);
    if (walker.hasNext()) {
      while (walker.hasNext()) {

        final Content c = walker.next();
        // we do not ignore Text-like things in the Document.
        // the walker creates the indenting for us.
        if (c == null) {
          // but, what we do is ensure it is all whitespace, and not CDATA
          final String padding = walker.text();
          if (padding != null && Verifier.isAllXMLWhitespace(padding) &&
              !walker.isCDATA()) {
            // we do not use the escaping or text* method because this
            // content is outside of the root element, and thus is not
            // strict text.
            write(out, padding);
          }
        }
        else {
          switch (c.getCType()) {
            case Comment:
              printComment(out, (Comment)c);
              break;
            case DocType:
              printDocType(out, fstack, (DocType)c);
              break;
            case Element:
              printElement(out, fstack, nstack, (Element)c);
              break;
            case ProcessingInstruction:
              printProcessingInstruction(out, fstack,
                                         (ProcessingInstruction)c);
              break;
            case Text:
              final String padding = ((Text)c).getText();
              if (padding != null && Verifier.isAllXMLWhitespace(padding)) {
                // we do not use the escaping or text* method because this
                // content is outside of the root element, and thus is not
                // strict text.
                write(out, padding);
              }
            default:
              // do nothing.
          }
        }
      }

      if (fstack.getLineSeparator() != null) {
        write(out, fstack.getLineSeparator());
      }
    }
  }

  /**
   * This will handle printing of the XML declaration. Assumes XML version 1.0
   * since we don't directly know.
   *
   * @param out    <code>Writer</code> to use.
   * @param fstack the FormatStack
   * @throws IOException if the destination Writer fails
   */
  private void printDeclaration(final Writer out, final FormatStack fstack) throws IOException {

    // Only print the declaration if it's not being omitted
    if (fstack.isOmitDeclaration()) {
      return;
    }
    // Declaration is never indented.
    // write(out, fstack.getLevelIndent());

    // Assume 1.0 version
    if (fstack.isOmitEncoding()) {
      write(out, "<?xml version=\"1.0\"?>");
    }
    else {
      write(out, "<?xml version=\"1.0\"");
      write(out, " encoding=\"");
      write(out, fstack.getEncoding());
      write(out, "\"?>");
    }

    // Print new line after decl always, even if no other new lines
    // Helps the output look better and is semantically
    // inconsequential
    // newline(out, fstack);
    write(out, fstack.getLineSeparator());
  }

  /**
   * This will handle printing of a {@link DocType}.
   *
   * @param out     <code>Writer</code> to use.
   * @param fstack  the FormatStack
   * @param docType <code>DocType</code> to write.
   * @throws IOException if the destination Writer fails
   */
  private void printDocType(final Writer out, final FormatStack fstack,
                            final DocType docType) throws IOException {

    final String publicID = docType.getPublicID();
    final String systemID = docType.getSystemID();
    final String internalSubset = docType.getInternalSubset();
    boolean hasPublic = false;

    // Declaration is never indented.
    // write(out, fstack.getLevelIndent());

    write(out, "<!DOCTYPE ");
    write(out, docType.getElementName());
    if (publicID != null) {
      write(out, " PUBLIC \"");
      write(out, publicID);
      write(out, "\"");
      hasPublic = true;
    }
    if (systemID != null) {
      if (!hasPublic) {
        write(out, " SYSTEM");
      }
      write(out, " \"");
      write(out, systemID);
      write(out, "\"");
    }
    if ((internalSubset != null) && (!internalSubset.isEmpty())) {
      write(out, " [");
      write(out, fstack.getLineSeparator());
      write(out, docType.getInternalSubset());
      write(out, "]");
    }
    write(out, ">");
  }

  /**
   * This will handle printing of a {@link ProcessingInstruction}.
   *
   * @param out    <code>Writer</code> to use.
   * @param fstack the FormatStack
   * @param pi     <code>ProcessingInstruction</code> to write.
   * @throws IOException if the destination Writer fails
   */
  private void printProcessingInstruction(final Writer out,
                                          final FormatStack fstack, final ProcessingInstruction pi)
    throws IOException {
    String target = pi.getTarget();
    boolean piProcessed = false;

    if (!fstack.isIgnoreTrAXEscapingPIs()) {
      if (target.equals(Result.PI_DISABLE_OUTPUT_ESCAPING)) {
        // special case... change the FormatStack
        fstack.setEscapeOutput(false);
        piProcessed = true;
      }
      else if (target.equals(Result.PI_ENABLE_OUTPUT_ESCAPING)) {
        // special case... change the FormatStack
        fstack.setEscapeOutput(true);
        piProcessed = true;
      }
    }
    if (!piProcessed) {
      String rawData = pi.getData();

      // Write <?target data?> or if no data then just <?target?>
      if (!"".equals(rawData)) {
        write(out, "<?");
        write(out, target);
        write(out, " ");
        write(out, rawData);
        write(out, "?>");
      }
      else {
        write(out, "<?");
        write(out, target);
        write(out, "?>");
      }
    }
  }

  /**
   * This will handle printing of a {@link Comment}.
   *
   * @param out     <code>Writer</code> to use.
   * @param comment <code>Comment</code> to write.
   * @throws IOException if the destination Writer fails
   */
  private void printComment(final Writer out, final Comment comment) throws IOException {
    write(out, "<!--");
    write(out, comment.getText());
    write(out, "-->");
  }

  /**
   * This will handle printing of an {@link EntityRef}.
   *
   * @param out    <code>Writer</code> to use.
   * @param entity <code>EntotyRef</code> to write.
   * @throws IOException if the destination Writer fails
   */
  private void printEntityRef(final Writer out, final EntityRef entity) throws IOException {
    // EntityRefs are treated like text, not indented/newline content.
    textEntityRef(out, entity.getName());
  }

  /**
   * This will handle printing of a {@link CDATA}.
   *
   * @param out    <code>Writer</code> to use.
   * @param cdata  <code>CDATA</code> to write.
   * @throws IOException if the destination Writer fails
   */
  private void printCDATA(final Writer out, final CDATA cdata) throws IOException {
    // CDATAs are treated like text, not indented/newline content.
    textCDATA(out, cdata.getText());
  }

  /**
   * This will handle printing of a {@link Text}.
   *
   * @param out    <code>Writer</code> to use.
   * @param fstack the FormatStack
   * @param text   <code>Text</code> to write.
   * @throws IOException if the destination Writer fails
   */
  private void printText(final Writer out, final FormatStack fstack,
                         final Text text) throws IOException {
    if (fstack.getEscapeOutput()) {
      textRaw(out, Format.escapeText(fstack.getEscapeStrategy(),
                                     fstack.getLineSeparator(), text.getText()));

      return;
    }
    textRaw(out, text.getText());
  }

  /**
   * This will handle printing of an {@link Element}.
   * <p>
   * This method arranges for outputting the Element infrastructure including
   * Namespace Declarations and Attributes.
   *
   * @param out     <code>Writer</code> to use.
   * @param fstack  the FormatStack
   * @param nstack  the NamespaceStack
   * @param element <code>Element</code> to write.
   * @throws IOException if the destination Writer fails
   */
  private void printElement(final Writer out, final FormatStack fstack,
                            final NamespaceStack nstack, final Element element) throws IOException {

    nstack.push(element);
    try {
      final List<Content> content = element.getContent();

      // Print the beginning of the tag plus attributes and any
      // necessary namespace declarations
      write(out, "<");

      write(out, element.getQualifiedName());

      // Print the element's namespace, if appropriate
      for (final Namespace ns : nstack.addedForward()) {
        printNamespace(out, fstack, ns);
      }

      // Print out attributes
      if (element.hasAttributes()) {
        for (final Attribute attribute : element.getAttributes()) {
          printAttribute(out, fstack, attribute);
        }
      }

      if (content.isEmpty()) {
        // Case content is empty
        if (fstack.isExpandEmptyElements()) {
          write(out, "></");
          write(out, element.getQualifiedName());
          write(out, ">");
        }
        else {
          write(out, " />");
        }
        // nothing more to do.
        return;
      }

      // OK, we have real content to push.
      fstack.push();
      try {

        // Check for xml:space and adjust format settings
        final String space = element.getAttributeValue("space",
                                                       Namespace.XML_NAMESPACE);

        if ("default".equals(space)) {
          fstack.setTextMode(fstack.getDefaultMode());
        }
        else if ("preserve".equals(space)) {
          fstack.setTextMode(TextMode.PRESERVE);
        }

        // note we ensure the FStack is right before creating the walker
        Walker walker = buildWalker(fstack, content, true);

        if (!walker.hasNext()) {
          // the walker has formatted out whatever content we had
          if (fstack.isExpandEmptyElements()) {
            write(out, "></");
            write(out, element.getQualifiedName());
            write(out, ">");
          }
          else {
            write(out, " />");
          }
          // nothing more to do.
          return;
        }
        // we have some content.
        write(out, ">");
        if (!walker.isAllText()) {
          // we need to newline/indent
          textRaw(out, fstack.getPadBetween());
        }

        printContent(out, fstack, nstack, walker);

        if (!walker.isAllText()) {
          // we need to newline/indent
          textRaw(out, fstack.getPadLast());
        }
        write(out, "</");
        write(out, element.getQualifiedName());
        write(out, ">");
      }
      finally {
        fstack.pop();
      }
    }
    finally {
      nstack.pop();
    }
  }

  /**
   * This will handle printing of a List of {@link Content}.
   * <p>
   * The list of Content is basically processed as one of three types of
   * content
   * <ol>
   * <li>Consecutive text-type (Text, CDATA, and EntityRef) content
   * <li>Stand-alone text-type content
   * <li>Non-text-type content.
   * </ol>
   * Although the code looks complex, the theory is conceptually simple:
   * <ol>
   * <li>identify one of the three types (consecutive, stand-alone, non-text)
   * <li>do indent if any is specified.
   * <li>send the type to the respective print* handler (e.g.
   * {@link #printCDATA(Writer, CDATA)}, or
   * {@link #printComment(Writer, Comment)},
   * <li>do a newline if one is specified.
   * <li>loop back to 1. until there's no more content to process.
   * </ol>
   *
   * @param out    <code>Writer</code> to use.
   * @param fstack the FormatStack
   * @param nstack the NamespaceStack
   * @param walker {@link Walker} of <code>Content</code> to write.
   * @throws IOException if the destination Writer fails
   */
  private void printContent(final Writer out,
                            final FormatStack fstack, final NamespaceStack nstack,
                            final Walker walker)
    throws IOException {

    while (walker.hasNext()) {
      Content c = walker.next();
      if (c == null) {
        // it is a text value of some sort.
        final String t = walker.text();
        if (walker.isCDATA()) {
          textCDATA(out, t);
        }
        else {
          textRaw(out, t);
        }
      }
      else {
        switch (c.getCType()) {
          case CDATA:
            printCDATA(out, (CDATA)c);
            break;
          case Comment:
            printComment(out, (Comment)c);
            break;
          case DocType:
            printDocType(out, fstack, (DocType)c);
            break;
          case Element:
            printElement(out, fstack, nstack, (Element)c);
            break;
          case EntityRef:
            printEntityRef(out, (EntityRef)c);
            break;
          case ProcessingInstruction:
            printProcessingInstruction(out, fstack,
                                       (ProcessingInstruction)c);
            break;
          case Text:
            printText(out, fstack, (Text)c);
            break;
        }
      }
    }
  }

  /**
   * This will handle printing of any needed <code>{@link Namespace}</code>
   * declarations.
   *
   * @param out    <code>Writer</code> to use.
   * @param fstack The current FormatStack
   * @param ns     <code>Namespace</code> to print definition of
   * @throws IOException if the output fails
   */
  private void printNamespace(final Writer out, final FormatStack fstack,
                              final Namespace ns) throws IOException {
    final String prefix = ns.getPrefix();
    final String uri = ns.getURI();

    write(out, " xmlns");
    if (!prefix.isEmpty()) {
      write(out, ":");
      write(out, prefix);
    }
    write(out, "=\"");
    attributeEscapedEntitiesFilter(out, fstack, uri);
    write(out, "\"");
  }

  /**
   * This will handle printing of an <code>{@link Attribute}</code>.
   *
   * @param out       <code>Writer</code> to use.
   * @param fstack    The current FormatStack
   * @param attribute <code>Attribute</code> to output
   * @throws IOException if the output fails
   */
  private void printAttribute(final Writer out, final FormatStack fstack,
                              final Attribute attribute) throws IOException {

    write(out, " ");
    write(out, attribute.getQualifiedName());
    write(out, "=");

    write(out, "\"");
    attributeEscapedEntitiesFilter(out, fstack, attribute.getValue());
    write(out, "\"");
  }
}
