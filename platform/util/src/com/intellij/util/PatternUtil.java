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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class PatternUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.PatternUtil");
  private static final HashMap<String, String> ourEscapeRules = new HashMap<String, String>();

  static {
    // '.' should be escaped first
    ourEscapeRules.put("*", ".*");
    ourEscapeRules.put("?", ".");
    escape2('+');
    escape2('(');
    escape2(')');
    escape2('[');
    escape2(']');
    escape2('/');
    escape2('^');
    escape2('$');
    escape2('{');
    escape2('}');
    escape2('|');
  }

  private static void escape2(char symbol) {
    ourEscapeRules.put(String.valueOf(symbol), "\\" + symbol);
  }

  public static String convertToRegex(String mask) {
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

  public static Pattern fromMask(@NonNls String mask) {
//    String pattern = mask.replaceAll("\\.", "\\.").replaceAll("\\*", ".*").replaceAll("\\?", ".");
    try {
      return Pattern.compile(convertToRegex(mask));
    }
    catch (PatternSyntaxException e) {
      LOG.error(mask, e);
      return Pattern.compile("");
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
