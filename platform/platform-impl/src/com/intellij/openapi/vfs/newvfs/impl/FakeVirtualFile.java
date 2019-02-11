// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class FakeVirtualFile extends StubVirtualFile {
  private final VirtualFile myParent;
  private final String myName;

  public FakeVirtualFile(@NotNull VirtualFile parent, @NotNull String name) {
    myParent = parent;
    myName = name;
  }

  @NotNull
  @Override
  public VirtualFile getParent() {
    return myParent;
  }

  @Override
  public boolean isDirectory() {
    return false;
  }

  @NotNull
  @Override
  public String getPath() {
    String basePath = myParent.getPath();
    return StringUtil.endsWithChar(basePath, '/') ? basePath + myName : basePath + '/' + myName;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public String toString() {
    return getPath();
  }
}