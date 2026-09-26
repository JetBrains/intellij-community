// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.util.indexing.ValueContainer;
import org.jetbrains.annotations.NotNull;

/** Only difference with {@link com.intellij.util.Processor} is that this interface allows custom exceptions */
@FunctionalInterface
public interface ValueContainerProcessor<V, E extends Exception> {
  /**
   * @return true to continue processing of next container, if available, i.e. processor is ready
   * for more data. false if farther processing is unnecessary, i.e. processor already found what it
   * was looking for
   */
  boolean process(@NotNull ValueContainer<V> container) throws E;
}
