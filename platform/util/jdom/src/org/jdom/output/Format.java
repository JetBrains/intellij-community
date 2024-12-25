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

import org.jdom.Verifier;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Locale;

/**
 * Class to encapsulate XMLOutputter format options.
 * Typically, users adapt the standard format configurations obtained by
 * {@link #getRawFormat} (no whitespace changes),
 * {@link #getPrettyFormat} (whitespace beautification), and
 * {@link #getCompactFormat} (whitespace normalization).
 * <p>
 * Several modes are available to effect the way textual content is printed.
 * See the documentation for {@link TextMode} for details.
 * <p>
 * <b>Note about Line Separator:</b>
 * <p>
 * <b>Note about XML Character Escaping:</b>
 * <p>
 * JDOM will escape characters in the output based on the EscapeStrategy that
 * is specified by this Format. The Format will by default use a sensible
 * EscapeStrategy that is based on the character encoding of the output. If
 * the default escape mechanism is not producing the correct results you can
 * change the EscapeStrategy on the format to suit your own needs.
 *
 * @author Jason Hunter
 * @author Rolf Lear
 */
public final class Format implements Cloneable {
  /**
   * An EscapeStrategy suitable for UTF-8 a UTF-16
   */
  private static final EscapeStrategy UTFEscapeStrategy = new EscapeStrategy() {
    @Override
    public boolean shouldEscape(char ch) {
      return Verifier.isHighSurrogate(ch);
    }
  };

  /**
   * An EscapeStrategy suitable for 8-bit charsets
   */
  private static final EscapeStrategy Bits8EscapeStrategy = new EscapeStrategy() {
    @Override
    public boolean shouldEscape(final char ch) {
      return (ch >>> 8) != 0;
    }
  };

  /**
   * An EscapeStrategy suitable for 7-bit charsets
   */
  private static final EscapeStrategy Bits7EscapeStrategy = new EscapeStrategy() {
      @Override
      public boolean shouldEscape(final char ch) {
        return (ch >>> 7) != 0;
      }
    };

  /**
   * An EscapeStrategy suitable for 'unknown' charsets
   */
  private static final EscapeStrategy DEFAULT_ESCAPE_STRATEGY = new EscapeStrategy() {
    @Override
    public boolean shouldEscape(char ch) {
      // Safer this way per http://unicode.org/faq/utf_bom.html#utf8-4
      return Verifier.isHighSurrogate(ch);
    }
  };

  /**
   * Handles Charsets.
   */
  private static final class DefaultCharsetEscapeStrategy implements EscapeStrategy {
    private final CharsetEncoder encoder;

    private DefaultCharsetEscapeStrategy(CharsetEncoder cse) {
      encoder = cse;
    }

    @Override
    public boolean shouldEscape(final char ch) {

      if (Verifier.isHighSurrogate(ch)) {
        return true;  // Safer this way per http://unicode.org/faq/utf_bom.html#utf8-4
      }

      return !encoder.canEncode(ch);
    }
  }

  /**
   * Returns a new Format object that performs no whitespace changes, uses
   * the UTF-8 encoding, doesn't expand empty elements, includes the
   * declaration and encoding, and uses the default entity escape strategy.
   * Tweaks can be made to the returned Format instance without affecting
   * other instances.
   *
   * @return a Format with no whitespace changes
   */
  public static Format getRawFormat() {
    return new Format();
  }

  /**
   * Returns a new Format object that performs whitespace beautification with
   * 2-space indents, uses the UTF-8 encoding, doesn't expand empty elements,
   * includes the declaration and encoding, and uses the default entity
   * escape strategy.
   * Tweaks can be made to the returned Format instance without affecting
   * other instances.
   *
   * @return a Format with whitespace beautification
   */
  public static Format getPrettyFormat() {
    Format f = new Format();
    f.setIndent(STANDARD_INDENT);
    f.setTextMode(TextMode.TRIM);
    return f;
  }

  /**
   * Returns a new Format object that performs whitespace normalization, uses
   * the UTF-8 encoding, doesn't expand empty elements, includes the
   * declaration and encoding, and uses the default entity escape strategy.
   * Tweaks can be made to the returned Format instance without affecting
   * other instances.
   *
   * @return a Format with whitespace normalization
   */
  public static Format getCompactFormat() {
    Format f = new Format();
    f.setTextMode(TextMode.NORMALIZE);
    return f;
  }

