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
package com.intellij.util.execution;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtilRt;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author nik
 */
public class ParametersListUtil {
  public static final Function<String, List<String>> DEFAULT_LINE_PARSER = new Function<String, List<String>>() {
    @Override
    public List<String> fun(String text) {
      return parse(text, true);
    }
  };
  public static final Function<List<String>, String> DEFAULT_LINE_JOINER = new Function<List<String>, String>() {
    @Override
    public String fun(List<String> strings) {
      return StringUtil.join(strings, " ");
    }
  };
  public static final Function<String, List<String>> COLON_LINE_PARSER = new Function<String, List<String>>() {
    @Override
    public List<String> fun(String text) {
      final ArrayList<String> result = ContainerUtilRt.newArrayList();
      final StringTokenizer tokenizer = new StringTokenizer(text, ";", false);
      while (tokenizer.hasMoreTokens()) {
        result.add(tokenizer.nextToken());
      }
      return result;
    }
  };
  public static final Function<List<String>, String> COLON_LINE_JOINER = new Function<List<String>, String>() {
    @Override
    public String fun(List<String> strings) {
      return StringUtil.join(strings, ";");
    }
  };

  /**
   * <p>Joins list of parameters into single string, which may be then parsed back into list by {@link #parseToArray(String)}.</p>
   * <p/>
   * <p>
   * <strong>Conversion rules:</strong>
   * <ul>
   * <li>double quotes are escaped by backslash ({@code &#92;});</li>
   * <li>empty parameters parameters and parameters with spaces inside are surrounded with double quotes ({@code "});</li>
   * <li>parameters are separated by single whitespace.</li>
   * </ul>
   * </p>
   * <p/>
   * <p><strong>Examples:</strong></p>
   * <p>
   * {@code ['a', 'b'] => 'a  b'}<br/>
   * {@code ['a="1 2"', 'b'] => '"a &#92;"1 2&#92;"" b'}
   * </p>
   *
   * @param parameters a list of parameters to join.
   * @return a string with parameters.
   */
  @NotNull
  public static String join(@NotNull final List<? extends CharSequence> parameters) {
    return encode(parameters);
  }

  @NotNull
  public static String join(final String... parameters) {
    return encode(Arrays.asList(parameters));
  }

  /**
   * @see #parse(String)
   */
  @NotNull
  public static String[] parseToArray(@NotNull final String string) {
    final List<String> params = parse(string);
    return ArrayUtil.toStringArray(params);
  }

  /**
   * <p>Splits single parameter string (as created by {@link #join(List)}) into list of parameters.</p>
   * <p/>
   * <p>
   * <strong>Conversion rules:</strong>
   * <ul>
   * <li>starting/whitespaces are trimmed;</li>
   * <li>parameters are split by whitespaces, whitespaces itself are dropped</li>
   * <li>parameters inside double quotes ({@code "a b"}) are kept as single one;</li>
   * <li>double quotes are dropped, escaped double quotes ({@code &#92;"}) are un-escaped.</li>
   * <li>For single quotes support see {@link #parse(String, boolean, boolean)}</li>
   * </ul>
   * </p>
   * <p/>
   * <p><strong>Examples:</strong></p>
   * <p>
   * {@code ' a  b ' => ['a', 'b']}<br/>
   * {@code 'a="1 2" b' => ['a=1 2', 'b']}<br/>
   * {@code 'a " " b' => ['a', ' ', 'b']}<br/>
   * {@code '"a &#92;"1 2&#92;"" b' => ['a="1 2"', 'b']}
   * </p>
   *
   * @param parameterString parameter string to split.
   * @return array of parameters.
   */
  @NotNull
  public static List<String> parse(@NotNull String parameterString) {
    return parse(parameterString, false);
  }

  @NotNull
  public static List<String> parse(@NotNull String parameterString, boolean keepQuotes) {
    return parse(parameterString, keepQuotes, false);
  }

  @NotNull
  public static List<String> parse(@NotNull String parameterString, boolean keepQuotes, boolean supportSingleQuotes) {
    parameterString = parameterString.trim();

    final ArrayList<String> params = ContainerUtilRt.newArrayList();
    final StringBuilder token = new StringBuilder(128);
    boolean inQuotes = false;
    boolean escapedQuote = false;
    final TIntHashSet possibleQuoteChars = new TIntHashSet();
    possibleQuoteChars.add('"');
    if (supportSingleQuotes) {
      possibleQuoteChars.add('\'');
    }
    char currentQuote = 0;
    boolean nonEmpty = false;

    for (int i = 0; i < parameterString.length(); i++) {
      final char ch = parameterString.charAt(i);
      if ((inQuotes ? currentQuote == ch : possibleQuoteChars.contains(ch))) {
        if (!escapedQuote) {
          inQuotes = !inQuotes;
          currentQuote = ch;
          nonEmpty = true;
          if (!keepQuotes) {
            continue;
          }
        }
        escapedQuote = false;
      }
      else if (Character.isWhitespace(ch)) {
        if (!inQuotes) {
          if (token.length() > 0 || nonEmpty) {
            params.add(token.toString());
            token.setLength(0);
            nonEmpty = false;
          }
          continue;
        }
      }
      else if (ch == '\\' && i < parameterString.length() - 1) {
        final char nextchar = parameterString.charAt(i + 1);
        if (inQuotes ? currentQuote == nextchar : possibleQuoteChars.contains(nextchar)) {
          escapedQuote = true;
          if (!keepQuotes) {
            continue;
          }
        }
      }

      token.append(ch);
    }

    if (token.length() > 0 || nonEmpty) {
      params.add(token.toString());
    }

    return params;
  }

  @NotNull
  private static String encode(@NotNull final List<? extends CharSequence> parameters) {
    if (parameters.isEmpty()) {
      return "";
    }

    final StringBuilder buffer = new StringBuilder();
    final StringBuilder paramBuilder = new StringBuilder();
    for (CharSequence parameter : parameters) {
      if (buffer.length() > 0) {
        buffer.append(' ');
      }

      paramBuilder.append(parameter);
      encodeParam(paramBuilder);
      buffer.append(paramBuilder);
      paramBuilder.setLength(0);
    }
    return buffer.toString();
  }

  private static void encodeParam(@NotNull StringBuilder builder) {
    StringUtil.escapeQuotes(builder);
    if (builder.length() == 0 || StringUtil.indexOf(builder, ' ') >= 0 || StringUtil.indexOf(builder, '|') >= 0) {
      // don't let a trailing backslash (if any) unintentionally escape the closing quote
      int numTrailingBackslashes = builder.length() - StringUtil.trimTrailing(builder, '\\').length();
      StringUtil.quote(builder);
      StringUtil.repeatSymbol(builder, '\\', numTrailingBackslashes);
    }
  }
}
