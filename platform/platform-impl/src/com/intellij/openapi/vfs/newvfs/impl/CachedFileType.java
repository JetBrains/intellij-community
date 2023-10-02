// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class CachedFileType {
  private static final ConcurrentMap<FileType, CachedFileType> ourInterner = new ConcurrentHashMap<>();
  private static final ReadWriteLock ourInternerLock = new ReentrantReadWriteLock(/*fair = */ true);

  private @Nullable FileType fileType;

  private CachedFileType(@NotNull FileType fileType) {
    this.fileType = fileType;
  }

  @Nullable FileType getUpToDateOrNull() {
    return fileType;
  }

  static CachedFileType forType(@NotNull FileType fileType) {
    Lock readLock = ourInternerLock.readLock();
    readLock.lock();
    try {
      return ourInterner.computeIfAbsent(fileType, CachedFileType::new);
    } finally {
      readLock.unlock();
    }
  }

  public static void clearCache() {
    Lock writeLock = ourInternerLock.writeLock();
    writeLock.lock();
    try {
      ourInterner.forEach((type, cachedType) -> {
        // clear references to file types to aid plugin unloading
        cachedType.fileType = null;
      });
      ourInterner.clear();
    } finally {
      writeLock.unlock();
    }
  }

  /**
   * @return result that returns true if no files changed their types since method invocation
   */
  public static @NotNull Supplier<@NotNull Boolean> getFileTypeChangeChecker() {
    CachedFileType type = forType(PlainTextFileType.INSTANCE);
    return () -> {
      return type.getUpToDateOrNull() != null;
    };
  }

  static final class PsiListener implements PsiModificationTracker.Listener {
    @Override
    public void modificationCountChanged() {
      clearCache();
    }
  }

  static final class ReparseListener implements BulkFileListener {
    @Override
    public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
      for (VFileEvent event : events) {
        if (FileContentUtilCore.FORCE_RELOAD_REQUESTOR.equals(event.getRequestor())) {
          clearCache();
          break;
        }
      }
    }
  }
}
