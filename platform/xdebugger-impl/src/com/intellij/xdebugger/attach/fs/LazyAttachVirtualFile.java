// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.attach.fs;

import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

class LazyAttachVirtualFile extends LightVirtualFile {
  private final String myRemotePath;

  LazyAttachVirtualFile(String path,
                               String content) {
    super(new File(path).getName(), content);
    myRemotePath = path;

    setWritable(false);
  }


  @Override
  public @NotNull String getPath() {
    return myRemotePath;
  }

  @Override
  public @NotNull LazyAttachVirtualFS getFileSystem() {
    return LazyAttachVirtualFS.getInstance();
  }
}
