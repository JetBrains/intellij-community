// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TraceableDisposable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.*;

@ApiStatus.Internal
public class VirtualFilePointerImpl extends TraceableDisposable implements VirtualFilePointer {
  private static final Logger LOG = Logger.getInstance(VirtualFilePointerImpl.class);

  private static final boolean TRACE_CREATION = LOG.isDebugEnabled() || ApplicationManager.getApplication().isUnitTestMode();

  volatile FilePartNode myNode; // null means disposed
  private int useCount;
  boolean recursive; // true if the validityChanged() event should be fired for any change under this directory. Used for library jar directories.
  final VirtualFilePointerListener myListener;

  VirtualFilePointerImpl(@Nullable VirtualFilePointerListener listener) {
    super(TRACE_CREATION);
    myListener = listener;
  }

  @ApiStatus.Internal
  public FilePartNode getNode() {
    return myNode;
  }

  @Override
  public @NotNull String getFileName() {
    FilePartNode node = checkDisposed(myNode);
    if (node == null) return "";
    Object result = node.fileOrUrl;
    if (result instanceof VirtualFile) {
      return ((VirtualFile)result).getName();
    }
    String url = (String)result;
    if (node.fs instanceof ArchiveFileSystem) {
      url = ArchiveFileSystem.getLocalPath((ArchiveFileSystem)node.fs, url);
    }
    int index = url.lastIndexOf('/');
    return index >= 0 ? url.substring(index + 1) : url;
  }

  @Override
  public VirtualFile getFile() {
    FilePartNode node = checkDisposed(myNode);
    if (node == null) return null;
    VirtualFile file = FilePartNode.fileOrNull(node.fileOrUrl);
    return (file != null && file.isValid()) ? file : null;
  }

  @Override
  public @NotNull String getUrl() {
    FilePartNode node = myNode;
    if (node == null) return "";
    return FilePartNode.urlOf(node.fileOrUrl);
  }

  @Override
  public @NotNull String getPresentableUrl() {
    return PathUtil.toPresentableUrl(getUrl());
  }

  private FilePartNode checkDisposed(FilePartNode node) {
    if (node == null) {
      ProgressManager.checkCanceled();
      LOG.error("Already disposed: URL='" + this + "'");
    }
    return node;
  }


  @Override
  public boolean isValid() {
    FilePartNode node = myNode;
    return node != null && FilePartNode.fileOrNull(node.fileOrUrl) != null;
  }

  @Override
  public @NonNls String toString() {
    FilePartNode node = myNode;
    return node == null ? "(disposed)" : FilePartNode.urlOf(node.fileOrUrl);
  }

  public void dispose() {
    VirtualFilePointerManager pointerManager = VirtualFilePointerManager.getInstance();
    String url = TRACE_CREATION ? getUrl() : "?";
    boolean shouldKill;
    if (pointerManager instanceof VirtualFilePointerManagerImpl) {
      shouldKill = ((VirtualFilePointerManagerImpl)pointerManager).decrementUsageCount(this);
    }
    else {
      shouldKill = incrementUsageCount(-1) == 0;
    }

    if (shouldKill) {
      kill("URL when die: " + url);
    }
  }

  int incrementUsageCount(int delta) {
    return useCount += delta;
  }

  @VisibleForTesting
  public boolean isRecursive() {
    return recursive;
  }
}
