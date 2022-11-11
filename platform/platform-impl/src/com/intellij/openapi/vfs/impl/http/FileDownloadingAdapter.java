// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class FileDownloadingAdapter implements FileDownloadingListener {
  @Override
  public void fileDownloaded(@NotNull final VirtualFile localFile) {
  }

  @Override
  public void errorOccurred(@NotNull final String errorMessage) {
  }

  @Override
  public void downloadingStarted() {
  }

  @Override
  public void downloadingCancelled() {
  }

  @Override
  public void progressMessageChanged(final boolean indeterminate, @NotNull final String message) {
  }

  @Override
  public void progressFractionChanged(final double fraction) {
  }
}
