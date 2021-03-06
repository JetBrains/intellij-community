// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

public class SimpleEntry<V> implements IntObjectMap.Entry<V> {
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

  @NotNull
  @Override
  public V getValue() {
    return myValue;
  }
}
