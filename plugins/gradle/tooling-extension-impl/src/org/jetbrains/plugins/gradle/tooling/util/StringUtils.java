// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtils {
  private static final Pattern WORD_SEPARATOR = Pattern.compile("\\W+");

  /**
   * Decompiled from org.gradle.util.GUtil
   */
  public static String toCamelCase(String string, boolean lower) {
    if (string == null) {
      return null;
    } else {
      StringBuilder builder = new StringBuilder();
      Matcher matcher = WORD_SEPARATOR.matcher(string);
      int pos = 0;
      boolean first = true;

      while(true) {
        String chunk;
        do {
          if (!matcher.find()) {
            chunk = string.subSequence(pos, string.length()).toString();
            if (lower && first) {
              chunk = org.gradle.internal.impldep.org.apache.commons.lang.StringUtils.uncapitalize(chunk);
            } else {
              chunk = org.gradle.internal.impldep.org.apache.commons.lang.StringUtils.capitalize(chunk);
            }

            builder.append(chunk);
            return builder.toString();
          }

          chunk = string.subSequence(pos, matcher.start()).toString();
          pos = matcher.end();
        } while(chunk.isEmpty());

        if (lower && first) {
          chunk = org.gradle.internal.impldep.org.apache.commons.lang.StringUtils.uncapitalize(chunk);
          first = false;
        } else {
          chunk = org.gradle.internal.impldep.org.apache.commons.lang.StringUtils.capitalize(chunk);
        }

        builder.append(chunk);
      }
    }
  }
}
