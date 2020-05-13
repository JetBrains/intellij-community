// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * An input data diff builder that provides direct access to indexed keys
 */
@ApiStatus.OverrideOnly
@ApiStatus.Experimental
public abstract class DirectInputDataDiffBuilder<Key, Value> extends InputDataDiffBuilder<Key, Value> {
  protected DirectInputDataDiffBuilder(int inputId) {
    super(inputId);
  }

  /**
   * @return keys stored for a corresponding {@link InputDataDiffBuilder#myInputId}
   */
  @NotNull
  public abstract Collection<Key> getKeys();
}