  /**
   * Use the XML Specification definition of whitespace to compact the
   * input value. The value is trimmed, and any internal XML whitespace
   * is replaced with a single ' ' space.
   *
   * @param str The value to compact.
   * @return The compacted value
   * @since JDOM2
   */
  public static String compact(String str) {
    int right = str.length() - 1;
    int left = 0;
    while (left <= right &&
           Verifier.isXMLWhitespace(str.charAt(left))) {
      left++;
    }
    while (right > left &&
           Verifier.isXMLWhitespace(str.charAt(right))) {
      right--;
    }

    if (left > right) {
      return "";
    }

    boolean space = true;
    final StringBuilder buffer = new StringBuilder(right - left + 1);
    while (left <= right) {
      final char c = str.charAt(left);
      if (Verifier.isXMLWhitespace(c)) {
        if (space) {
          buffer.append(' ');
          space = false;
        }
      }
      else {
        buffer.append(c);
        space = true;
      }
      left++;
    }
    return buffer.toString();
  }

  /**
   * Use the XML Specification definition of whitespace to Right-trim the
   * input value.
   *
   * @param str The value to trim.
   * @return The value right-trimmed
   * @since JDOM2
   */
  public static String trimRight(String str) {
    int right = str.length() - 1;
    while (right >= 0 && Verifier.isXMLWhitespace(str.charAt(right))) {
      right--;
    }
    if (right < 0) {
      return "";
    }
    return str.substring(0, right + 1);
  }

  /**
   * Use the XML Specification definition of whitespace to Left-trim the
   * input value.
   *
   * @param str The value to trim.
   * @return The value left-trimmed
   * @since JDOM2
   */
  public static String trimLeft(final String str) {
    final int right = str.length();
    int left = 0;
    while (left < right && Verifier.isXMLWhitespace(str.charAt(left))) {
      left++;
    }
    if (left >= right) {
      return "";
    }

    return str.substring(left);
  }

  /**
   * Use the XML Specification definition of whitespace to trim the
   * input value.
   *
   * @param str The value to trim.
   * @return The value trimmed
   * @since JDOM2
   */
  public static String trimBoth(final String str) {
    int right = str.length() - 1;
    while (right > 0 && Verifier.isXMLWhitespace(str.charAt(right))) {
      right--;
    }
    int left = 0;
    while (left <= right && Verifier.isXMLWhitespace(str.charAt(left))) {
      left++;
    }
    if (left > right) {
      return "";
    }
    return str.substring(left, right + 1);
  }

  private static EscapeStrategy chooseStrategy(String encoding) {
    if ("UTF-8".equalsIgnoreCase(encoding) ||
        "UTF-16".equalsIgnoreCase(encoding)) {
      return UTFEscapeStrategy;
    }

    // Note issue #149: https://github.com/hunterhacker/jdom/issues/149
    // require locale for case conversion to avoid potential security issue.
    if (encoding.toUpperCase(Locale.ENGLISH).startsWith("ISO-8859-") ||
        "Latin1".equalsIgnoreCase(encoding)) {
      return Bits8EscapeStrategy;
    }

    if ("US-ASCII".equalsIgnoreCase(encoding) ||
        "ASCII".equalsIgnoreCase(encoding)) {
      return Bits7EscapeStrategy;
    }

    try {
      final CharsetEncoder cse = Charset.forName(encoding).newEncoder();
      return new DefaultCharsetEscapeStrategy(cse);
    }
    catch (Exception e) {
      // swallow that... and assume false.
    }
    return DEFAULT_ESCAPE_STRATEGY;
  }


  /**
   * standard value to indent by if we are indenting
   */
  private static final String STANDARD_INDENT = "  ";

  /**
   * standard string with which to end a line
   */
  private static final String STANDARD_LINE_SEPARATOR = System.lineSeparator();

  /**
   * standard encoding
   */
  private static final String STANDARD_ENCODING = "UTF-8";

  /**
   * The default indent is no spaces (as an original document)
   */
  String indent = null;

  /**
   * New line separator
   */
  String lineSeparator = "\n";

  /**
   * The encoding format
   */
  String encoding = STANDARD_ENCODING;

