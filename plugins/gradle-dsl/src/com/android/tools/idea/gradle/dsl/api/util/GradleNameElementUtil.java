// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.gradle.dsl.api.util;

import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GradleNameElementUtil {


  @NotNull
  public static String escape(@NotNull String part) {
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < part.length(); i++) {
      char c = part.charAt(i);
      if (c == '.' || c == '\\') {
        buf.append('\\');
      }
      buf.append(c);
    }
    String result = buf.toString();
    return result;
  }

  @NotNull
  public static String unescape(@NotNull String part) {
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < part.length(); i++) {
      char c = part.charAt(i);
      if (c == '\\') {
        assert i < part.length() - 1;
        buf.append(part.charAt(++i));
      }
      else {
        buf.append(c);
      }
    }
    String result = buf.toString();
    return result;
  }

  @NotNull
  @VisibleForTesting
  public static String join (@NotNull List<String> parts) {
    String result = parts.stream().map(GradleNameElementUtil::escape).collect(Collectors.joining("."));
    return result;
  }

  @NotNull
  public static List<String> split(@NotNull String name) {
    StringBuilder buf = new StringBuilder();
    List<String> result = new ArrayList<>();
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c == '\\') {
        assert i < name.length() - 1;
        buf.append(name.charAt(++i));
      }
      else if (c == '.') {
        result.add(buf.toString());
        buf.setLength(0);
      }
      else {
        buf.append(name.charAt(i));
      }
    }
    result.add(buf.toString());
    return result;
  }
}
