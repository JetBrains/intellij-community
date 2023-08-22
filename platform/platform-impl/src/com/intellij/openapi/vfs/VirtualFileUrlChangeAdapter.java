// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import org.jetbrains.annotations.NotNull;

public abstract class VirtualFileUrlChangeAdapter implements VirtualFileListener {
  @Override
  public void fileMoved(@NotNull VirtualFileMoveEvent event) {
    String oldUrl = event.getOldParent().getUrl() + "/" + event.getFileName();
    String newUrl = event.getNewParent().getUrl() + "/" + event.getFileName();
    fileUrlChanged(oldUrl, newUrl);
  }

  protected abstract void fileUrlChanged(String oldUrl, String newUrl);

  @Override
  public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
    if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
      final VirtualFile parent = event.getFile().getParent();
      if (parent != null) {
        final String parentUrl = parent.getUrl();
        fileUrlChanged(parentUrl + "/" + event.getOldValue(), parentUrl + "/" + event.getNewValue());
      }
    }
  }
}
