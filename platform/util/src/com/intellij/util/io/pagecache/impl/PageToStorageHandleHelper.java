// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * For test implementations which usually need to override only 1-2 methods
 */
@TestOnly
public abstract class PageToStorageHandleHelper implements PageToStorageHandle {
  @Override
  public void pageBecomeDirty() {
  }

  @Override
  public void pageBecomeClean() {
  }

  @Override
  public void modifiedRegionUpdated(final long startOffsetInFile,
                                    final int length) {
  }

  @Override
  public void flushBytes(final @NotNull ByteBuffer dataToFlush,
                         final long offsetInFile) throws IOException {
  }
}
