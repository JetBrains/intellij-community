// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import gnu.trove.TObjectHashingStrategy;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

// ContainerUtil requires trove in classpath
public final class CollectionFactory {
  private static final Hash.Strategy<File> FILE_HASH_STRATEGY = new Hash.Strategy<File>() {
    @Override
    public int hashCode(File o) {
      return FileUtil.fileHashCode(o);
    }

    @Override
    public boolean equals(File a, File b) {
      return FileUtil.filesEqual(a, b);
    }
  };

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<K, V> createConcurrentWeakMap() {
    return new ConcurrentWeakHashMap<>(0.75f);
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<K, V> createConcurrentWeakIdentityMap() {
    //noinspection unchecked
    return new ConcurrentWeakHashMap<>((TObjectHashingStrategy<K>)TObjectHashingStrategy.IDENTITY);
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K, V> Map<K, V> createWeakMap() {
    return ContainerUtil.createWeakMap();
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<K, V> createConcurrentWeakKeyWeakValueMap() {
    return ContainerUtil.createConcurrentWeakKeyWeakValueMap(ContainerUtil.canonicalStrategy());
  }

  @Contract(value = "_,_,_ -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<K, V> createConcurrentWeakMap(int initialCapacity,
                                                                            float loadFactor,
                                                                            int concurrencyLevel) {
    return new ConcurrentWeakHashMap<>(initialCapacity, loadFactor, concurrencyLevel, ContainerUtil.canonicalStrategy());
  }

  public static @NotNull <T> Map<CharSequence, T> createCharSequenceMap(boolean caseSensitive, int expectedSize, float loadFactor) {
    return new Object2ObjectOpenCustomHashMap<>(expectedSize, loadFactor, caseSensitive ? CharSequenceHashingStrategy.CASE_SENSITIVE : CharSequenceHashingStrategy.CASE_INSENSITIVE);
  }

  public static @NotNull Set<CharSequence> createCharSequenceSet(boolean caseSensitive, int expectedSize, float loadFactor) {
    return new ObjectOpenCustomHashSet<>(expectedSize, loadFactor, caseSensitive ? CharSequenceHashingStrategy.CASE_SENSITIVE : CharSequenceHashingStrategy.CASE_INSENSITIVE);
  }

  public static @NotNull <T> Map<CharSequence, T> createCharSequenceMap(boolean caseSensitive) {
    return new Object2ObjectOpenCustomHashMap<>(caseSensitive ? CharSequenceHashingStrategy.CASE_SENSITIVE : CharSequenceHashingStrategy.CASE_INSENSITIVE);
  }

  public static @NotNull Set<String> createCaseInsensitiveStringSet() {
    return new ObjectOpenCustomHashSet<>(CaseInsensitiveStringHashingStrategy.INSTANCE);
  }

  public static <V> @NotNull Map<String, V> createCaseInsensitiveStringMap() {
    return new Object2ObjectOpenCustomHashMap<>(CaseInsensitiveStringHashingStrategy.INSTANCE);
  }

  public static @NotNull Set<String> createFilePathSet() {
    if (SystemInfoRt.isFileSystemCaseSensitive) {
      return new HashSet<>();
    }
    else {
      return createCaseInsensitiveStringSet();
    }
  }

  public static @NotNull Set<String> createFilePathSet(int expectedSize) {
    return createFilePathSet(expectedSize, SystemInfoRt.isFileSystemCaseSensitive);
  }

  public static @NotNull Set<String> createFilePathSet(int expectedSize, boolean isFileSystemCaseSensitive) {
    if (isFileSystemCaseSensitive) {
      return new HashSet<>(expectedSize);
    }
    else {
      return new ObjectOpenCustomHashSet<>(expectedSize, CaseInsensitiveStringHashingStrategy.INSTANCE);
    }
  }

  public static @NotNull Set<String> createFilePathSet(@NotNull Collection<String> paths, boolean isFileSystemCaseSensitive) {
    if (isFileSystemCaseSensitive) {
      return new HashSet<>(paths);
    }
    else {
      return new ObjectOpenCustomHashSet<>(paths, CaseInsensitiveStringHashingStrategy.INSTANCE);
    }
  }

  public static @NotNull <V> Map<String, V> createFilePathMap() {
    if (SystemInfoRt.isFileSystemCaseSensitive) {
      return new HashMap<>();
    }
    else {
      return createCaseInsensitiveStringMap();
    }
  }

  public static @NotNull <V> Map<String, V> createFilePathMap(int expectedSize) {
    return createFilePathMap(expectedSize, SystemInfoRt.isFileSystemCaseSensitive);
  }

  public static @NotNull <V> Map<String, V> createFilePathMap(int expectedSize, boolean isFileSystemCaseSensitive) {
    if (isFileSystemCaseSensitive) {
      return new HashMap<>(expectedSize);
    }
    else {
      return new Object2ObjectOpenCustomHashMap<>(expectedSize, CaseInsensitiveStringHashingStrategy.INSTANCE);
    }
  }

  public static @NotNull <V> Map<File, V> createFileMap() {
    return new Object2ObjectOpenCustomHashMap<>(FILE_HASH_STRATEGY);
  }

  public static @NotNull Set<File> createFileSet() {
    return new ObjectOpenCustomHashSet<>(FILE_HASH_STRATEGY);
  }

  public static @NotNull Set<File> createFileLinkedSet() {
    return new ObjectLinkedOpenCustomHashSet<>(FILE_HASH_STRATEGY);
  }

  public static @NotNull Set<String> createFilePathLinkedSet() {
    if (SystemInfoRt.isFileSystemCaseSensitive) {
      return new ObjectLinkedOpenHashSet<>();
    }
    else {
      return new ObjectLinkedOpenCustomHashSet<>(CaseInsensitiveStringHashingStrategy.INSTANCE);
    }
  }

  /**
   * Create linked map with key hash strategy according to file system path case sensitivity.
   */
  public static @NotNull <V> Map<String, V> createFilePathLinkedMap() {
    if (SystemInfoRt.isFileSystemCaseSensitive) {
      return new Object2ObjectLinkedOpenHashMap<>();
    }
    else {
      return new Object2ObjectLinkedOpenCustomHashMap<>(CaseInsensitiveStringHashingStrategy.INSTANCE);
    }
  }

  /**
   * Create linked map with canonicalized key hash strategy.
   */
  public static @NotNull <V> Map<String, V> createCanonicalFilePathLinkedMap() {
    return new Object2ObjectLinkedOpenCustomHashMap<>(new Hash.Strategy<String>() {
      @Override
      public int hashCode(String value) {
        return FileUtil.pathHashCode(value);
      }

      @Override
      public boolean equals(String val1, String val2) {
        return FileUtil.pathsEqual(val1, val2);
      }
    });
  }

  /**
   * Create map with canonicalized key hash strategy.
   */
  public static @NotNull <V> Map<File, V> createCanonicalFileMap() {
    return new Object2ObjectOpenCustomHashMap<>(new Hash.Strategy<File>() {
      @Override
      public int hashCode(File value) {
        return FileUtil.fileHashCode(value);
      }

      @Override
      public boolean equals(File val1, File val2) {
        return FileUtil.filesEqual(val1, val2);
      }
    });
  }

  public static @NotNull Set<String> createFilePathSet(@NotNull Collection<String> paths) {
    if (SystemInfoRt.isFileSystemCaseSensitive) {
      return new HashSet<>(paths);
    }
    else {
      return new ObjectOpenCustomHashSet<>(paths, CaseInsensitiveStringHashingStrategy.INSTANCE);
    }
  }
}

// must be not exposed to avoid exposing Hash.Strategy interface
final class CharSequenceHashingStrategy implements Hash.Strategy<CharSequence> {
  static final CharSequenceHashingStrategy CASE_SENSITIVE = new CharSequenceHashingStrategy(true);
  static final CharSequenceHashingStrategy CASE_INSENSITIVE = new CharSequenceHashingStrategy(false);

  private final boolean isCaseSensitive;

  private CharSequenceHashingStrategy(boolean caseSensitive) {
    isCaseSensitive = caseSensitive;
  }

  @Override
  public int hashCode(CharSequence o) {
    if (o == null) {
      return 0;
    }
    return isCaseSensitive ? StringUtil.stringHashCode(o) : StringUtil.stringHashCodeInsensitive(o);
  }

  @Override
  public boolean equals(CharSequence s1, CharSequence s2) {
    return StringUtilRt.equal(s1, s2, isCaseSensitive);
  }
}

// must be not exposed to avoid exposing Hash.Strategy interface
final class CaseInsensitiveStringHashingStrategy implements Hash.Strategy<String> {
  static final CaseInsensitiveStringHashingStrategy INSTANCE = new CaseInsensitiveStringHashingStrategy();

  @Override
  public int hashCode(String s) {
    return s == null ? 0 : StringUtil.stringHashCodeInsensitive(s);
  }

  @Override
  public boolean equals(String s1, String s2) {
    return s1 == s2 || (s1 != null && s2 != null && s1.equalsIgnoreCase(s2));
  }
}
