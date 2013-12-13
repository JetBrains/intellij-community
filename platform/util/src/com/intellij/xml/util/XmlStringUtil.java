/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.xml.util;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class XmlStringUtil {
  @NonNls private static final String HTML_HEADER = "<html>";
  @NonNls private static final String BODY_HEADER = "<body>";
  @NonNls private static final String HTML_FOOTER = "</html>";
  @NonNls private static final String BODY_FOOTER = "</body>";

  private XmlStringUtil() {
  }

  public static String escapeString(@Nullable String str) {
    return escapeString(str, false);
  }

  public static String escapeString(@Nullable String str, final boolean escapeWhiteSpace) {
    return escapeString(str, escapeWhiteSpace, true);
  }

  public static String escapeString(@Nullable String str, final boolean escapeWhiteSpace, final boolean convertNoBreakSpace) {
    if (str == null) return null;
    StringBuilder buffer = null;
    for (int i = 0; i < str.length(); i++) {
      @NonNls String entity;
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
          entity = "&quot;";
          break;
        case '<':
          entity = "&lt;";
          break;
        case '>':
          entity = "&gt;";
          break;
        case '&':
          entity = "&amp;";
          break;
        case 160: // unicode char for &nbsp;
          entity = convertNoBreakSpace ? "&nbsp;" : null;
          break;
        default:
          entity = null;
          break;
      }
      if (buffer == null) {
        if (entity != null) {
          // An entity occurred, so we'll have to use StringBuffer
          // (allocate room for it plus a few more entities).
          buffer = new StringBuilder(str.length() + 20);
          // Copy previous skipped characters and fall through
          // to pickup current character
          buffer.append(str.substring(0, i));
          buffer.append(entity);
        }
      }
      else {
        if (entity == null) {
          buffer.append(ch);
        }
        else {
          buffer.append(entity);
        }
      }
    }

    // If there were any entities, return the escaped characters
    // that we put in the StringBuffer. Otherwise, just return
    // the unmodified input string.
    return buffer == null ? str : buffer.toString();
  }

  @NotNull
  public static String wrapInHtml(@NotNull CharSequence result) {
    return HTML_HEADER + result + HTML_FOOTER;
  }

  public static boolean isWrappedInHtml(@NotNull String tooltip) {
    return StringUtil.startsWithIgnoreCase(tooltip, HTML_HEADER) &&
           StringUtil.endsWithIgnoreCase(tooltip, HTML_FOOTER);
  }

  @NotNull
  public static String stripHtml(@NotNull String toolTip) {
    toolTip = StringUtil.trimStart(toolTip, HTML_HEADER);
    toolTip = StringUtil.trimStart(toolTip, BODY_HEADER);
    toolTip = StringUtil.trimEnd(toolTip, HTML_FOOTER);
    toolTip = StringUtil.trimEnd(toolTip, BODY_FOOTER);
    return toolTip;
  }
}