  /**
   * Whether to output the XML declaration
   */
  boolean omitDeclaration = false;

  /**
   * Whether to output the encoding in the XML declaration
   * - default is <code>false</code>
   */
  boolean omitEncoding = false;

  /**
   * Whether or not to expand empty elements to
   * &lt;tagName&gt;&lt;/tagName&gt; - default is <code>false</code>
   */
  boolean expandEmptyElements = false;

  /**
   * Whether TrAX output escaping disabling/enabling PIs are ignored
   * or processed - default is <code>false</code>
   */
  boolean ignoreTrAXEscapingPIs = false;

  /**
   * text handling mode
   */
  TextMode mode = TextMode.PRESERVE;

  /**
   * entity escape logic
   */
  EscapeStrategy escapeStrategy = DEFAULT_ESCAPE_STRATEGY;

  /**
   * Creates a new Format instance with defaulted (raw) behavior.
   */
  private Format() {
    setEncoding(STANDARD_ENCODING);
  }

  /**
   * Returns the current escape strategy
   *
   * @return the current escape strategy
   */
  public EscapeStrategy getEscapeStrategy() {
    return escapeStrategy;
  }

  public Format setLineSeparator(String separator) {
    lineSeparator = separator == null ? STANDARD_LINE_SEPARATOR : separator;
    return this;
  }

  /**
   * Returns the current line separator.
   *
   * @return the current line separator
   */
  public String getLineSeparator() {
    return lineSeparator;
  }

  /**
   * This will set whether the XML declaration
   * (<code>&lt;&#063;xml version="1&#046;0"
   * encoding="UTF-8"&#063;&gt;</code>)
   * includes the encoding of the document. It is common to omit
   * this in uses such as WML and other wireless device protocols.
   *
   * @param omitEncoding <code>boolean</code> indicating whether or not
   *                     the XML declaration should indicate the document encoding.
   * @return a pointer to this Format for chaining
   */
  public Format setOmitEncoding(boolean omitEncoding) {
    this.omitEncoding = omitEncoding;
    return this;
  }

  /**
   * Returns whether the XML declaration encoding will be omitted.
   *
   * @return whether the XML declaration encoding will be omitted
   */
  public boolean getOmitEncoding() {
    return omitEncoding;
  }

  /**
   * This will set whether the XML declaration
   * (<code>&lt;&#063;xml version="1&#046;0"&#063;&gt;</code>)
   * will be omitted or not. It is common to omit this in uses such
   * as SOAP and XML-RPC calls.
   *
   * @param omitDeclaration <code>boolean</code> indicating whether or not
   *                        the XML declaration should be omitted.
   * @return a pointer to this Format for chaining
   */
  public Format setOmitDeclaration(boolean omitDeclaration) {
    this.omitDeclaration = omitDeclaration;
    return this;
  }

  /**
   * Returns whether the XML declaration will be omitted.
   *
   * @return whether the XML declaration will be omitted
   */
  public boolean getOmitDeclaration() {
    return omitDeclaration;
  }

  /**
   * This will set whether empty elements are expanded from
   * <code>&lt;tagName/&gt;</code> to
   * <code>&lt;tagName&gt;&lt;/tagName&gt;</code>.
   *
   * @param expandEmptyElements <code>boolean</code> indicating whether or not
   *                            empty elements should be expanded.
   * @return a pointer to this Format for chaining
   */
  public Format setExpandEmptyElements(boolean expandEmptyElements) {
    this.expandEmptyElements = expandEmptyElements;
    return this;
  }

  /**
   * Returns whether empty elements are expanded.
   *
   * @return whether empty elements are expanded
   */
  public boolean getExpandEmptyElements() {
    return expandEmptyElements;
  }
  /**
   * Returns whether JAXP TrAX processing instructions for
   * disabling/enabling output escaping are ignored.
   *
   * @return whether or not TrAX ouput escaping PIs are ignored.
   */
  public boolean getIgnoreTrAXEscapingPIs() {
    return ignoreTrAXEscapingPIs;
  }

  /**
   * This sets the text output style.  Options are available as static
   * {@link TextMode} instances.  The default is {@link TextMode#PRESERVE}.
   *
   * @param mode The TextMode to set.
   * @return a pointer to this Format for chaining
   */
  public Format setTextMode(TextMode mode) {
    this.mode = mode;
    return this;
  }

