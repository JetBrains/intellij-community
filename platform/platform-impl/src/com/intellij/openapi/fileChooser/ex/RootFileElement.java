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

import com.intellij.execution.wsl.WSLUtil;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class RootFileElement extends FileElement {
  private List<VirtualFile> myFiles;
  private Object[] myChildren;

  public RootFileElement(@NotNull List<VirtualFile> files, String name, boolean showFileSystemRoots) {
    super(files.size() == 1 ? files.get(0) : null, name);
    myFiles = files.size() == 0 && showFileSystemRoots ? null : files;
  }

  public Object[] getChildren() {
    if (myChildren == null) {
      if (myFiles == null) {
        myFiles = getFileSystemRoots();
      }

      myChildren = myFiles.stream().
        filter(Objects::nonNull).
        map(file -> new FileElement(file, file.getPresentableUrl())).
        toArray();
    }
    return myChildren;
  }

  private static List<VirtualFile> getFileSystemRoots() {
    LocalFileSystem localFileSystem = LocalFileSystem.getInstance();

    StreamEx<String> paths = StreamEx.of(FileSystems.getDefault().getRootDirectories().spliterator()).map(Path::toString);

    if (SystemInfo.isWin10OrNewer && Experiments.getInstance().isFeatureEnabled("wsl.p9.show.roots.in.file.chooser")) {
      paths = paths.append(StreamEx.of(WSLUtil.getExistingUNCRoots()).map(File::getAbsolutePath));
    }
    return paths.map(name -> localFileSystem.findFileByPath(FileUtil.toSystemIndependentName(name))).nonNull().toList();
  }
}
