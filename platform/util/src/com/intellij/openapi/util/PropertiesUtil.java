// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class PropertiesUtil {
  /**
   * Like {@link Properties#load(Reader)}, but preserves the order of key/value pairs.
   */
  @NotNull
  public static Map<String, String> loadProperties(@NotNull Reader reader) throws IOException {
    final Map<String, String> map = new LinkedHashMap<>();

    new Properties() {
      @Override
      public synchronized Object put(Object key, Object value) {
        map.put(String.valueOf(key), String.valueOf(value));
        //noinspection UseOfPropertiesAsHashtable
        return super.put(key, value);
      }
    }.load(reader);

    return map;
  }
}
