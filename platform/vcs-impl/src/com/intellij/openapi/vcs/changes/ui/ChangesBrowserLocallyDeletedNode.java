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

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.ChangeListOwner;
import com.intellij.openapi.vcs.changes.LocallyDeletedChange;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.util.FontUtil.spaceAndThinSpace;

public class ChangesBrowserLocallyDeletedNode extends ChangesBrowserNode<LocallyDeletedChange>
  implements TreeLinkMouseListener.HaveTooltip {
  public ChangesBrowserLocallyDeletedNode(@NotNull LocallyDeletedChange userObject) {
    super(userObject);
    if (!isDirectory()) {
      myCount = 1;
    }
  }

  public boolean canAcceptDrop(ChangeListDragBean dragBean) {
    return false;
  }

  public void acceptDrop(ChangeListOwner dragOwner, ChangeListDragBean dragBean) {
  }

  @Override
  protected boolean isDirectory() {
    return getUserObject().getPath().isDirectory();
  }

  @Override
  public void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    // todo would be good to have render code in one place
    FilePath filePath = getUserObject().getPath();
    renderer.appendFileName(filePath.getVirtualFile(), filePath.getName(), FileStatus.NOT_CHANGED.getColor());

    if (renderer.isShowFlatten()) {
      FilePath parentPath = filePath.getParentPath();
      if (parentPath != null) {
        renderer.append(spaceAndThinSpace() + parentPath.getPresentableUrl(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
    }
    else if (getCount() != 1 || getDirectoryCount() != 0) {
      appendCount(renderer);
    }

    renderer.setIcon(getIcon());
  }

  @Nullable
  public String getTooltip() {
    return getUserObject().getDescription();
  }

  @Nullable
  private Icon getIcon() {
    Icon result = getUserObject().getAddIcon();

    if (result == null) {
      FilePath filePath = getUserObject().getPath();
      result = filePath.isDirectory() || !isLeaf() ? PlatformIcons.DIRECTORY_CLOSED_ICON : filePath.getFileType().getIcon();
    }

    return result;
  }
}
