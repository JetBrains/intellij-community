// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache.impl;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Page is 'owned' by a storage, and storage needs to be notified about some state transitions
 * happened in Page, and also storage links Page to an appropriate region of a file, which is
 * needed for flush(). This interface abstracts such a relationship.
 */
public interface PageToStorageHandle {
  void pageBecomeDirty();

  void pageBecomeClean();

  /** region [startOffsetInFile, length) of file is modified */
  void modifiedRegionUpdated(final long startOffsetInFile,
                             final int length);

  /** Writes buffer content (between position and limit) into the file at offsetInFile position */
  void flushBytes(final @NotNull ByteBuffer dataToFlush,
                  final long offsetInFile) throws IOException;
}
