/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class ChangesBrowserFileNode extends ChangesBrowserNode<VirtualFile> implements Comparable<ChangesBrowserFileNode> {
  private final Project myProject;
  private final String myName;

  public ChangesBrowserFileNode(Project project, @NotNull VirtualFile userObject) {
    super(userObject);
    myName = StringUtil.toLowerCase(userObject.getName());
    myProject = project;
  }

  @Override
  protected boolean isFile() {
    return !getUserObject().isDirectory();
  }

  @Override
  protected boolean isDirectory() {
    return getUserObject().isDirectory() &&
           (isLeaf() || FileStatusManager.getInstance(myProject).getStatus(getUserObject()) != FileStatus.NOT_CHANGED);
  }

  @Override
  public void render(final ChangesBrowserNodeRenderer renderer, final boolean selected, final boolean expanded, final boolean hasFocus) {
    final VirtualFile file = getUserObject();
    FileStatus fileStatus = ChangeListManager.getInstance(myProject).getStatus(file);

    renderer.appendFileName(file, file.getName(), fileStatus.getColor());

    if (renderer.isShowFlatten()) {
      if (file.isValid()) {
        appendParentPath(renderer, file.getParent());
      }
    }

    if (!renderer.isShowFlatten() && getFileCount() != 1 || getDirectoryCount() != 0) {
      appendCount(renderer);
    }

    renderer.setIcon(file.getFileType(), file.isDirectory());
  }

  @Override
  public String getTextPresentation() {
    return getUserObject().getName();
  }

  @Override
  public String toString() {
    return getUserObject().getPresentableUrl();
  }

  public int getSortWeight() {
    return VIRTUAL_FILE_SORT_WEIGHT;
  }

  @Override
  public int compareTo(ChangesBrowserFileNode o) {
    return myName.compareTo(o.myName);
  }

  public int compareUserObjects(final VirtualFile o2) {
    return getUserObject().getName().compareToIgnoreCase(o2.getName());
  }
}
