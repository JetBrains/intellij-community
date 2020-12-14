// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.storage;

import com.intellij.util.io.PagePool;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

final class RecordsTable extends AbstractRecordsTable {
  private static final int VERSION = 1;

  private static final byte[] ZEROES = new byte[DEFAULT_RECORD_SIZE];

  RecordsTable(@NotNull Path storageFilePath, PagePool pool) throws IOException {
    super(storageFilePath, pool);
  }

  @Override
  protected int getImplVersion() {
    return VERSION;
  }

  @Override
  protected int getRecordSize() {
    return DEFAULT_RECORD_SIZE;
  }

  @Override
  protected byte[] getZeros() {
    return ZEROES;
  }

}
