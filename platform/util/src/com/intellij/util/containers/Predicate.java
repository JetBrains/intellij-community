// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

/**
 * @deprecated Use {@link java.util.function.Predicate} instead
 */
@Deprecated
@FunctionalInterface
public interface Predicate<T> extends java.util.function.Predicate<T> {
  boolean apply(T input);

  @Override
  default boolean test(T t) {
    return apply(t);
  }
}
