// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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

  @Override
  public int getSortWeight() {
    return VIRTUAL_FILE_SORT_WEIGHT;
  }

  @Override
  public int compareTo(ChangesBrowserFileNode o) {
    return myName.compareTo(o.myName);
  }

  @Override
  public int compareUserObjects(final VirtualFile o2) {
    return getUserObject().getName().compareToIgnoreCase(o2.getName());
  }
}
