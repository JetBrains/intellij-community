// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.indexing.impl;

import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.io.DataExternalizer;

import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 */
public abstract class UpdatableValueContainer<T> extends ValueContainer<T> {

  public abstract void addValue(int inputId, T value);

  /**
   * Removes inputId from the value it is associated with (if any).
   * TODO RC: it is assumed if may be at most 1 value associated with particular inputId -- why?
   *
   * @return true if inputId was actually removed (i.e. anything was changed) as a result
   */
  public abstract boolean removeAssociatedValue(int inputId);

  private volatile boolean myNeedsCompacting;

  boolean needsCompacting() {
    return myNeedsCompacting;
  }

  void setNeedsCompacting(boolean value) {
    myNeedsCompacting = value;
  }

  public abstract void saveTo(DataOutput out, DataExternalizer<? super T> externalizer) throws IOException;
}
