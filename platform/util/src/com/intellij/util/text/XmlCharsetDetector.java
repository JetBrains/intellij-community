/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.text;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class XmlCharsetDetector {
  @NonNls private static final String XML_PROLOG_START = "<?xml";
  @NonNls private static final byte[] XML_PROLOG_START_BYTES = CharsetToolkit.getUtf8Bytes(XML_PROLOG_START);
  @NonNls private static final String ENCODING = "encoding";
  @NonNls private static final byte[] ENCODING_BYTES = CharsetToolkit.getUtf8Bytes(ENCODING);
  @NonNls private static final String XML_PROLOG_END = "?>";
  @NonNls private static final byte[] XML_PROLOG_END_BYTES = CharsetToolkit.getUtf8Bytes(XML_PROLOG_END);

  @Nullable
  public static String extractXmlEncodingFromProlog(@NotNull byte[] bytes) {
    int index = 0;
    if (CharsetToolkit.hasUTF8Bom(bytes)) {
      index = CharsetToolkit.UTF8_BOM.length;
    }

    index = skipWhiteSpace(index, bytes);
    if (!ArrayUtil.startsWith(bytes, index, XML_PROLOG_START_BYTES)) return null;
    index += XML_PROLOG_START_BYTES.length;
    while (index < bytes.length) {
      index = skipWhiteSpace(index, bytes);
      if (ArrayUtil.startsWith(bytes, index, XML_PROLOG_END_BYTES)) return null;
      if (ArrayUtil.startsWith(bytes, index, ENCODING_BYTES)) {
        index += ENCODING_BYTES.length;
        index = skipWhiteSpace(index, bytes);
        if (index >= bytes.length || bytes[index] != '=') continue;
        index++;
        index = skipWhiteSpace(index, bytes);
        if (index >= bytes.length || bytes[index] != '\'' && bytes[index] != '\"') continue;
        byte quote = bytes[index];
        index++;
        StringBuilder encoding = new StringBuilder();
        while (index < bytes.length) {
          if (bytes[index] == quote) return encoding.toString();
          encoding.append((char)bytes[index++]);
        }
      }
      index++;
    }
    return null;
  }

  @Nullable
  public static String extractXmlEncodingFromProlog(@NotNull CharSequence text) {
    int index = 0;

    index = skipWhiteSpace(index, text);
    if (!StringUtil.startsWith(text, index, XML_PROLOG_START)) return null;
    index += XML_PROLOG_START.length();
    while (index < text.length()) {
      index = skipWhiteSpace(index, text);
      if (StringUtil.startsWith(text, index, XML_PROLOG_END)) return null;
      if (StringUtil.startsWith(text, index, ENCODING)) {
        index += ENCODING.length();
        index = skipWhiteSpace(index, text);
        if (index >= text.length() || text.charAt(index) != '=') continue;
        index++;
        index = skipWhiteSpace(index, text);
        if (index >= text.length()) continue;
        char quote = text.charAt(index);
        if (quote != '\'' && quote != '\"') continue;
        index++;
        StringBuilder encoding = new StringBuilder();
        while (index < text.length()) {
          char c = text.charAt(index);
          if (c == quote) return encoding.toString();
          encoding.append(c);
          index++;
        }
      }
      index++;
    }
    return null;
  }

  private static int skipWhiteSpace(int start, @NotNull byte[] bytes) {
    while (start < bytes.length) {
      char c = (char)bytes[start];
      if (!Character.isWhitespace(c)) break;
      start++;
    }
    return start;
  }

  private static int skipWhiteSpace(int start, @NotNull CharSequence text) {
    while (start < text.length()) {
      char c = text.charAt(start);
      if (!Character.isWhitespace(c)) break;
      start++;
    }
    return start;
  }
}
