// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.PersistentFSConstants;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.limits.FileSizeLimit;
import com.intellij.openapi.vfs.newvfs.FileSystemInterface;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One instance serves one VFS event batch, so the content of one batch never reaches another batch
 */
final class ContentPrefetcher {
  private static final Logger LOG = Logger.getInstance(ContentPrefetcher.class);

  /**
   * Without the limit a large batch, such as a branch switch, holds every changed file in memory
   */
  private static final long MAX_PREFETCHED_CONTENT_BYTES = 50L * FileUtilRt.MEGABYTE;

  private final FileDocumentManager fileDocumentManager;
  private final Map<VirtualFile, PrefetchedContent> prefetchedContents = new HashMap<>();
  private long prefetchBudget = MAX_PREFETCHED_CONTENT_BYTES;

  ContentPrefetcher(@NotNull FileDocumentManager fileDocumentManager) {
    this.fileDocumentManager = fileDocumentManager;
  }

  @RequiresReadLock
  @Nullable PrefetchedContent prefetch(@NotNull VFileContentChangeEvent event, @NotNull List<VirtualFile> toRecompute) {
    PrefetchedContent prefetched = readContent(event, toRecompute);
    if (prefetched != null) {
      prefetchedContents.put(event.getFile(), prefetched);
      prefetchBudget -= prefetched.sizeInBytes();
    }
    return prefetched;
  }

  @Nullable PrefetchedContent getPrefetched(@NotNull VirtualFile file) {
    return prefetchedContents.get(file);
  }

  /**
   * Reads through the file system, because the VFS content cache still holds the old content at this point
   */
  private @Nullable PrefetchedContent readContent(@NotNull VFileContentChangeEvent event, @NotNull List<VirtualFile> toRecompute) {
    if (prefetchBudget <= 0 || event.isFromSave()) {
      return null;
    }
    if (!event.isFromRefresh()) {
      // a write through VfsUtil.saveText changes the file only after the `before` events fire.
      // the disk still holds the old content here, so a preload is meaningless
      return null;
    }
    VirtualFile file = event.getFile();
    Document document = fileDocumentManager.getCachedDocument(file);
    if (document == null) {
      // document is not strongly reachable; no need to read
      return null;
    }
    if (toRecompute.contains(file)) {
      // the file type changes together with the content, so the reload path is still unknown
      return null;
    }
    if (file.getFileType().isBinary()) {
      // a decompiler loads its own text; we shall decompile asynchronously later
      return null;
    }
    // the event carries UNDEFINED_TIMESTAMP_OR_LENGTH when it doesn't know the new length
    long newLength = event.getNewLength();
    boolean newLengthKnown = newLength != VFileContentChangeEvent.UNDEFINED_TIMESTAMP_OR_LENGTH;
    long expectedLength = newLengthKnown ? newLength : file.getLength();
    if (expectedLength > prefetchBudget) {
      return null;
    }
    if (FileSizeLimit.isTooLargeForContentLoading(expectedLength, file.getExtension())) {
      return null;
    }
    if (expectedLength > PersistentFSConstants.MAX_FILE_LENGTH_TO_CACHE) {
      return null;
    }
    if (!(file.getFileSystem() instanceof FileSystemInterface fileSystem)) {
      return null;
    }
    try {
      byte[] content = fileSystem.contentsToByteArray(file);
      if (newLengthKnown && content.length != newLength) {
        // the file changed again after the event appeared; let the write action read it
        return null;
      }
      return new PrefetchedContent(content, event.getModificationStamp());
    }
    catch (IOException e) {
      LOG.debug(e);
      return null;
    }
  }
}
