// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesTreeCompatibilityProvider;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.vcs.changes.ChangesUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.util.FontUtil.spaceAndThinSpace;

public class ChangesBrowserChangeNode extends ChangesBrowserNode<Change> implements TreeLinkMouseListener.HaveTooltip {

  private final @Nullable Project myProject;
  private final @Nullable ChangeNodeDecorator myDecorator;
  private final @Nullable @Nls String myAdditionalText;

  public ChangesBrowserChangeNode(@Nullable Project project, @NotNull Change userObject, @Nullable ChangeNodeDecorator decorator) {
    super(userObject);
    myProject = project;
    myDecorator = decorator;
    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-318216, EA-831659")) {
      myAdditionalText = getUserObject().getOriginText(project);
    }
  }

  @Override
  protected boolean isFile() {
    return !isDirectory();
  }

  @Override
  protected boolean isDirectory() {
    return ChangesUtil.getFilePath(getUserObject()).isDirectory();
  }

  @Override
  public void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    ReadAction.run(() -> {
      Change change = getUserObject();
      FilePath filePath = ChangesUtil.getFilePath(change);
      VirtualFile file = filePath.getVirtualFile();

      if (myDecorator != null) {
        myDecorator.preDecorate(change, renderer, renderer.isShowFlatten());
      }

      renderer.appendFileName(file, filePath.getName(), change.getFileStatus().getColor());

      if (myAdditionalText != null) {
        renderer.append(spaceAndThinSpace() + myAdditionalText, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }

      if (renderer.isShowFlatten()) {
        appendParentPath(renderer, filePath.getParentPath());
      }

      appendSwitched(renderer, file);

      if (!renderer.isShowFlatten() && getFileCount() != 1 || getDirectoryCount() != 0) {
        appendCount(renderer);
      }

      setIcon(change, filePath, renderer);

      if (myDecorator != null) {
        myDecorator.decorate(change, renderer, renderer.isShowFlatten());
      }
    });
  }

  private void setIcon(@NotNull Change change, @NotNull FilePath filePath, @NotNull ChangesBrowserNodeRenderer renderer) {
    Icon additionalIcon = change.getAdditionalIcon();
    if (additionalIcon != null) {
      renderer.setIcon(additionalIcon);
      return;
    }
    renderer.setIcon(filePath, filePath.isDirectory() || !isLeaf());
  }

  private void appendSwitched(@NotNull ChangesBrowserNodeRenderer renderer, @Nullable VirtualFile file) {
    if (file != null && myProject != null && !myProject.isDefault() && !myProject.isDisposed()) {
      String branch = ChangesTreeCompatibilityProvider.getInstance().getSwitchedBranch(myProject, file);
      if (branch != null) {
        String switchedToBranch = "[" + VcsBundle.message("changes.switched.to.branch.name", branch) + "]";
        renderer.append(spaceAndThinSpace() + switchedToBranch, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }
  }

  @Override
  public String getTooltip() {
    return getUserObject().getDescription();
  }

  @Override
  public String getTextPresentation() {
    return ChangesUtil.getFilePath(getUserObject()).getName();
  }

  @Override
  public String toString() {
    return FileUtil.toSystemDependentName(ChangesUtil.getFilePath(getUserObject()).getPath());
  }

  @Override
  public int getSortWeight() {
    return CHANGE_SORT_WEIGHT;
  }

  @Override
  public int compareUserObjects(final Change o2) {
    return ChangesComparator.getInstance(true).compare(getUserObject(), o2);
  }
}
