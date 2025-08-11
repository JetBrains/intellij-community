// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.ChangesTreeCompatibilityProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.vcs.VcsUtil;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChangesBrowserFileNode extends ChangesBrowserNode<VirtualFile> implements Comparable<ChangesBrowserFileNode> {
  private final @Nullable Project myProject;
  private final String myName;

  public ChangesBrowserFileNode(@Nullable Project project, @NotNull VirtualFile userObject) {
    super(userObject);
    myName = userObject.getName();
    myProject = project;
  }

  @Override
  protected boolean isFile() {
    return !getUserObject().isDirectory();
  }

  @Override
  protected boolean isDirectory() {
    if (getUserObject().isDirectory()) {
      return isLeaf() || getFileStatus() != FileStatus.NOT_CHANGED;
    }
    return false;
  }

  @Override
  public void render(final @NotNull ChangesBrowserNodeRenderer renderer, final boolean selected, final boolean expanded, final boolean hasFocus) {
    final VirtualFile file = getUserObject();
    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-322065, EA-857522")) {
      FileStatus fileStatus = getFileStatus();
      renderer.appendFileName(file, file.getName(), fileStatus.getColor());
    }

    if (renderer.isShowFlatten()) {
      if (file.isValid()) {
        appendParentPath(renderer, file.getParent());
      }
    }

    if (!renderer.isShowFlatten() && getFileCount() != 1 || getDirectoryCount() != 0) {
      appendCount(renderer);
    }

    renderer.setIcon(VcsUtil.getFilePath(file), file.isDirectory());
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
    return compareFileNames(myName, o.myName);
  }

  private @NotNull FileStatus getFileStatus() {
    if (myProject == null || myProject.isDisposed()) return FileStatus.NOT_CHANGED;
    return ChangesTreeCompatibilityProvider.getInstance().getFileStatus(myProject, getUserObject());
  }
}
