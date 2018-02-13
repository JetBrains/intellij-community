// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.attach.fs;

import com.intellij.testFramework.LightVirtualFile;
import com.intellij.xdebugger.attach.fs.LazyAttachVirtualFS;
import org.jetbrains.annotations.NotNull;

import java.io.*;

class LazyAttachVirtualFile extends LightVirtualFile {
  private final String myRemotePath;
  private final LazyAttachVirtualFS myVirtualFileSystem;

  public LazyAttachVirtualFile(String path,
                               String content,
                               @NotNull LazyAttachVirtualFS virtualFileSystem) {
    super(new File(path).getName());

    setContent(null, content, false);
    myRemotePath = path;
    myVirtualFileSystem = virtualFileSystem;

    setWritable(false);
  }


  @NotNull
  @Override
  public String getPath() {
    return myRemotePath;
  }

  @Override
  @NotNull
  public LazyAttachVirtualFS getFileSystem() {
    return myVirtualFileSystem;
  }
}
