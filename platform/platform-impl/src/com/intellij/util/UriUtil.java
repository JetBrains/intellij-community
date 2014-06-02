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
package com.intellij.util;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.Couple;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public final class UriUtil {
  public static final CharMatcher PARAM_CHAR_MATCHER = CharMatcher.anyOf("?#;");
  public static final CharMatcher SLASH_MATCHER = CharMatcher.is('/');

  private UriUtil() {
  }

  @NotNull
  public static String trimTrailingSlashes(@NotNull String url) {
    return SLASH_MATCHER.trimTrailingFrom(url);
  }

  @NotNull
  public static String trimLeadingSlashes(@NotNull String url) {
    return SLASH_MATCHER.trimLeadingFrom(url);
  }

  public static String trimParameters(@NotNull String url) {
    int end = PARAM_CHAR_MATCHER.indexIn(url);
    return end != -1 ? url.substring(0, end) : url;
  }

  /**
   * Splits the url into 2 parts: the scheme ("http", for instance) and the rest of the URL. <br/>
   * Scheme separator is not included neither to the scheme part, nor to the url part. <br/>
   * The scheme can be absent, in which case empty string is written to the first item of the Pair.
   */
  @NotNull
  public static Couple<String> splitScheme(@NotNull String url) {
    ArrayList<String> list = Lists.newArrayList(Splitter.on(URLUtil.SCHEME_SEPARATOR).limit(2).split(url));
    if (list.size() == 1) {
      return Couple.of("", list.get(0));
    }
    return Couple.of(list.get(0), list.get(1));
  }
}