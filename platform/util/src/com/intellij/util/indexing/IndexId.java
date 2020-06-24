// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class IndexId<K, V> {
  private static final Map<String, IndexId<?, ?>> ourInstances = new HashMap<>();

  @NotNull
  private final String myName;

  protected IndexId(@NotNull String name) {myName = name;}

  @NotNull
  public final String getName() {
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
