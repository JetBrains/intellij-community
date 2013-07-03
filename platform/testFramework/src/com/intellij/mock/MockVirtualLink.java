/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  @Nullable
  @Override
  public VirtualFile getCanonicalFile() {
    return myTarget;
  }

  @Nullable
  @Override
  public String getCanonicalPath() {
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
