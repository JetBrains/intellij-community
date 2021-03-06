// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class PatternUtil {
  private static final Logger LOG = Logger.getInstance(PatternUtil.class);

  public static final Pattern NOTHING = Pattern.compile("(a\\A)");

  private static final Map<String, String> ourEscapeRules = new LinkedHashMap<>();

  static {
    // '.' should be escaped first
    ourEscapeRules.put("*", ".*");
    ourEscapeRules.put("?", ".");
    for (char c : "+()[]/^${}|".toCharArray()) {
      ourEscapeRules.put(String.valueOf(c), "\\" + c);
    }
  }

  @NotNull
  public static String convertToRegex(@NotNull String mask) {
    List<String> strings = StringUtil.split(mask, "\\");
    StringBuilder pattern = new StringBuilder();
    String separator = "";

    for (String string : strings) {
      string = StringUtil.replace(string, ".", "\\.");
      for (Map.Entry<String, String> e : ourEscapeRules.entrySet()) {
        string = StringUtil.replace(string, e.getKey(), e.getValue());
      }
      pattern.append(separator);
      separator = "\\\\";
      pattern.append(string);
    }
    return pattern.toString();
  }

  @NotNull
  public static Pattern fromMask(@NotNull String mask) {
    try {
      return Pattern.compile(convertToRegex(mask));
    }
    catch (PatternSyntaxException e) {
      LOG.error(mask, e);
      return NOTHING;
    }
  }

  @Contract("_, !null->!null")
  public static Pattern compileSafe(String pattern, Pattern def) {
    try {
      return Pattern.compile(pattern);
    }
    catch (Exception e) {
      return def;
    }
  }
  /**
   * Finds the first match in a list os Strings.
   *
   * @param lines list of lines, may be null.
   * @param regex pattern to match to.
   * @return pattern's first matched group, or entire matched string if pattern has no groups, or null.
   */
  @Nullable
  public static String getFirstMatch(List<String> lines, Pattern regex) {
    if (lines == null) return null;
    for (String s : lines) {
      Matcher m = regex.matcher(s);
      if (m.matches()) {
        if (m.groupCount() > 0) {
          return m.group(1);
        }
      }
    }
    return null;
  }
}
