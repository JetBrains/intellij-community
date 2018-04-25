// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.LocallyDeletedChange;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ChangesBrowserLocallyDeletedNode extends ChangesBrowserNode<LocallyDeletedChange>
  implements TreeLinkMouseListener.HaveTooltip {
  public ChangesBrowserLocallyDeletedNode(@NotNull LocallyDeletedChange userObject) {
    super(userObject);
  }

  @Override
  protected boolean isFile() {
    return !isDirectory();
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
      appendParentPath(renderer, filePath.getParentPath());
    }

    if (!renderer.isShowFlatten() && getFileCount() != 1 || getDirectoryCount() != 0) {
      appendCount(renderer);
    }

    Icon additionalIcon = getUserObject().getAddIcon();
    if (additionalIcon != null) {
      renderer.setIcon(additionalIcon);
    }
    else {
      renderer.setIcon(filePath.getFileType(), filePath.isDirectory() || !isLeaf());
    }
  }

  @Nullable
  public String getTooltip() {
    return getUserObject().getDescription();
  }
}
