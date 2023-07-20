// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.Supplier;

/**
 * Deprecated. Use {@link java.util.function.Supplier} instead
 */
@FunctionalInterface
@ApiStatus.Obsolete
public interface Producer<T> extends Supplier<T> {

  T produce();

  @Override
  default T get() {
    return produce();
  }
}
