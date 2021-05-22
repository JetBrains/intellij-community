// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.ex;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public abstract class FileTypeManagerEx extends FileTypeManager {
  public static FileTypeManagerEx getInstanceEx() {
    return (FileTypeManagerEx)getInstance();
  }

  public abstract void freezeFileTypeTemporarilyIn(@NotNull VirtualFile file, @NotNull Runnable runnable);

  /**
   * @deprecated use {@code com.intellij.fileType} extension point or {@link FileTypeFactory} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  public abstract void registerFileType(@NotNull FileType fileType);

  /**
   * @deprecated use {@code com.intellij.fileType} extension point or {@link FileTypeFactory} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  public abstract void unregisterFileType(@NotNull FileType fileType);

  public abstract boolean isIgnoredFilesListEqualToCurrent(@NotNull String list);

  @NotNull
  public abstract String getExtension(@NotNull String fileName);

  public abstract void fireFileTypesChanged();

  public abstract void fireBeforeFileTypesChanged();
}