  /**
   * Returns the current text output style.
   *
   * @return the current text output style
   */
  public TextMode getTextMode() {
    return mode;
  }

  /**
   * This will set the indent <code>String</code> to use; this
   * is usually a <code>String</code> of empty spaces. If you pass
   * the empty string (""), then no indentation will happen, but newlines
   * will still be generated.  Passing null will result in no indentation
   * and no newlines generated.  Default: none (null)
   *
   * @param indent <code>String</code> to use for indentation.
   * @return a pointer to this Format for chaining
   */
  public Format setIndent(String indent) {
    this.indent = indent;
    return this;
  }

  /**
   * Returns the indent string in use.
   *
   * @return the indent string in use
   */
  public String getIndent() {
    return indent;
  }

  /**
   * Sets the output encoding.  The name should be an accepted XML
   * encoding.
   *
   * @param encoding the encoding format.  Use XML-style names like
   *                 "UTF-8" or "ISO-8859-1" or "US-ASCII"
   * @return a pointer to this Format for chaining
   */
  public Format setEncoding(String encoding) {
    this.encoding = encoding;
    escapeStrategy = chooseStrategy(encoding);
    return this;
  }

  /**
   * Returns the configured output encoding.
   *
   * @return the output encoding
   */
  public String getEncoding() {
    return encoding;
  }

  @Override
  public Format clone() {
    Format format = null;

    try {
      format = (Format)super.clone();
    }
    catch (CloneNotSupportedException ce) {
      // swallow.
    }

    return format;
  }

  /**
   * Class to signify how a text should be handled on output.  The following
   * table provides details.
   *
   * <table>
   *   <tr>
   *     <th align="left">
   *       Text Mode
   *     </th>
   *     <th>
   *       Resulting behavior.
   *     </th>
   *   </tr>
   * <p>
   *   <tr valign="top">
   *     <td>
   *       <i>PRESERVE (Default)</i>
   *     </td>
   *     <td>
   *       All content is printed in the format it was created, no whitespace
   *       or line separators are are added or removed.
   *     </td>
   *   </tr>
   * <p>
   *   <tr valign="top">
   *     <td>
   *       TRIM_FULL_WHITE
   *     </td>
   *     <td>
   *       Content between tags consisting of all whitespace is not printed.
   *       If the content contains even one non-whitespace character, it is
   *       all printed verbatim, whitespace and all.
   *     </td>
   *   </tr>
   * <p>
   *   <tr valign="top">
   *     <td>
   *       TRIM
   *     </td>
   *     <td>
   *       All leading and trailing whitespace is trimmed.
   *     </td>
   *   </tr>
   * <p>
   *   <tr valign="top">
   *     <td>
   *       NORMALIZE
   *     </td>
   *     <td>
   *       Leading and trailing whitespace is trimmed, and any 'internal'
   *       whitespace is compressed to a single space.
   *     </td>
   *   </tr>
   * </table>
   * <p>
   * In most cases, textual content is aligned with the surrounding tags
   * (after the appropriate text mode is applied). In the case where the only
   * content between the start and end tags is textual, the start tag, text,
   * and end tag are all printed on the same line. If the document
   * output already has whitespace, it's wise to turn on TRIM mode so the
   * pre-existing whitespace can be trimmed before adding new whitespace.
   * <p>
   * When an element has a xml:space attribute with the value of "preserve",
   * all formating is turned off (actually, the TextMode is set to
   * {@link #PRESERVE} until the element and its contents have been printed.
   * If a nested element contains another xml:space with the value "default"
   * formatting is turned back on for the child element and then off for the
   * remainder of the parent element.
   *
   * @since JDOM2
   */
  public enum TextMode {
    /**
     * Mode for literal text preservation.
     */
    PRESERVE,

    /**
     * Mode for text trimming (left and right trim).
     */
    TRIM,

    /**
     * Mode for text normalization (left and right trim plus internal
     * whitespace is normalized to a single space.
     *
     * @see org.jdom.Element#getTextNormalize
     */
    NORMALIZE,

    /**
     * Mode for text trimming of content consisting of nothing but
     * whitespace but otherwise not changing output.
     */
    TRIM_FULL_WHITE
  }
}
