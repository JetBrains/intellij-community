// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class IndexId<K, V> {
  private static final Map<String, IndexId<?, ?>> ourInstances = new HashMap<>();

  private final @NotNull String myName;

  protected IndexId(@NotNull String name) {myName = name;}

  public final @NotNull String getName() {
    return myName;
  }

  /**
   * Consider to use {@link ID#getName()} instead of this method
   */
  @Override
  public String toString() {
    return getName();
  }

  public static <K, V> IndexId<K, V> create(String name) {
    synchronized (ourInstances) {
      @SuppressWarnings("unchecked")
      IndexId<K, V> id = (IndexId<K, V>)ourInstances.get(name);
      if (id == null) {
        ourInstances.put(name, id = new IndexId<>(name));
      }
      return id;
    }
  }
}
