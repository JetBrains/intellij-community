// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.FileContentUtilCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ApiStatus.Internal
public final class CachedFileType {
  private static final ConcurrentMap<FileType, CachedFileType> ourInterner = new ConcurrentHashMap<>();

  private @Nullable FileType fileType;

  private CachedFileType(@NotNull FileType fileType) {
    this.fileType = fileType;
  }

  @Nullable FileType getUpToDateOrNull() {
    return fileType;
  }

  static CachedFileType forType(@NotNull FileType fileType) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return ourInterner.computeIfAbsent(fileType, CachedFileType::new);
  }

  public static void clearCache() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    ourInterner.forEach((type, cachedType) -> {
      // clear references to file types to aid plugin unloading
      cachedType.fileType = null;
    });
    ourInterner.clear();
  }

  static final class PsiListener implements PsiModificationTracker.Listener {
    @Override
    public void modificationCountChanged() {
      clearCache();
    }
  }

  static final class ReparseListener implements BulkFileListener {
    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
      for (VFileEvent event : events) {
        if (FileContentUtilCore.FORCE_RELOAD_REQUESTOR.equals(event.getRequestor())) {
          clearCache();
          break;
        }
      }
    }
  }
}
