// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Url;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public abstract class RemoteContentProvider {
  public abstract boolean canProvideContent(@NotNull Url url);

  public abstract void saveContent(@NotNull Url url, @NotNull File targetFile, @NotNull DownloadingCallback callback);

  public abstract boolean isUpToDate(@NotNull Url url, @NotNull VirtualFile local);


  public interface DownloadingCallback {
    void finished(@Nullable FileType fileType);

    void errorOccurred(@NotNull String errorMessage, boolean cancelled);

    void setProgressText(@NotNull String text, boolean indeterminate);

    void setProgressFraction(double fraction);

    boolean isCancelled();
  }
}
