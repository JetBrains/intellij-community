// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.hash;

public interface EqualityPolicy<T> {
  EqualityPolicy<?> IDENTITY = new EqualityPolicy<Object>() {
    public int getHashCode(Object value) {
      return System.identityHashCode(value);
    }

    public boolean isEqual(Object val1, Object val2) {
      return val1 == val2;
    }
  };

  EqualityPolicy<?> CANONICAL = new EqualityPolicy<Object>() {
    public int getHashCode(Object value) {
      return value != null ? value.hashCode() : 0;
    }

    public boolean isEqual(Object val1, Object val2) {
      return val1 != null ? val1.equals(val2) : val2 == null;
    }
  };

  int getHashCode(T value);

  boolean isEqual(T val1, T val2);
}