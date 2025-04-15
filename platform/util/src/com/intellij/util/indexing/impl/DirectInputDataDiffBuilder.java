// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * An input data diff builder that provides direct access to indexed keys
 */
@Internal
public abstract class DirectInputDataDiffBuilder<Key, Value> extends InputDataDiffBuilder<Key, Value> {
  protected DirectInputDataDiffBuilder(int inputId) {
    super(inputId);
  }

  /**
   * @return keys stored for a corresponding {@link InputDataDiffBuilder#myInputId}
   */
  public abstract @NotNull @Unmodifiable Collection<Key> getKeys();
}
