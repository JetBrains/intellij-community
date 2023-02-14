// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.util.text.Strings;
import org.jdom.Verifier;
import org.jetbrains.annotations.*;

import static com.intellij.xml.CommonXmlStrings.*;

public final class XmlStringUtil {
  private XmlStringUtil() {
  }

  public static @NotNull String wrapInCDATA(@NotNull String str) {
    StringBuilder sb = new StringBuilder();
    int cur = 0;
    int len = str.length();
    while (cur < len) {
      int next = Strings.indexOf(str, CDATA_END, cur);
      sb.append(CDATA_START).append(str.subSequence(cur, next = next < 0 ? len : next + 1)).append(CDATA_END);
      cur = next;
    }
    return sb.toString();
  }

  @Contract(value = "null->null; !null->!null", pure = true)
  public static String escapeString(@Nullable String str) {
    return escapeString(str, false);
  }

  @Contract(value = "null,_->null; !null,_->!null", pure = true)
  public static String escapeString(@Nullable String str, final boolean escapeWhiteSpace) {
    return escapeString(str, escapeWhiteSpace, true);
  }

  @Contract(value = "null,_,_->null; !null,_,_->!null", pure = true)
  public static String escapeString(@Nullable String str, final boolean escapeWhiteSpace, final boolean convertNoBreakSpace) {
    if (str == null) {
      return null;
    }

    StringBuilder buffer = null;
    for (int i = 0; i < str.length(); i++) {
      String entity;
      char ch = str.charAt(i);
      switch (ch) {
        case '\n':
          entity = escapeWhiteSpace ? "&#10;" : null;
          break;
        case '\r':
          entity = escapeWhiteSpace ? "&#13;" : null;
          break;
        case '\t':
          entity = escapeWhiteSpace ? "&#9;" : null;
          break;
        case '\"':
          entity = QUOT;
          break;
        case '<':
          entity = LT;
          break;
        case '>':
          entity = GT;
          break;
        case '&':
          entity = AMP;
          break;
        case 160: // unicode char for &nbsp;
          entity = convertNoBreakSpace ? NBSP : null;
          break;
        default:
          entity = null;
          break;
      }
      buffer = appendEscapedSymbol(str, buffer, i, entity, ch);
    }

    // If there were any entities, return the escaped characters
    // that we put in the StringBuffer. Otherwise, just return
    // the unmodified input string.
    return buffer == null ? str : buffer.toString();
  }

  public static @Nullable StringBuilder appendEscapedSymbol(@NotNull String str, StringBuilder buffer, int i, String entity, char ch) {
    if (buffer == null) {
      if (entity != null) {
        // An entity occurred, so we'll have to use StringBuffer
        // (allocate room for it plus a few more entities).
        buffer = new StringBuilder(str.length() + 20);
        // Copy previous skipped characters and fall through
        // to pickup current character
        buffer.append(str, 0, i);
        buffer.append(entity);
      }
    }
    else if (entity == null) {
      buffer.append(ch);
    }
    else {
      buffer.append(entity);
    }
    return buffer;
  }

  @Contract(pure = true)
  public static @NotNull String wrapInHtml(@NotNull CharSequence result) {
    return HTML_START + result + HTML_END;
  }

  /**
   * @param lines Text to be used for example in multi-line labels
   * @return HTML where specified lines separated by &lt;br&gt; and each line wrapped in &lt;nobr&gt; to prevent breaking text inside
   */
  public static @NotNull String wrapInHtmlLines(CharSequence @NotNull ... lines) {
    StringBuilder sb = new StringBuilder(HTML_START);
    for (int i = 0; i < lines.length; i++) {
      CharSequence sequence = lines[i];
      if (i > 0) sb.append("<br>");
      sb.append("<nobr>").append(sequence).append("</nobr>");
    }
    return sb.append(HTML_END).toString();
  }

  public static @NotNull String wrapInHtmlTag(@Nls @NotNull String text, @NonNls @NotNull String tagWord) {
    return String.format("<%s>%s</%s>", tagWord, text, tagWord);
  }

