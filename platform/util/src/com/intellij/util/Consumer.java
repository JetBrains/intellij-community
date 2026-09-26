// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.ApiStatus;

/**
 * deprecated Use {@link java.util.function.Consumer} instead
 */
@ApiStatus.Obsolete
@FunctionalInterface
public interface Consumer<T> extends java.util.function.Consumer<T> {
  /**
   * @param t consequently takes value of each element of the set this processor is passed to for processing.
   * t is supposed to be a not-null value. If you need to pass {@code null}s to the consumer use {@link NullableConsumer} instead
   */
  void consume(T t);

  @Override
  default void accept(T t) {
    consume(t);
  }
}