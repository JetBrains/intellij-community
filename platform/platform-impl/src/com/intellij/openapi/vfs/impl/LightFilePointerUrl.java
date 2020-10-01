// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.workspaceModel.storage.url.VirtualFileUrl;
import org.jetbrains.annotations.NotNull;

public class LightFilePointerUrl extends LightFilePointer implements VirtualFileUrl {
  public LightFilePointerUrl(@NotNull String url) {
    super(url);
  }

  public LightFilePointerUrl(@NotNull VirtualFile file) {
    super(file);
  }
}
