// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@ApiStatus.Internal
public interface HashingStrategy<T> {
  int hashCode(T object);

  boolean equals(T o1, T o2);

  static <T> @NotNull HashingStrategy<T> canonical() {
    //noinspection unchecked
    return (HashingStrategy<T>)CanonicalHashingStrategy.INSTANCE;
  }

  static <T> @NotNull HashingStrategy<T> identity() {
    //noinspection unchecked
    return (HashingStrategy<T>)IdentityHashingStrategy.INSTANCE;
  }

  static @NotNull HashingStrategy<String> caseInsensitive() {
    return CaseInsensitiveStringHashingStrategy.INSTANCE;
  }
  static @NotNull HashingStrategy<CharSequence> caseInsensitiveCharSequence() {
    return CaseInsensitiveCharSequenceHashingStrategy.INSTANCE;
  }
}

final class CaseInsensitiveCharSequenceHashingStrategy implements HashingStrategy<CharSequence> {
  static final CaseInsensitiveCharSequenceHashingStrategy INSTANCE = new CaseInsensitiveCharSequenceHashingStrategy();

  @Override
  public int hashCode(CharSequence object) {
    return Strings.stringHashCodeInsensitive(object);
  }

  @Override
  public boolean equals(CharSequence s1, CharSequence s2) {
    return StringUtilRt.equal(s1, s2, false);
  }
}

final class CanonicalHashingStrategy<T> implements HashingStrategy<T> {
  static final HashingStrategy<?> INSTANCE = new CanonicalHashingStrategy<>();

  @Override
  public int hashCode(T value) {
    return Objects.hashCode(value);
  }

  @Override
  public boolean equals(T o1, T o2) {
    return Objects.equals(o1, o2);
  }
}

final class IdentityHashingStrategy<T> implements HashingStrategy<T> {
  static final HashingStrategy<?> INSTANCE = new IdentityHashingStrategy<>();

  @Override
  public int hashCode(T value) {
    return System.identityHashCode(value);
  }

  @Override
  public boolean equals(T o1, T o2) {
    return o1 == o2;
  }
}

final class CaseInsensitiveStringHashingStrategy implements HashingStrategy<String> {
  public static final HashingStrategy<String> INSTANCE = new CaseInsensitiveStringHashingStrategy();

  @Override
  public int hashCode(String s) {
    return Strings.stringHashCodeInsensitive(s);
  }

  @Override
  public boolean equals(String s1, String s2) {
    assert s1 != null;
    return s1.equalsIgnoreCase(s2);
  }
}