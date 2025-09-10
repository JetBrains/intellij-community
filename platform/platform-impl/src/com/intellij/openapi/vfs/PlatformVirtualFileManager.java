// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.impl.VirtualFileManagerImpl;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Queue;

@ApiStatus.Internal
public class PlatformVirtualFileManager extends VirtualFileManagerImpl {
  public PlatformVirtualFileManager() {
    super(Collections.emptyList());
  }

  @Override
  protected final long doRefresh(boolean asynchronous, @Nullable Runnable postAction) {
    if (!asynchronous) {
      ApplicationManager.getApplication().assertWriteIntentLockAcquired();
    }

    // todo: get an idea how to deliver changes from local FS to jar fs before they go refresh
    var session = RefreshQueue.getInstance().createSession(asynchronous, true, postAction);
    session.addAllFiles(ManagingFS.getInstance().getRoots());
    session.launch();

    super.doRefresh(asynchronous, postAction);

    return 0;
  }

  @Override
  public final long getModificationCount() {
    return ManagingFS.getInstance().getFilesystemModificationCount();
  }

  @Override
  public final long getStructureModificationCount() {
    return ManagingFS.getInstance().getStructureModificationCount();
  }

  @Override
  public final VirtualFile findFileById(int id) {
    return ManagingFS.getInstance().findFileById(id);
  }

  @Override
  public final int[] listAllChildIds(int id) {
    IntSet result = new IntOpenHashSet();
    Queue<Integer> queue = new ArrayDeque<>();
    queue.add(id);
    FSRecordsImpl vfsPeer = FSRecords.getInstance();
    while (!queue.isEmpty()) {
      int recordId = queue.poll();
      if (result.add(recordId)) {
        ProgressManager.checkCanceled();
        vfsPeer.forEachChildOf(recordId, childId -> {
          queue.add(childId);
          return false;
        });
      }
    }
    return result.toIntArray();
  }

  @Override
  public final @NotNull CharSequence getVFileName(int nameId) {
    return FSRecords.getInstance().getNameByNameId(nameId);
  }

  @Override
  public final int storeName(@NotNull String name) {
    return FSRecords.getInstance().getNameId(name);
  }
}
