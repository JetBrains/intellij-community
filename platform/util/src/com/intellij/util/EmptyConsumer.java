// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

/**
 * use {@link java.util.function.Consumer} instead
 */
@Deprecated
public final class EmptyConsumer {
  private static final Consumer<Object> EMPTY_CONSUMER = t -> {};

  public static <T> Consumer<T> getInstance() {
    // noinspection unchecked
    return (Consumer<T>)EMPTY_CONSUMER;
  }
}
