// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

/**
 * Generic callback with continue/stop semantics.
 *
 * @param <T> Input value type.
 * @see CommonProcessors
 * @see Processors
 */
@FunctionalInterface
public interface Processor<T> {
  /**
   * @param t sequentially each element of the set this processor is passed
   *
   * @return {@code true} to continue processing or {@code false} to stop.
   */
  boolean process(T t);
}