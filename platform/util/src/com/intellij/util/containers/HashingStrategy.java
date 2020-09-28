// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@ApiStatus.Internal
public interface HashingStrategy<T> {
  int hashCode(@Nullable T object);

  boolean equals(@Nullable T o1, @Nullable T o2);

  static <T> HashingStrategy<T> canonical() {
    //noinspection unchecked
    return (HashingStrategy<T>)CanonicalHashingStrategy.INSTANCE;
  }

  static <T> HashingStrategy<T> identity() {
    //noinspection unchecked
    return (HashingStrategy<T>)IdentityHashingStrategy.INSTANCE;
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
