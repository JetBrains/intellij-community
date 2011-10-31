/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 *         Date: 10/31/11
 */
public class SymlinkDirectory extends VirtualDirectoryImpl {

  @Nullable
  private final VirtualFile myTarget;

  public SymlinkDirectory(@NotNull String name, final VirtualDirectoryImpl parent, @NotNull NewVirtualFileSystem fs, final int id) {
    super(name, parent, fs, id);
    String path = fs.resolveSymLink(this);
    myTarget = path == null ? null : VfsImplUtil.findFileByPath(fs, path);
  }

  @NotNull
  @Override
  public VirtualFile[] getChildren() {
    return myTarget == null ? VirtualFile.EMPTY_ARRAY : myTarget.getChildren();
  }
}
