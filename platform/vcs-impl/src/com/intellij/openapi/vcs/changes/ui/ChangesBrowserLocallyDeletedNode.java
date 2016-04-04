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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;

import javax.swing.*;
import java.awt.*;
import java.io.File;

import static com.intellij.util.FontUtil.spaceAndThinSpace;

public class ChangesBrowserLocallyDeletedNode extends ChangesBrowserNode implements TreeLinkMouseListener.HaveTooltip {
  public ChangesBrowserLocallyDeletedNode(LocallyDeletedChange userObject) {
    super(userObject);
    myCount = 1;
  }

  public boolean canAcceptDrop(final ChangeListDragBean dragBean) {
    return false;
  }

  public void acceptDrop(final ChangeListOwner dragOwner, final ChangeListDragBean dragBean) {
  }

  @Override
  protected FilePath getMyPath() {
    final LocallyDeletedChange change = (LocallyDeletedChange) getUserObject();
    if (change != null) {
      return change.getPath();
    }
    return null;
  }

  @Override
  public void render(ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    // todo would be good to have render code in one place
    final LocallyDeletedChange change = (LocallyDeletedChange)getUserObject();
    final FilePath filePath = change.getPath();

    final String fileName = filePath.getName();
    VirtualFile vFile = filePath.getVirtualFile();
    final Color changeColor = FileStatus.NOT_CHANGED.getColor();
    renderer.appendFileName(vFile, fileName, changeColor);

    if (renderer.isShowFlatten()) {
      final File parentFile = filePath.getIOFile().getParentFile();
      if (parentFile != null) {
        renderer.append(spaceAndThinSpace() + parentFile.getPath(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
    }
    else if (getCount() != 1 || getDirectoryCount() != 0) {
      appendCount(renderer);
    }

    final Icon addIcon = change.getAddIcon();
    if (addIcon != null) {
      renderer.setIcon(addIcon);
    } else {
      if (filePath.isDirectory() || !isLeaf()) {
        renderer.setIcon(PlatformIcons.DIRECTORY_CLOSED_ICON);
      }
      else {
        renderer.setIcon(filePath.getFileType().getIcon());
      }
    }
  }

  public String getTooltip() {
    final LocallyDeletedChange change = (LocallyDeletedChange)getUserObject();
    return change.getDescription();
  }
}
