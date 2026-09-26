// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.transformer.TextPresentationTransformers;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import org.jetbrains.annotations.NotNull;

/**
 * Read in the read part of a VFS refresh, so the write part does not stall on IO
 */
final class PrefetchedContent {
  private final byte @NotNull [] content;
  private final long expectedModificationStamp;

  PrefetchedContent(byte @NotNull [] content, long expectedModificationStamp) {
    this.content = content;
    this.expectedModificationStamp = expectedModificationStamp;
  }

  long sizeInBytes() {
    return content.length;
  }

  /**
   * A detection would store the charset on the file, and this runs before the change reaches the VFS.
   * The transformer matches {@code LoadTextUtil.loadText}, so the result compares with a document.
   */
  @NotNull CharSequence decodeText(@NotNull VirtualFile file) {
    CharSequence text = LoadTextUtil.getTextByBinaryPresentation(content, file.getCharset());
    return TextPresentationTransformers.fromPersistent(text, file);
  }

  boolean isUpToDate(@NotNull VirtualFile file) {
    return expectedModificationStamp == file.getModificationStamp();
  }

  /**
   * Some clients, local history for one, read the new content from the VFS cache.
   * A false result also means a VFS refusal, so use {@link #isUpToDate} to test the revision.
   */
  boolean cacheInVfs(@NotNull VirtualFile file) {
    if (isUpToDate(file)) {
      return ((PersistentFSImpl)PersistentFS.getInstance()).cacheFileContent(file, content);
    }
    return false;
  }
}
