// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentMap;

class CachedFileType {
  private static final ConcurrentMap<FileType, CachedFileType> ourInterner = ContainerUtil.newConcurrentMap();

  private final long modCount = vfsModCount();
  @Nullable private FileType fileType;

  private CachedFileType(@NotNull FileType fileType) {
    this.fileType = fileType;
  }

  @Nullable
  FileType getUpToDateOrNull() {
    return vfsModCount() == modCount ? fileType : null;
  }

  private static int vfsModCount() {
    return PersistentFS.getInstance().getModificationCount();
  }

  static CachedFileType forType(@NotNull FileType fileType) {
    CachedFileType cached = ourInterner.get(fileType);
    return cached != null ? cached : computeSynchronized(fileType);
  }

  private static CachedFileType computeSynchronized(FileType fileType) {
    synchronized (ourInterner) {
      return ourInterner.computeIfAbsent(fileType, CachedFileType::new);
    }
  }

  static {
    Application app = ApplicationManager.getApplication();
    app.getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        project.getMessageBus().connect().subscribe(PsiModificationTracker.TOPIC, CachedFileType::clearCache);
      }
    });
  }

  private static void clearCache() {
    synchronized (ourInterner) {
      for (CachedFileType value : ourInterner.values()) {
        // clear references to file types to aid plugin unloading
        value.fileType = null;
      }
      ourInterner.clear();
    }
  }
}
