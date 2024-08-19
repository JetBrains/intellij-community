// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.util.indexing.StorageException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public abstract class AbstractUpdateData<Key, Value> {
  private final int myInputId;

  protected AbstractUpdateData(int id) {myInputId = id;}

  //TODO RC: iterateDifferences() is more clear name?..
  protected abstract boolean iterateKeys(@NotNull KeyValueUpdateProcessor<? super Key, ? super Value> addProcessor,
                                         @NotNull KeyValueUpdateProcessor<? super Key, ? super Value> updateProcessor,
                                         @NotNull RemovedKeyProcessor<? super Key> removeProcessor) throws StorageException;

  public int getInputId() {
    return myInputId;
  }

  protected void updateForwardIndex() throws IOException { }

}
