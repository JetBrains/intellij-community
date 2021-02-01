// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.openapi.util.io.FileUtilRt;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenCustomHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Creates map or set with canonicalized path hash strategy.
 */
public final class FileCollectionFactory {
  private interface SerializableHashStrategy<T> extends Hash.Strategy<T>, Serializable {}

  private static final Hash.Strategy<File> FILE_HASH_STRATEGY = new SerializableHashStrategy<File>() {
    @Override
    public int hashCode(@Nullable File o) {
      return FileUtilRt.pathHashCode(o == null ? null : o.getPath());
    }

    @Override
    public boolean equals(@Nullable File a, @Nullable File b) {
      return FileUtilRt.pathsEqual(a == null ? null : a.getPath(), b == null ? null : b.getPath());
    }
  };

  private static final Hash.Strategy<String> FILE_PATH_HASH_STRATEGY = new SerializableHashStrategy<String>() {
    @Override
    public int hashCode(@Nullable String value) {
      return FileUtilRt.pathHashCode(value);
    }

    @Override
    public boolean equals(@Nullable String val1, @Nullable String val2) {
      return FileUtilRt.pathsEqual(val1, val2);
    }
  };

  public static @NotNull <V> Map<String, V> createCanonicalFilePathMap() {
    return new Object2ObjectOpenCustomHashMap<>(FILE_PATH_HASH_STRATEGY);
  }

  /**
   * Create linked map with canonicalized key hash strategy.
   */
  public static @NotNull <V> Map<String, V> createCanonicalFilePathLinkedMap() {
    return new Object2ObjectLinkedOpenCustomHashMap<>(FILE_PATH_HASH_STRATEGY);
  }

  public static @NotNull <V> Map<File, V> createCanonicalFileMap() {
    return new Object2ObjectOpenCustomHashMap<>(FILE_HASH_STRATEGY);
  }

  public static @NotNull <V> Map<File, V> createCanonicalFileMap(@NotNull Map<File, V> map) {
    if (map instanceof Object2ObjectOpenCustomHashMap) {
      Object2ObjectOpenCustomHashMap<File, V> m = (Object2ObjectOpenCustomHashMap<File, V>)map;
      if (m.strategy() == FILE_HASH_STRATEGY) {
        return m.clone();
      }
    }
    return new Object2ObjectOpenCustomHashMap<>(map, FILE_HASH_STRATEGY);
  }

  public static @NotNull Set<File> createCanonicalFileSet() {
    return new ObjectOpenCustomHashSet<>(FILE_HASH_STRATEGY);
  }

  public static @NotNull Set<File> createCanonicalFileSet(@NotNull Collection<? extends File> files) {
    return new ObjectOpenCustomHashSet<>(files, FILE_HASH_STRATEGY);
  }

  public static @NotNull Set<Path> createCanonicalPathSet(@NotNull Collection<? extends Path> files) {
    return new ObjectOpenCustomHashSet<>(files, new SerializableHashStrategy<Path>() {
      @Override
      public int hashCode(@Nullable Path o) {
        return FileUtilRt.pathHashCode(o == null ? null : o.toString());
      }

      @Override
      public boolean equals(@Nullable Path a, @Nullable Path b) {
        return FileUtilRt.pathsEqual(a == null ? null : a.toString(), b == null ? null : b.toString());
      }
    });
  }

  public static @NotNull Set<File> createCanonicalFileLinkedSet() {
    return new ObjectLinkedOpenCustomHashSet<>(FILE_HASH_STRATEGY);
  }
}
