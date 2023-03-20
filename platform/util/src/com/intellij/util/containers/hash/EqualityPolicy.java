// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.hash;

import java.util.Objects;

public interface EqualityPolicy<T> {
  EqualityPolicy<?> IDENTITY = new EqualityPolicy<Object>() {
    @Override
    public int getHashCode(Object value) {
      return System.identityHashCode(value);
    }

    @Override
    public boolean isEqual(Object val1, Object val2) {
      return val1 == val2;
    }
  };

  EqualityPolicy<?> CANONICAL = new EqualityPolicy<Object>() {
    @Override
    public int getHashCode(Object value) {
      return value != null ? value.hashCode() : 0;
    }

    @Override
    public boolean isEqual(Object val1, Object val2) {
      return Objects.equals(val1, val2);
    }
  };

  int getHashCode(T value);

  boolean isEqual(T val1, T val2);
}