/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import org.jetbrains.annotations.NotNull;

public class ChangesBrowserGenericNode extends ChangesBrowserNode<Object> implements Comparable<ChangesBrowserGenericNode> {
  @NotNull private final FilePath myFilePath;
  @NotNull private final FileStatus myFileStatus;

  protected ChangesBrowserGenericNode(@NotNull FilePath filePath, @NotNull FileStatus fileStatus, @NotNull Object userObject) {
    super(userObject);
    myFilePath = filePath;
    myFileStatus = fileStatus;
  }

  @Override
  protected boolean isFile() {
    return !isDirectory();
  }

  @Override
  protected boolean isDirectory() {
    return myFilePath.isDirectory();
  }

  @Override
  public void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    renderer.appendFileName(myFilePath.getVirtualFile(), myFilePath.getName(), myFileStatus.getColor());

    if (renderer.isShowFlatten()) {
      FilePath parentPath = myFilePath.getParentPath();
      if (parentPath != null) {
        appendParentPath(renderer, parentPath);
      }
    }

    if (!renderer.isShowFlatten() && getFileCount() != 1 || getDirectoryCount() != 0) {
      appendCount(renderer);
    }

    renderer.setIcon(myFilePath.getFileType(), myFilePath.isDirectory() || !isLeaf());
  }

  public String getTooltip() {
    return null;
  }

  @Override
  public String getTextPresentation() {
    return myFilePath.getName();
  }

  @Override
  public String toString() {
    return FileUtil.toSystemDependentName(myFilePath.getPath());
  }

  public int getSortWeight() {
    return GENERIC_FILE_PATH_SORT_WEIGHT;
  }

  @Override
  public int compareTo(@NotNull ChangesBrowserGenericNode o) {
    return myFilePath.getPath().compareToIgnoreCase(o.myFilePath.getPath());
  }

  public int compareUserObjects(final Object o2) {
    return 0;
  }
}
