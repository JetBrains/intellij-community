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
import com.intellij.util.PlatformIcons;
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
    if (!ChangesUtil.getFilePath(userObject).isDirectory()) {
      myCount = 1;
    }
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
        renderer.append(spaceAndThinSpace() + FileUtil.getLocationRelativeToUserHome(parentPath.getPath()),
                        SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      appendSwitched(renderer, file);
    }
    else if (getCount() != 1 || getDirectoryCount() != 0) {
      appendSwitched(renderer, file);
      appendCount(renderer);
    }
    else {
      appendSwitched(renderer, file);
    }

    renderer.setIcon(getIcon(change, filePath));

    if (myDecorator != null) {
      myDecorator.decorate(change, renderer, renderer.isShowFlatten());
    }
  }

  @Nullable
  private Icon getIcon(@NotNull Change change, @NotNull FilePath filePath) {
    Icon result = change.getAdditionalIcon();

    if (result == null) {
      result = filePath.isDirectory() || !isLeaf() ? PlatformIcons.DIRECTORY_CLOSED_ICON : filePath.getFileType().getIcon();
    }

    return result;
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
    return 6;
  }

  public int compareUserObjects(final Object o2) {
    if (o2 instanceof Change) {
      return ChangesUtil.getFilePath(getUserObject()).getName().compareToIgnoreCase(ChangesUtil.getFilePath((Change)o2).getName());
    }
    return 0;
  }
}
