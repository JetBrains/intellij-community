// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.util.indexing.StorageException;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * A class intended to make a diff between existing forward index data and new one.
 */
@Internal
public abstract class InputDataDiffBuilder<Key, Value> {
  protected final int myInputId;

  protected InputDataDiffBuilder(int id) {myInputId = id;}
  /**
   * produce a diff between existing data and newData and consume result to addProcessor, updateProcessor and removeProcessor.
   * @return false if there is no difference and true otherwise
   */
  public abstract boolean differentiate(@NotNull Map<Key, Value> newData,
                                        @NotNull UpdatedEntryProcessor<? super Key, ? super Value> changesProcessor) throws StorageException;
}
