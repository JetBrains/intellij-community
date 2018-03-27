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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.util.FontUtil.spaceAndThinSpace;

public class ChangesBrowserChangeNode extends ChangesBrowserNode<Change> implements TreeLinkMouseListener.HaveTooltip {

  @NotNull private final Project myProject;
  @Nullable private final ChangeNodeDecorator myDecorator;

  protected ChangesBrowserChangeNode(@NotNull Project project, @NotNull Change userObject, @Nullable ChangeNodeDecorator decorator) {
    super(userObject);
    myProject = project;
    myDecorator = decorator;
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
    Change change = getUserObject();
    FilePath filePath = ChangesUtil.getFilePath(change);
    VirtualFile file = filePath.getVirtualFile();

    if (myDecorator != null) {
      myDecorator.preDecorate(change, renderer, renderer.isShowFlatten());
    }

    renderer.appendFileName(file, filePath.getName(), change.getFileStatus().getColor());

    String originText = change.getOriginText(myProject);
    if (originText != null) {
      renderer.append(spaceAndThinSpace() + originText, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }

    if (renderer.isShowFlatten()) {
      FilePath parentPath = filePath.getParentPath();
      if (parentPath != null) {
        appendParentPath(renderer, parentPath);
      }
    }

    appendSwitched(renderer, file);

    if (!renderer.isShowFlatten() && getFileCount() != 1 || getDirectoryCount() != 0) {
      appendCount(renderer);
    }

    Icon additionalIcon = change.getAdditionalIcon();
    if (additionalIcon != null) {
      renderer.setIcon(additionalIcon);
    }
    else {
      renderer.setIcon(filePath.getFileType(), filePath.isDirectory() || !isLeaf());
    }

    if (myDecorator != null) {
      myDecorator.decorate(change, renderer, renderer.isShowFlatten());
    }
  }

  private void appendSwitched(@NotNull ChangesBrowserNodeRenderer renderer, @Nullable VirtualFile file) {
    if (file != null && !myProject.isDefault()) {
      String branch = ChangeListManager.getInstance(myProject).getSwitchedBranch(file);
      if (branch != null) {
        renderer.append(spaceAndThinSpace() + "[switched to " + branch + "]", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }
  }

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

  public int getSortWeight() {
    return CHANGE_SORT_WEIGHT;
  }

  public int compareUserObjects(final Change o2) {
    return ChangesComparator.getInstance(true).compare(getUserObject(), o2);
  }
}
