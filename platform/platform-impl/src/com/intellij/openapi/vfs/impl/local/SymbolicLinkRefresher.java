// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.intellij.openapi.vfs.VirtualFile.PROP_NAME;
import static com.intellij.util.ObjectUtils.doIfNotNull;

class SymbolicLinkRefresher {

  private final ScheduledExecutorService myExecutor = AppExecutorUtil.createBoundedScheduledExecutorService(
    "File SymbolicLinkRefresher", 1);

  private static final int REFRESH_DELAY = 10;

  private final Object myLock = new Object();
  private Set<String> myRefreshQueue = new HashSet<>();
  private ScheduledFuture<?> myScheduledFuture;
  private final LocalFileSystemImpl mySystem;

  SymbolicLinkRefresher(LocalFileSystemImpl system) {
    mySystem = system;
    ApplicationManager.getApplication().getMessageBus().connect(system).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        analyzeEvents(events);
      }
    });
  }

  private void analyzeEvents(@NotNull List<? extends VFileEvent> events) {
    if (events.isEmpty()) {
      return;
    }
    Set<String> toRefresh = new HashSet<>();
    FileWatcher fileWatcher = mySystem.getFileWatcher();

    Consumer<String> queuePath = path -> toRefresh.addAll(fileWatcher.mapToAllSymlinks(FileUtil.toSystemDependentName(path)));
    Consumer<VirtualFile> queueFile = file -> {
      if (file instanceof VirtualFileSystemEntry) {
        if (((VirtualFileSystemEntry)file).hasSymlink() && !isUnderRecursiveOrCircularSymlink(file)) {
          file = doIfNotNull(file.getCanonicalPath(), mySystem::findFileByPathIfCached);
          if (file != null && fileWatcher.belongsToWatchRoots(FileUtil.toSystemDependentName(file.getPath()), !file.isDirectory())) {
            toRefresh.add(file.getPath());
          }
        }
        else {
          queuePath.accept(file.getPath());
        }
      }
    };

    for (VFileEvent event : events) {
      if (event.isFromRefresh() || event.getFileSystem() != mySystem) {
        continue;
      }
      if (event instanceof VFileContentChangeEvent
          || event instanceof VFileDeleteEvent) {
        queueFile.accept(event.getFile());
      }
      else if (event instanceof VFilePropertyChangeEvent) {
        VirtualFile file = ((VFilePropertyChangeEvent)event).getFile();
        if (((VFilePropertyChangeEvent)event).getPropertyName().equals(PROP_NAME)) {
          queuePath.accept(((VFilePropertyChangeEvent)event).getOldPath());
          queueFile.accept(file.getParent());
        }
        else {
          queueFile.accept(file);
        }
      }
      else if (event instanceof VFileCreateEvent) {
        queueFile.accept(((VFileCreateEvent)event).getParent());
      }
      else if (event instanceof VFileCopyEvent) {
        queueFile.accept(((VFileCopyEvent)event).getNewParent());
      }
      else if (event instanceof VFileMoveEvent) {
        queueFile.accept(event.getFile());
        queueFile.accept(((VFileMoveEvent)event).getNewParent());
      }
    }
    if (!toRefresh.isEmpty()) {
      scheduleRefresh(toRefresh);
    }
  }

  private void scheduleRefresh(Set<String> toRefresh) {
    synchronized (myLock) {
      myRefreshQueue.addAll(toRefresh);
      if (myScheduledFuture == null) {
        myScheduledFuture = myExecutor.schedule(this::performRefresh, REFRESH_DELAY, TimeUnit.MILLISECONDS);
      }
    }
  }

  private void performRefresh() {
    Set<String> toRefresh;
    synchronized (myLock) {
      toRefresh = myRefreshQueue;
      myRefreshQueue = new HashSet<>();
      myScheduledFuture = null;
    }
    List<VirtualFile> files = ContainerUtil.mapNotNull(toRefresh, mySystem::findFileByPath);
    RefreshQueue.getInstance().refresh(false, false, null, files);
  }

  private static boolean isUnderRecursiveOrCircularSymlink(@NotNull VirtualFile file) {
    if (((VirtualFileSystemEntry)file).hasSymlink()) {
      while (file != null && !file.is(VFileProperty.SYMLINK)) {
        file = file.getParent();
      }
      return file != null && file.isRecursiveOrCircularSymlink();
    }
    return false;
  }
}
