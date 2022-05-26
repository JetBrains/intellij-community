// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.ApiStatus;

/**
 * Deprecated. Use {@link java.util.function.Consumer} instead
 */
public interface Consumer<T> {
  /**
   * @deprecated use {@link com.intellij.util.EmptyConsumer#getInstance()} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  Consumer<Object> EMPTY_CONSUMER = new Consumer<Object>() {
    public void consume(Object t) { }
  };

  /**
   * @param t consequently takes value of each element of the set this processor is passed to for processing.
   * t is supposed to be a not-null value. If you need to pass {@code null}s to the consumer use {@link NullableConsumer} instead
   */
  void consume(T t);
}