// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import gnu.trove.THashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@ApiStatus.Experimental
public class IndexId<K, V> {
  private static final Map<String, IndexId<?, ?>> ourInstances = new THashMap<String, IndexId<?, ?>>();

  @NotNull
  private final String myName;

  protected IndexId(@NotNull String name) {myName = name;}

  @NotNull
  public final String getName() {
    return myName;
  }

  public static <K, V> IndexId<K, V> create(String name) {
    synchronized (ourInstances) {
      @SuppressWarnings("unchecked")
      IndexId<K, V> id = (IndexId<K, V>)ourInstances.get(name);
      if (id == null) {
        ourInstances.put(name, id = new IndexId<K, V>(name));
      }
      return id;
    }
  }
}
