// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.uploader.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ExtraHTTPHeadersParser {
  /**
   * Headers are serialized into a string to be passed as a program argument.
   * Key/value pairs are separated by ';', key and value are separated by '='
   * @param data headers string
   * @return empty map if data is empty, null of malformed
   */
  @NotNull
  public static Map<String, String> parse(@Nullable String data) {
    if (data == null) {
      return Collections.emptyMap();
    } else {
      try {
        Map<String, String> res = new LinkedHashMap<>();
        String[] pairs = data.split(";");
        for (String pair : pairs) {
          String[] strings = pair.split("=");
          if (strings.length == 2 && "" != strings[0] && "" != strings[1]) {
            res.put(strings[0], strings[1]);
          }
        }
        return res;
      }
      catch (Exception ignored) {
        return Collections.emptyMap();
      }
    }
  }

  @NotNull
  public static String serialize(@NotNull Map<String, String> headers) {
    StringBuilder stringBuilder = new StringBuilder();
    headers.forEach((k, v) -> {
      stringBuilder.append(k);
      stringBuilder.append('=');
      stringBuilder.append(v);
      stringBuilder.append(';');
    });
    if (!headers.isEmpty())
      stringBuilder.deleteCharAt(stringBuilder.length() - 1);
    return stringBuilder.toString();
  }
}
