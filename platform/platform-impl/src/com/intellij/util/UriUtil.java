/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.io.BaseEncoding;
import com.google.common.net.MediaType;
import com.intellij.openapi.util.Pair;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.text.StringUtil.stripQuotesAroundValue;

public final class UriUtil {
  public static final CharMatcher PARAM_CHAR_MATCHER = CharMatcher.anyOf("?#;");
  public static final CharMatcher SLASH_MATCHER = CharMatcher.is('/');

  private static final Pattern DATA_URI_PATTERN = Pattern.compile("data:([^,;]+/[^,;]+)(;charset=[^,;]+)?(;base64)?,(.+)");

  private UriUtil() {
  }

  public static String trimLastSlash(@NotNull String url) {
    return SLASH_MATCHER.trimTrailingFrom(url);
  }

  public static String trimParameters(@NotNull String url) {
    int end = PARAM_CHAR_MATCHER.indexIn(url);
    return end != -1 ? url.substring(0, end) : url;
  }

  public static boolean isDataUri(@NotNull String value) {
    return !value.isEmpty() && value.startsWith("data:", value.charAt(0) == '"' || value.charAt(0) == '\'' ? 1 : 0);
  }

  /**
   * Extracts byte array from given data:URL string.
   * data:URL will be decoded from base64 if it contains the marker of base64 encoding.
   *
   * @param dataUrl data:URL-like string (may be quoted)
   * @return extracted byte array or {@code null} if it cannot be extracted.
   */
  @Nullable
  public static byte[] getBytesFromDataUri(@NotNull String dataUrl) {
    Matcher matcher = DATA_URI_PATTERN.matcher(stripQuotesAroundValue(dataUrl));
    if (matcher.matches()) {
      try {
        String content = matcher.group(4);
        return ";base64".equalsIgnoreCase(matcher.group(3)) ? BaseEncoding.base64().decode(content) : content.getBytes(Charsets.UTF_8);
      }
      catch (IllegalArgumentException e) {
        return null;
      }
    }
    return null;
  }

  /**
   * Extracts mime-type of given data:URL string
   *
   * @param dataUrl data:URL-like string (may be quoted)
   * @return mime-type extracted from image or {@code null} if string doesn't contain mime definition.
   */
  @Nullable
  public static MediaType getMediaTypeFromDataUri(@NotNull String dataUrl) {
    Matcher matcher = DATA_URI_PATTERN.matcher(stripQuotesAroundValue(dataUrl));
    if (matcher.matches()) {
      try {
        return MediaType.parse(matcher.group(1));
      }
      catch (IllegalArgumentException e) {
        return null;
      }
    }
    return null;
  }

  /**
   * Splits the url into 2 parts: the scheme ("http", for instance) and the rest of the URL. <br/>
   * Scheme separator is not included neither to the scheme part, nor to the url part. <br/>
   * The scheme can be absent, in which case empty string is written to the first item of the Pair.
   */
  @NotNull
  public static Pair<String, String> splitScheme(@NotNull String url) {
    ArrayList<String> list = Lists.newArrayList(Splitter.on(URLUtil.SCHEME_SEPARATOR).limit(2).split(url));
    if (list.size() == 1) {
      return Pair.create("", list.get(0));
    }
    return Pair.create(list.get(0), list.get(1));
  }
}