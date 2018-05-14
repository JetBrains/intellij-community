// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.attach.fs;

import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

class LazyAttachVirtualFile extends LightVirtualFile {
  private final String myRemotePath;

  public LazyAttachVirtualFile(String path,
                               String content) {
    super(new File(path).getName(), content);
    myRemotePath = path;

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
    return LazyAttachVirtualFS.getInstance();
  }
}
