// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

public class InputData<Key, Value> {
  @SuppressWarnings("rawtypes")
  private static final InputData EMPTY = new InputData<>(Collections.emptyMap());

  @SuppressWarnings("unchecked")
  public static <Key, Value> InputData<Key, Value> empty() {
    return EMPTY;
  }

  private final @NotNull Map<Key, Value> myKeyValues;

  protected InputData(@NotNull Map<Key, Value> values) {
    myKeyValues = values;
  }

  public @NotNull Map<Key, Value> getKeyValues() {
    return myKeyValues;
  }
}
