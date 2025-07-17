// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.util.text.Strings;
import it.unimi.dsi.fastutil.Hash;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Objects;

// must be not exposed to avoid exposing Hash.Strategy interface
@ApiStatus.Internal
public final class FastUtilHashingStrategies {
  interface SerializableHashStrategy<T> extends Hash.Strategy<T>, Serializable {}

  private static final Hash.Strategy<CharSequence> CASE_SENSITIVE = new FastUtilCharSequenceHashingStrategy(true);
  private static final Hash.Strategy<CharSequence> CASE_INSENSITIVE = new FastUtilCharSequenceHashingStrategy(false);

  private FastUtilHashingStrategies() {
  }

  public static final Hash.Strategy<String> FILE_PATH_HASH_STRATEGY = new Hash.Strategy<String>() {
    @Override
    public int hashCode(@Nullable String o) {
      return FileUtilRt.pathHashCode(o);
    }

    @Override
    public boolean equals(@Nullable String p1, @Nullable String p2) {
      return FileUtilRt.pathsEqual(p1, p2);
    }
  };

  public static @NotNull Hash.Strategy<CharSequence> getCharSequenceStrategy(boolean isCaseSensitive) {
    return isCaseSensitive ? CASE_SENSITIVE : CASE_INSENSITIVE;
  }

  public static @NotNull Hash.Strategy<String> getStringStrategy(boolean isCaseSensitive) {
    return isCaseSensitive ? getCanonicalStrategy() : FastUtilCaseInsensitiveStringHashingStrategy.INSTANCE;
  }

  public static @NotNull Hash.Strategy<String> getCaseInsensitiveStringStrategy() {
    return FastUtilCaseInsensitiveStringHashingStrategy.INSTANCE;
  }

  public static @NotNull <T> Hash.Strategy<T> getCanonicalStrategy() {
    //noinspection unchecked
    return (Hash.Strategy<T>)CanonicalObjectStrategy.INSTANCE;
  }

  public static @NotNull <T> Hash.Strategy<T> adaptAsNotNull(@NotNull HashingStrategy<? super T> hashingStrategy) {
    return new Hash.Strategy<T>() {
      @Override
      public int hashCode(@Nullable T o) {
        return o == null ? 0 : hashingStrategy.hashCode(o);
      }

      @Override
      public boolean equals(@Nullable T a, @Nullable T b) {
        return a == b || (a != null && b != null && hashingStrategy.equals(a, b));
      }
    };
  }
}

final class FastUtilCaseInsensitiveStringHashingStrategy implements Hash.Strategy<String> {
  static final Hash.Strategy<String> INSTANCE = new FastUtilCaseInsensitiveStringHashingStrategy();

  @Override
  public int hashCode(String s) {
    return s == null ? 0 : StringUtilRt.stringHashCodeInsensitive(s);
  }

  @Override
  public boolean equals(String s1, String s2) {
    // s1==s2 is exessive here: equalsIgnoreCase checks instance equality by itself
    return (s1 != null && s1.equalsIgnoreCase(s2));
  }
}

final class FastUtilCharSequenceHashingStrategy implements Hash.Strategy<CharSequence> {
  private final boolean isCaseSensitive;

  FastUtilCharSequenceHashingStrategy(boolean caseSensitive) {
    isCaseSensitive = caseSensitive;
  }

  @Override
  public int hashCode(CharSequence o) {
    if (o == null) {
      return 0;
    }
    return isCaseSensitive ? Strings.stringHashCode(o) : Strings.stringHashCodeInsensitive(o);
  }

  @Override
  public boolean equals(CharSequence s1, CharSequence s2) {
    return StringUtilRt.equal(s1, s2, isCaseSensitive);
  }
}

final class CanonicalObjectStrategy<T> implements Hash.Strategy<T> {
  static final Hash.Strategy<Object> INSTANCE = new CanonicalObjectStrategy<>();

  @Override
  public int hashCode(@Nullable T o) {
    return Objects.hashCode(o);
  }

  @Override
  public boolean equals(@Nullable T a, @Nullable T b) {
    return Objects.equals(a, b);
  }
}
