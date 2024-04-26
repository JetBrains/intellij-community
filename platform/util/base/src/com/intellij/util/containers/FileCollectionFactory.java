// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private interface SerializableHashingStrategy<T> extends Hash.Strategy<T>, Serializable {}

  private static final SerializableHashingStrategy<File> FILE_HASH_STRATEGY = new SerializableHashingStrategy<File>() {
    @Override
    public int hashCode(@Nullable File o) {
      return FileUtilRt.pathHashCode(o == null ? null : o.getPath());
    }

    @Override
    public boolean equals(@Nullable File a, @Nullable File b) {
      return FileUtilRt.pathsEqual(a == null ? null : a.getPath(), b == null ? null : b.getPath());
    }
  };

  /**
   * Create linked map with canonicalized key hash strategy.
   */
  public static @NotNull <V> Map<Path, V> createCanonicalPathLinkedMap() {
    return new Object2ObjectLinkedOpenCustomHashMap<>(new PathSerializableHashStrategy());
  }

  /**
   * Create linked map with canonicalized key hash strategy.
   */
  public static @NotNull <V> Map<String, V> createCanonicalFilePathLinkedMap() {
    return new Object2ObjectLinkedOpenCustomHashMap<>(new Hash.Strategy<String>() {
      @Override
      public int hashCode(@Nullable String value) {
        return FileUtilRt.pathHashCode(value);
      }

      @Override
      public boolean equals(@Nullable String val1, @Nullable String val2) {
        return FileUtilRt.pathsEqual(val1, val2);
      }
    });
  }

  public static @NotNull <V> Map<File, V> createCanonicalFileMap() {
    return new Object2ObjectOpenCustomHashMap<>(FILE_HASH_STRATEGY);
  }

  public static @NotNull <V> Map<File, V> createCanonicalFileMap(int expected) {
    return new Object2ObjectOpenCustomHashMap<>(expected, FILE_HASH_STRATEGY);
  }

  public static @NotNull <V> Map<File, V> createCanonicalFileMap(@NotNull Map<? extends File, ? extends V> map) {
    Map<File, V> result = createCanonicalFileMap(map.size());
    result.putAll(map);
    return result;
  }

  public static @NotNull Set<File> createCanonicalFileSet() {
    return new ObjectOpenCustomHashSet<>(FILE_HASH_STRATEGY);
  }

  public static @NotNull Set<File> createCanonicalFileSet(@NotNull Collection<? extends File> files) {
    Set<File> set = createCanonicalFileSet();
    set.addAll(files);
    return set;
  }

  public static @NotNull Set<Path> createCanonicalPathSet() {
    return new ObjectOpenCustomHashSet<>(new PathSerializableHashStrategy());
  }

  public static @NotNull Set<Path> createCanonicalPathSet(@NotNull Collection<? extends Path> files) {
    return new ObjectOpenCustomHashSet<>(files, new PathSerializableHashStrategy());
  }

  public static @NotNull Set<String> createCanonicalFilePathSet() {
    return new ObjectOpenCustomHashSet<>(FastUtilHashingStrategies.FILE_PATH_HASH_STRATEGY);
  }

  public static @NotNull Set<File> createCanonicalFileLinkedSet() {
    return new ObjectLinkedOpenCustomHashSet<>(new Hash.Strategy<File>() {
      @Override
      public int hashCode(@Nullable File o) {
        return FileUtilRt.pathHashCode(o == null ? null : o.getPath());
      }

      @Override
      public boolean equals(@Nullable File a, @Nullable File b) {
        return FileUtilRt.pathsEqual(a == null ? null : a.getPath(), b == null ? null : b.getPath());
      }
    });
  }

  private static final class PathSerializableHashStrategy implements FastUtilHashingStrategies.SerializableHashStrategy<Path> {
    @Override
    public int hashCode(@Nullable Path o) {
      return FileUtilRt.pathHashCode(o == null ? null : o.toString());
    }

    @Override
    public boolean equals(@Nullable Path a, @Nullable Path b) {
      return FileUtilRt.pathsEqual(a == null ? null : a.toString(), b == null ? null : b.toString());
    }
  }
}
