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
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.nio.file.FileSystems;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class RootFileElement extends FileElement {
  private Stream<VirtualFile> myFiles;
  private Object[] myChildren;

  public RootFileElement(@NotNull List<VirtualFile> files, String name, boolean showFileSystemRoots) {
    super(files.size() == 1 ? files.get(0) : null, name);
    myFiles = files.size() == 0 && showFileSystemRoots ? null : files.stream();
  }

  public Object[] getChildren() {
    if (myChildren == null) {
      if (myFiles == null) {
        myFiles = getFileSystemRoots();
      }

      myChildren = myFiles.
        filter(file -> file != null).
        map(file -> new FileElement(file, file.getPresentableUrl())).
        toArray();
    }
    return myChildren;
  }

  private static Stream<VirtualFile> getFileSystemRoots() {
    LocalFileSystem localFileSystem = LocalFileSystem.getInstance();

    return StreamSupport.stream(FileSystems.getDefault().getRootDirectories().spliterator(), false).
      map(root -> localFileSystem.findFileByPath(FileUtil.toSystemIndependentName(root.toString()))).
      filter(file -> file != null);
  }
}
