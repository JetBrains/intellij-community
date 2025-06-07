// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class PushedFilePropertiesUpdater {
  public abstract void runConcurrentlyIfPossible(List<? extends Runnable> tasks);

  public static @NotNull PushedFilePropertiesUpdater getInstance(@NotNull Project project) {
    return project.getService(PushedFilePropertiesUpdater.class);
  }

  public abstract void pushAll(final FilePropertyPusher<?>... pushers);

  /**
   * @deprecated Use {@link #filePropertiesChanged(VirtualFile, Condition)}
   */
  @Deprecated
  public abstract void filePropertiesChanged(@NotNull VirtualFile file);

  @RequiresReadLock
  public abstract <T> void findAndUpdateValue(@NotNull VirtualFile fileOrDir, @NotNull FilePropertyPusher<T> pusher, @Nullable T moduleValue);

  /**
   * Invalidates indices and other caches for the given file or its immediate children (in case it's a directory).
   * Only files matching the condition are processed.
   */
  public abstract void filePropertiesChanged(@NotNull VirtualFile fileOrDir, @NotNull Condition<? super VirtualFile> acceptFileCondition);
}
