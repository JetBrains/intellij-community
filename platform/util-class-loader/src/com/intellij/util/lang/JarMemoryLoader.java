// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReferenceArray;

public final class JarMemoryLoader {
  // special entry to keep the number of reordered classes in jar
  public static final String SIZE_ENTRY = "META-INF/jb/$$size$$";

  private final AtomicReferenceArray<Object> resources;

  JarMemoryLoader(Object @NotNull [] resources) {
    this.resources = new AtomicReferenceArray<>(resources);
  }

  public Resource getResource(@NotNull String entryName) {
    int i = probe(entryName, resources);
    if (i >= 0) {
      resources.set(i, null);
      return (Resource)resources.getAndSet(i + 1, null);
    }
    return null;
  }

  public byte[] getBytes(@NotNull String entryName) throws IOException {
    int i = probe(entryName, resources);
    if (i >= 0) {
      resources.set(i, null);
      MemoryResource resource = (MemoryResource)resources.getAndSet(i + 1, null);
      return resource == null ? null : resource.getBytes();
    }
    return null;
  }

  private static int probe(Object key, AtomicReferenceArray<Object> table) {
    int length = table.length();
    int index = Math.floorMod(key.hashCode(), length >> 1) << 1;
    while (true) {
      Object foundKey = table.get(index);
      if (foundKey == null) {
        return -index - 1;
      }
      else if (key.equals(foundKey)) {
        return index;
      }
      else if ((index += 2) == length) {
        index = 0;
      }
    }
  }

  // returns index at which the probe key is present; or if absent,
  // (-i - 1) where i is location where element should be inserted.
  @SuppressWarnings("DuplicatedCode")
  public static int probePlain(Object key, Object[] table) {
    int index = Math.floorMod(key.hashCode(), table.length >> 1) << 1;
    while (true) {
      Object foundKey = table[index];
      if (foundKey == null) {
        return -index - 1;
      }
      else if (key.equals(foundKey)) {
        return index;
      }
      else if ((index += 2) == table.length) {
        index = 0;
      }
    }
  }
}
