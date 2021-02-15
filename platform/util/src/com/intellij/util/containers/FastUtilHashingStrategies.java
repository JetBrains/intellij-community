// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import it.unimi.dsi.fastutil.Hash;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

// must be not exposed to avoid exposing Hash.Strategy interface
@ApiStatus.Internal
public final class FastUtilHashingStrategies implements Hash.Strategy<CharSequence> {
  private static final Hash.Strategy<CharSequence> CASE_SENSITIVE = new FastUtilHashingStrategies(true);
  private static final Hash.Strategy<CharSequence> CASE_INSENSITIVE = new FastUtilHashingStrategies(false);

  private final boolean isCaseSensitive;

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

  private FastUtilHashingStrategies(boolean caseSensitive) {
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

final class FastUtilCaseInsensitiveStringHashingStrategy implements Hash.Strategy<String> {
  static final Hash.Strategy<String> INSTANCE = new FastUtilCaseInsensitiveStringHashingStrategy();

  @Override
  public int hashCode(String s) {
    return s == null ? 0 : StringUtil.stringHashCodeInsensitive(s);
  }

  @Override
  public boolean equals(String s1, String s2) {
    return s1 == s2 || (s1 != null && s1.equalsIgnoreCase(s2));
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
