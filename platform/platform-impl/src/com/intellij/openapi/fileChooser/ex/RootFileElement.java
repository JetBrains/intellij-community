/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class RootFileElement extends FileElement {
  private final VirtualFile[] myFiles;
  private final boolean myShowFileSystemRoots;
  private Object[] myChildren;

  public RootFileElement(VirtualFile[] files, String name, boolean showFileSystemRoots) {
    super(files.length == 1 ? files[0] : null, name);
    myFiles = files;
    myShowFileSystemRoots = showFileSystemRoots;
  }

  public Object[] getChildren() {
    if (myFiles.length <= 1 && myShowFileSystemRoots) {
      return getFileSystemRoots();
    }
    if (myChildren == null) {
      myChildren = createFileElementArray();
    }
    return myChildren;
  }

  private Object[] createFileElementArray() {
    final List<FileElement> roots = new ArrayList<FileElement>();
    for (final VirtualFile file : myFiles) {
      if (file != null) {
        roots.add(new FileElement(file, file.getPresentableUrl()));
      }
    }
    return ArrayUtil.toObjectArray(roots);
  }

  private static Object[] getFileSystemRoots() {
    File[] roots = File.listRoots();
    LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    HashSet<FileElement> rootChildren = new HashSet<FileElement>();
    for (File root : roots) {
      String path = root.getAbsolutePath();
      path = path.replace(File.separatorChar, '/');
      VirtualFile file = localFileSystem.findFileByPath(path);
      if (file == null) continue;
      rootChildren.add(new FileElement(file, file.getPresentableUrl()));
    }
    return ArrayUtil.toObjectArray(rootChildren);
  }
}
