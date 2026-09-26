// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import java.util.function.Predicate;

/**
 * @deprecated Use {@link java.util.function.Predicate} instead
 */
@Deprecated
@FunctionalInterface
public interface BooleanFunction<S> extends Predicate<S> {
    boolean fun(S s);

  @Override
  default boolean test(S s) {
    return fun(s);
  }
}