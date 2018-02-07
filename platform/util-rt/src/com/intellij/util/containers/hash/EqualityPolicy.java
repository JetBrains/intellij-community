// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers.hash;

public interface EqualityPolicy<T> {
  EqualityPolicy<?> IDENTITY = new EqualityPolicy() {
    public int getHashCode(Object value) {
      return System.identityHashCode(value);
    }

    public boolean isEqual(Object val1, Object val2) {
      return val1 == val2;
    }
  };

  EqualityPolicy<?> CANONICAL = new EqualityPolicy() {
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