// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class PushedFilePropertiesUpdater {
  public abstract void runConcurrentlyIfPossible(List<? extends Runnable> tasks);

  @NotNull
  public static PushedFilePropertiesUpdater getInstance(@NotNull Project project) {
    return project.getComponent(PushedFilePropertiesUpdater.class);
  }

  public abstract void initializeProperties();
  public abstract void pushAll(final FilePropertyPusher<?>... pushers);

  /**
   * @deprecated Use {@link #filePropertiesChanged(VirtualFile, Condition)}
   */
  @Deprecated
  public abstract void filePropertiesChanged(@NotNull VirtualFile file);
  public abstract void pushAllPropertiesNow();
  public abstract <T> void findAndUpdateValue(@NotNull VirtualFile fileOrDir, @NotNull FilePropertyPusher<T> pusher, @Nullable T moduleValue);

  /**
   * Invalidates indices and other caches for the given file or its immediate children (in case it's a directory).
   * Only files matching the condition are processed.
   */
  public abstract void filePropertiesChanged(@NotNull VirtualFile fileOrDir, @NotNull Condition<? super VirtualFile> acceptFileCondition);
}
