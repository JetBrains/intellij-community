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

import org.jdom.output.Format.TextMode;

import java.util.Arrays;

/**
 * FormatStack implements a mechanism where the formatting details can be
 * changed mid-tree, but then get reverted when that tree segment is
 * complete.
 * <p>
 * This class is intended as a working-class for in the various outputter
 * implementations. It is only public so that people extending the
 * Abstract*Processor classes can take advantage of its functionality.
 * <p>
 * The value this class adds is:
 * <ul>
 * <li>Fast -
 * </ul>
 *
 * @author Rolf Lear
 * @since JDOM2
 */
final class FormatStack {
  private int capacity = 16; // can grow if more than 16 levels in XML
  private int depth = 0; // current level in XML

  /*
   * ====================================================================
   * The following values cannot be changed midway through the output
   * ====================================================================
   */

  private final TextMode defaultMode; // the base/initial Text mode

  /**
   * The default indent is no spaces (as original document)
   */
  private final String indent;

  /**
   * The encoding format
   */
  private final String encoding;

  /**
   * New line separator
   */
  private final String lineSeparator;

  /**
   * Whether to output the XML declaration - default is
   * <code>false</code>
   */
  private final boolean omitDeclaration;

  /**
   * Whether to output the encoding in the XML declaration -
   * default is <code>false</code>
   */
  private final boolean omitEncoding;

  /**
   * Whether or not to expand empty elements to
   * &lt;tagName&gt;&lt;/tagName&gt; - default is <code>false</code>
   */
  private final boolean expandEmptyElements;

  /**
   * entity escape logic
   */
  private final EscapeStrategy escapeStrategy;

  /*
   * ====================================================================
   * The following values can be changed mid-way through the output, hence
   * they are arrays.
   * ====================================================================
   */

  /**
   * The 'current' accumulated indent
   */
  private String[] levelIndent = new String[capacity];

  /**
   * The 'current' End-Of-Line
   */
  private String[] levelEOL = new String[capacity];

  /**
   * The padding to put between content items
   */
  private String[] levelEOLIndent = new String[capacity];

  /**
   * The padding to put after the last item (typically one less indent)
   */
  private String[] termEOLIndent = new String[capacity];

  /**
   * Whether TrAX output escaping disabling/enabling PIs are ignored or
   * processed - default is <code>false</code>
   */
  private boolean[] ignoreTrAXEscapingPIs = new boolean[capacity];

  /**
   * text handling mode
   */
  private TextMode[] mode = new TextMode[capacity];

  /**
   * escape Output logic - can be changed by
   */
  private boolean[] escapeOutput = new boolean[capacity];

  /**
   * Creates a new FormatStack seeded with the specified Format
   *
   * @param format the Format instance to seed the stack with.
   */
  FormatStack(Format format) {
    indent = format.getIndent();
    lineSeparator = format.getLineSeparator();

    encoding = format.getEncoding();
    omitDeclaration = format.getOmitDeclaration();
    omitEncoding = format.getOmitEncoding();
    expandEmptyElements = format.getExpandEmptyElements();
    escapeStrategy = format.getEscapeStrategy();
    defaultMode = format.getTextMode();

    mode[depth] = format.getTextMode();
    if (mode[depth] == TextMode.PRESERVE) {
      // undo any special indenting and end-of-line management:
      levelIndent[depth] = null;
      levelEOL[depth] = null;
      levelEOLIndent[depth] = null;
      termEOLIndent[depth] = null;
    }
    else {
      levelIndent[depth] = format.getIndent() == null
                           ? null : "";
      levelEOL[depth] = format.getLineSeparator();
      levelEOLIndent[depth] = levelIndent[depth] == null ?
                              null : levelEOL[depth];
      termEOLIndent[depth] = levelEOLIndent[depth];
    }
    ignoreTrAXEscapingPIs[depth] = format.getIgnoreTrAXEscapingPIs();
    escapeOutput[depth] = true;
  }

  /**
   * If the indent strategy changes part way through a stack, we need to
   * clear the previously calculated reusable 'lower' levels of the stack.
   */
  private void resetReusableIndents() {
    int d = depth + 1;
    while (d < levelIndent.length && levelIndent[d] != null) {
      // all subsequent forays in to lower levels will need to be redone
      levelIndent[d] = null;
      d++;
    }
  }

  /**
   * @return the original {@link Format#getIndent()}, may be null
   */
  public String getIndent() {
    return indent;
  }

  /**
   * @return the original {@link Format#getLineSeparator()}
   */
  public String getLineSeparator() {
    return lineSeparator;
  }

  /**
   * @return the original {@link Format#getEncoding()}
   */
  public String getEncoding() {
    return encoding;
  }

  /**
   * @return the original {@link Format#getOmitDeclaration()}
   */
  boolean isOmitDeclaration() {
    return omitDeclaration;
  }

