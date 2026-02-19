// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.indexing.impl;

import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 */
@ApiStatus.Internal
public abstract class UpdatableValueContainer<T> extends ValueContainer<T> {

  public abstract void addValue(int inputId, T value);

  /**
   * Removes inputId from the value it is associated with, if any.
   * (It must be at most 1 value associated with particular inputId)
   *
   * @return true if inputId was actually removed (i.e. anything was changed) as a result
   */
  public abstract boolean removeAssociatedValue(int inputId);

  private volatile boolean needsCompacting;

  public boolean needsCompacting() {
    return needsCompacting;
  }

  public void setNeedsCompacting(boolean value) {
    needsCompacting = value;
  }

  //TODO RC: why saveTo() is a method of UpdatableValueContainer, and not general ValueContainer?
  public abstract void saveTo(@NotNull DataOutput out,
                              @NotNull DataExternalizer<? super T> externalizer) throws IOException;
}
