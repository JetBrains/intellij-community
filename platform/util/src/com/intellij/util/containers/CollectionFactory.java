// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.TObjectHashingStrategy;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
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

  public static @NotNull <T> Map<CharSequence, T> createCharSequenceToObjectMap(boolean caseSensitive, int expectedSize, float loadFactor) {
    return new Object2ObjectOpenCustomHashMap<>(expectedSize, loadFactor, caseSensitive ? CharSequenceHashingStrategy.CASE_SENSITIVE : CharSequenceHashingStrategy.CASE_INSENSITIVE);
  }

  public static @NotNull Set<String> createCaseInsensitiveStringSet() {
    return new ObjectOpenCustomHashSet<>(CaseInsensitiveStringHashingStrategy.INSTANCE);
  }

  public static <V> @NotNull Map<String, V> createCaseInsensitiveStringMap() {
    return new Object2ObjectOpenCustomHashMap<>(CaseInsensitiveStringHashingStrategy.INSTANCE);
  }

  public static @NotNull Set<String> createFilePathSet() {
    if (SystemInfoRt.isFileSystemCaseSensitive) {
      return new ObjectOpenHashSet<>();
    }
    else {
      return createCaseInsensitiveStringSet();
    }
  }

  public static @NotNull <V> Map<String, V> createFilePathMap() {
    if (SystemInfoRt.isFileSystemCaseSensitive) {
      return new Object2ObjectOpenHashMap<>();
    }
    else {
      return createCaseInsensitiveStringMap();
    }
  }

  public static @NotNull <V> Map<File, V> createFileMap() {
    return new Object2ObjectOpenCustomHashMap<>(FILE_HASH_STRATEGY);
  }

  public static @NotNull Set<File> createFileSet() {
    return new ObjectOpenCustomHashSet<>(FILE_HASH_STRATEGY);
  }

  public static @NotNull Set<String> createFilePathSet(@NotNull Collection<String> paths) {
    if (SystemInfoRt.isFileSystemCaseSensitive) {
      return new ObjectOpenHashSet<>(paths);
    }
    else {
      Set<String> result = createCaseInsensitiveStringSet();
      result.addAll(paths);
      return result;
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
    return isCaseSensitive ? StringUtil.stringHashCode(o) : StringUtil.stringHashCodeInsensitive(o);
  }

  @Override
  public boolean equals(final CharSequence s1, final CharSequence s2) {
    return Comparing.equal(s1, s2, isCaseSensitive);
  }
}

// must be not exposed to avoid exposing Hash.Strategy interface
final class CaseInsensitiveStringHashingStrategy implements Hash.Strategy<String> {
  static final CaseInsensitiveStringHashingStrategy INSTANCE = new CaseInsensitiveStringHashingStrategy();

  @Override
  public int hashCode(String s) {
    return StringUtil.stringHashCodeInsensitive(s);
  }

  @Override
  public boolean equals(final String s1, final String s2) {
    return s1.equalsIgnoreCase(s2);
  }
}
