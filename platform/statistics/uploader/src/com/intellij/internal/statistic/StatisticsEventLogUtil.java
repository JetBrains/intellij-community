// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StatisticsEventLogUtil {
  @NonNls public static final String UTF8 = "UTF-8";

  @NotNull
  public static HttpClient create() {
    return HttpClientBuilder.create().setUserAgent("IntelliJ").build();
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

  @NotNull
  @Contract(pure = true)
  public static List<String> split(@NotNull String s, @NotNull String separator) {
    if (separator.length() == 0) {
      return Collections.singletonList(s);
    }
    List<String> result = new ArrayList<>();
    int pos = 0;
    while (true) {
      int index = s.indexOf(separator, pos);
      //int index = indexOf(s, separator, pos);
      if (index == -1) break;
      final int nextPos = index + separator.length();
      String token = s.substring(pos, index);
      if (token.length() != 0) {
        result.add(token);
      }
      pos = nextPos;
    }
    if (pos < s.length()) {
      result.add(s.substring(pos, s.length()));
    }
    return result;
  }

  public static <T> T[] mergeArrays(@NotNull T[] a1, @NotNull T[] a2) {
    if (a1.length == 0) {
      return a2;
    }
    if (a2.length == 0) {
      return a1;
    }

    final Class<T> class1 = getComponentType(a1);
    final Class<T> class2 = getComponentType(a2);
    final Class<T> aClass = class1.isAssignableFrom(class2) ? class1 : class2;

    T[] result = newArray(aClass, a1.length + a2.length);
    System.arraycopy(a1, 0, result, 0, a1.length);
    System.arraycopy(a2, 0, result, a1.length, a2.length);
    return result;
  }

  @NotNull
  public static <T> T[] newArray(@NotNull Class<T> type, int length) {
    //noinspection unchecked
    return (T[])Array.newInstance(type, length);
  }


  @NotNull
  public static <T> Class<T> getComponentType(@NotNull T[] collection) {
    //noinspection unchecked
    return (Class<T>)collection.getClass().getComponentType();
  }
}
