// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class FileDownloadingAdapter implements FileDownloadingListener {
  @Override
  public void fileDownloaded(final @NotNull VirtualFile localFile) {
  }

  @Override
  public void errorOccurred(final @NotNull String errorMessage) {
  }

  @Override
  public void downloadingStarted() {
  }

  @Override
  public void downloadingCancelled() {
  }

  @Override
  public void progressMessageChanged(final boolean indeterminate, final @NotNull String message) {
  }

  @Override
  public void progressFractionChanged(final double fraction) {
  }
}
