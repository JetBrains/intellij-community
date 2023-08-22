// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors.impl;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

final class Option {
  private final Map<String, String> myMap = new HashMap<>();

  Option(String value) {
    if (value != null) {
      myMap.put("value", value);
    }
  }

  Option(String name, String value) {
    this(value);
    if (name != null) {
      myMap.put("name", name);
    }
  }

  Option os(@NotNull String value) {
    myMap.put("windows", value);
    myMap.put("linux", value);
    myMap.put("mac", value);
    return this;
  }

  Option put(@NotNull String key, @NotNull String value) {
    myMap.put(key, value);
    return this;
  }

  @NotNull
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("<option");
    for (Map.Entry<String, String> entry : myMap.entrySet()) {
      sb.append(' ').append(entry.getKey()).append('=');
      sb.append('"').append(entry.getValue()).append('"');
    }
    return sb.append("/>").toString();
  }
}