  public static @NotNull String wrapInHtmlTagWithAttributes(@Nls @NotNull String text,
                                                            @NonNls @NotNull String tagWord,
                                                            @NonNls @NotNull String attributes) {
    return String.format("<%s %s>%s</%s>", tagWord, attributes, text, tagWord);
  }

  public static @NotNull String formatLink(@NonNls @NotNull String targetUrl, @Nls @NotNull String text) {
    return wrapInHtmlTagWithAttributes(text, "a", "href=\"" + targetUrl + "\"");
  }

  public static boolean isWrappedInHtml(@NotNull String tooltip) {
    return StringUtilRt.startsWithIgnoreCase(tooltip, HTML_START) &&
           Strings.endsWithIgnoreCase(tooltip, HTML_END);
  }

  @Contract(pure = true)
  public static @NotNull String stripHtml(@NotNull String toolTip) {
    toolTip = Strings.trimStart(toolTip, HTML_START);
    toolTip = Strings.trimStart(toolTip, BODY_START);
    toolTip = Strings.trimEnd(toolTip, HTML_END);
    toolTip = Strings.trimEnd(toolTip, BODY_END);
    return toolTip;
  }

  /**
   * Converts {@code text} to a string which can be used inside an HTML document: if it's already an HTML text the root html/body tags will
   * be stripped, if it's a plain text special characters will be escaped
   */
  @Contract(pure = true)
  public static @NotNull String convertToHtmlContent(@NotNull String text) {
    return isWrappedInHtml(text) ? stripHtml(text) : escapeString(text);
  }

  /**
   * Some characters are illegal in XML even as numerical character references. This method performs escaping of them
   * in a custom format, which is supposed to be unescaped on retrieving from XML using {@link #unescapeIllegalXmlChars(String)}.
   * Resulting text can be part of XML version 1.0 document.
   *
   * @see <a href="https://www.w3.org/International/questions/qa-controls">https://www.w3.org/International/questions/qa-controls</a>
   * @see Verifier#isXMLCharacter(int)
   */
  public static @NotNull String escapeIllegalXmlChars(@NotNull String text) {
    StringBuilder b = null;
    int lastPos = 0;
    for (int i = 0; i < text.length(); i++) {
      int c = text.codePointAt(i);
      if (Character.isSupplementaryCodePoint(c)) {
        //noinspection AssignmentToForLoopParameter
        i++;
      }
      if (c == '#' || !Verifier.isXMLCharacter(c)) {
        if (b == null) b = new StringBuilder(text.length() + 5); // assuming there's one 'large' char (e.g. 0xFFFF) to escape numerically
        b.append(text, lastPos, i).append('#');
        if (c != '#') b.append(Integer.toHexString(c));
        b.append('#');
        lastPos = i + 1;
      }
    }
    return b == null ? text : b.append(text, lastPos, text.length()).toString();
  }

  /**
   * @see XmlStringUtil#escapeIllegalXmlChars(String)
   */
  public static @NotNull String unescapeIllegalXmlChars(@NotNull String text) {
    StringBuilder b = null;
    int lastPos = 0;
    for (int i = 0; i < text.length(); i++) {
      int c = text.charAt(i);
      if (c == '#') {
        int numberEnd = text.indexOf('#', i + 1);
        if (numberEnd > 0) {
          int charCode;
          try {
            charCode = numberEnd == i + 1 ? '#' : Integer.parseInt(text.substring(i + 1, numberEnd), 16);
          }
          catch (NumberFormatException e) {
            continue;
          }
          if (b == null) b = new StringBuilder(text.length());
          b.append(text, lastPos, i);
          b.append((char)charCode);
          //noinspection AssignmentToForLoopParameter
          i = numberEnd;
          lastPos = i + 1;
        }
      }
    }
    return b == null ? text : b.append(text, lastPos, text.length()).toString();
  }
}