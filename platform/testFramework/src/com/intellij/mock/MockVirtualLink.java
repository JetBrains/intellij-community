// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mock;

import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MockVirtualLink extends MockVirtualFile {
  private final VirtualFile myTarget;

  public MockVirtualLink(@NotNull String name, @Nullable VirtualFile target) {
    super(target != null && target.isDirectory(), name);
    myTarget = target;
  }

  @Override
  public boolean is(@NotNull VFileProperty property) {
    return property == VFileProperty.SYMLINK || super.is(property);
  }

  @Override
  public @Nullable VirtualFile getCanonicalFile() {
    return myTarget;
  }

  @Override
  public @Nullable String getCanonicalPath() {
    return myTarget != null ? myTarget.getPath() : null;
  }

  @Override
  public VirtualFile[] getChildren() {
    // note: the behavior is not exact as in "real" VFS where symlinks have duplicate set of children
    return myTarget != null ? myTarget.getChildren() : EMPTY_ARRAY;
  }

  @Override
  public void addChild(@NotNull MockVirtualFile child) {
    throw new UnsupportedOperationException();
  }
}
