// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class StatisticsEventLogUtil {
  @NonNls public static final String UTF8 = "UTF-8";

  @NotNull
  public static CloseableHttpClient create(@NotNull String userAgent) {
    return HttpClientBuilder.create().setUserAgent(userAgent).build();
  }

  public static boolean isEmpty(@Nullable String s) {
    return s == null || s.isEmpty();
  }

  public static boolean isNotEmpty(@Nullable String s) {
    return !isEmpty(s);
  }

  public static boolean isEmptyOrSpaces(@Nullable String s) {
    if (isEmpty(s)) {
      return true;
    }
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) > ' ') {
        return false;
      }
    }
    return true;
  }

  public static boolean equals(@Nullable String s1, @Nullable String s2) {
    if (s1 == s2) return true;
    if (s1 == null || s2 == null) return false;

    if (s1.length() != s2.length()) return false;
    return s1.equals(s2);
  }

  public static String[] mergeArrays(@NotNull String[] a1, @NotNull String[] a2) {
    String[] result = new String[a1.length + a2.length];
    System.arraycopy(a1, 0, result, 0, a1.length);
    System.arraycopy(a2, 0, result, a1.length, a2.length);
    return result;
  }

  public static Element parseXml(@NotNull InputStream stream) throws JDOMException, IOException, IllegalStateException {
    try {
      return new SAXBuilder().build(stream).getRootElement();
    }
    finally {
      stream.close();
    }
  }

  @NotNull
  public static List<String> split(@NotNull String text, char separator) {
    List<String> result = new ArrayList<>();
    int pos = 0;
    int index = text.indexOf(separator, pos);
    while (index >= 0) {
      final int nextPos = index + 1;
      String token = text.substring(pos, index);
      if (token.length() != 0) {
        result.add(token);
      }
      pos = nextPos;
      index = text.indexOf(separator, pos);
    }

    if (pos < text.length()) {
      result.add(text.substring(pos));
    }
    return result;
  }
}