  /**
   * @return the original {@link Format#getOmitEncoding()}
   */
  boolean isOmitEncoding() {
    return omitEncoding;
  }

  /**
   * @return the original {@link Format#getExpandEmptyElements()}
   */
  boolean isExpandEmptyElements() {
    return expandEmptyElements;
  }

  EscapeStrategy getEscapeStrategy() {
    return escapeStrategy;
  }

  /**
   * The escapeOutput flag can be set or unset. When set, Element text and
   * Attribute values are 'escaped' so that the output is valid XML. When
   * unset, the Element text and Attribute values are not escaped.
   *
   * @return the current depth's escapeOutput flag.
   */
  boolean getEscapeOutput() {
    return escapeOutput[depth];
  }

  /**
   * @return the TextMode that was originally set for this stack before
   * any modifications.
   */
  TextMode getDefaultMode() {
    return defaultMode;
  }

  /**
   * Get the end-of-line indenting sequence for before the first item in an
   * Element, as well as between subsequent items (but not after the last item)
   *
   * @return the String EOL sequence followed by an indent. Null if it should
   * be ignored
   */
  String getPadBetween() {
    return levelEOLIndent[depth];
  }

  /**
   * Get the end-of-line indenting sequence for after the last item in an
   * Element
   *
   * @return the String EOL sequence followed by an indent. Null if it should
   * be ignored
   */
  String getPadLast() {
    return termEOLIndent[depth];
  }

  /**
   * @return the current depth's End-Of-Line sequence, may be null
   */
  String getLevelEOL() {
    return levelEOL[depth];
  }

  /**
   * @return the current depth's {@link Format#getTextMode()}
   */
  TextMode getTextMode() {
    return mode[depth];
  }

  /**
   * Change the current level's TextMode
   *
   * @param mode the new mode to set.
   */
  void setTextMode(TextMode mode) {
    if (this.mode[depth] == mode) {
      return;
    }
    this.mode[depth] = mode;
    if (mode == TextMode.PRESERVE) {
      levelEOL[depth] = null;
      levelIndent[depth] = null;
      levelEOLIndent[depth] = null;
      termEOLIndent[depth] = null;
    }
    else {
      levelEOL[depth] = lineSeparator;
      if (indent == null || lineSeparator == null) {
        levelEOLIndent[depth] = null;
        termEOLIndent[depth] = null;
      }
      else {
        if (depth > 0) {
          final StringBuilder sb = new StringBuilder(indent.length() * depth);
          for (int i = 1; i < depth; i++) {
            sb.append(indent);
          }
          // the start point was '1', so we are one indent
          // short, which is just right for the term....
          termEOLIndent[depth] = lineSeparator + sb;
          // but we increase it once for the actual indent.
          sb.append(indent);
          levelIndent[depth] = sb.toString();
        }
        else {
          termEOLIndent[depth] = lineSeparator;
          levelIndent[depth] = "";
        }
        levelEOLIndent[depth] = lineSeparator + levelIndent[depth];
      }
    }
    resetReusableIndents();
  }

  /**
   * Create a new depth level on the stack. The previous level's details
   * are copied to this level, and the accumulated indent (if any) is
   * indented further.
   */
  public void push() {
    final int prev = depth++;
    if (depth >= capacity) {
      capacity *= 2;
      levelIndent = Arrays.copyOf(levelIndent, capacity);
      levelEOL = Arrays.copyOf(levelEOL, capacity);
      levelEOLIndent = Arrays.copyOf(levelEOLIndent, capacity);
      termEOLIndent = Arrays.copyOf(termEOLIndent, capacity);
      ignoreTrAXEscapingPIs = Arrays.copyOf(ignoreTrAXEscapingPIs, capacity);
      mode = Arrays.copyOf(mode, capacity);
      escapeOutput = Arrays.copyOf(escapeOutput, capacity);
    }

    ignoreTrAXEscapingPIs[depth] = ignoreTrAXEscapingPIs[prev];
    mode[depth] = mode[prev];
    escapeOutput[depth] = escapeOutput[prev];

    if (levelIndent[prev] == null || levelEOL[prev] == null) {
      levelIndent[depth] = null;
      levelEOL[depth] = null;
      levelEOLIndent[depth] = null;
      termEOLIndent[depth] = null;
    }
    else if (levelIndent[depth] == null) {
      // we need to build our level details ....
      // cannot reuse previous ones.
      levelEOL[depth] = levelEOL[prev];
      termEOLIndent[depth] = levelEOL[depth] + levelIndent[prev];
      levelIndent[depth] = levelIndent[prev] + indent;
      levelEOLIndent[depth] = levelEOL[depth] + levelIndent[depth];
    }
  }

  /**
   * Move back a level on the stack.
   */
  public void pop() {
    // no need to clear previously used members in the stack.
    // the stack is short-lived, and does not create new instances for
    // the depth levels, in other words, it does not affect GC and does
    // not save memory to clear the stack.
    depth--;
  }
}