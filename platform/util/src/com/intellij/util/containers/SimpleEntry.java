// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

public final class SimpleEntry<V> implements IntObjectMap.Entry<V> {
  private final int myKey;
  private final V myValue;

  public SimpleEntry(int key, @NotNull V value) {
    myKey = key;
    myValue = value;
  }

  @Override
  public int getKey() {
    return myKey;
  }

  @Override
  public @NotNull V getValue() {
    return myValue;
  }
}
