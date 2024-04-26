// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class FakeVirtualFile extends StubVirtualFile {
  private final VirtualFile myParent;
  private final String myName;

  public FakeVirtualFile(@NotNull VirtualFile parent, @NotNull String name) {
    super(parent.getFileSystem());
    myParent = parent;
    myName = name;
  }

  @Override
  public @NotNull VirtualFile getParent() {
    return myParent;
  }

  @Override
  public boolean isDirectory() {
    return false;
  }

  @Override
  public @NotNull String getPath() {
    String basePath = myParent.getPath();
    return StringUtil.endsWithChar(basePath, '/') ? basePath + myName : basePath + '/' + myName;
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  @Override
  public String toString() {
    return getPath();
  }
}