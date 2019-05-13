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
package com.intellij.openapi.fileChooser.ex;

import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RootFileElement extends FileElement {
  private VirtualFile[] myFiles;
  private Object[] myChildren;

  public RootFileElement(@NotNull VirtualFile[] files, String name, boolean showFileSystemRoots) {
    super(files.length == 1 ? files[0] : null, name);
    myFiles = files.length == 0 && showFileSystemRoots ? null : files;
  }

  public Object[] getChildren() {
    if (myChildren == null) {
      if (myFiles == null) {
        myFiles = getFileSystemRoots();
      }

      List<FileElement> children = new ArrayList<>();
      for (final VirtualFile file : myFiles) {
        if (file != null) {
          children.add(new FileElement(file, file.getPresentableUrl()));
        }
      }
      myChildren = ArrayUtil.toObjectArray(children);
    }
    return myChildren;
  }

  private static VirtualFile[] getFileSystemRoots() {
    final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    final Set<VirtualFile> roots = new HashSet<>();
    final File[] ioRoots = File.listRoots();
    if (ioRoots != null) {
      for (final File root : ioRoots) {
        final String path = FileUtil.toSystemIndependentName(root.getAbsolutePath());
        final VirtualFile file = localFileSystem.findFileByPath(path);
        if (file != null) {
          roots.add(file);
        }
      }
    }
    return VfsUtilCore.toVirtualFileArray(roots);
  }
}
