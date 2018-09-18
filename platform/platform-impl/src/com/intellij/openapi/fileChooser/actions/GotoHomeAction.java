// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Vladimir Kondratyev
 */
public final class GotoHomeAction extends FileChooserAction {
  @Override
  protected void actionPerformed(final FileSystemTree fileSystemTree, final AnActionEvent e) {
    final VirtualFile userHomeDir = VfsUtil.getUserHomeDir();
    if (userHomeDir != null) {
      fileSystemTree.select(userHomeDir, () -> fileSystemTree.expand(userHomeDir, null));
    }
  }

  @Override
  protected void update(final FileSystemTree fileSystemTree, final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    if (!presentation.isEnabled()) return;

    final VirtualFile userHomeDir = VfsUtil.getUserHomeDir();
    presentation.setEnabled(userHomeDir != null && fileSystemTree.isUnderRoots(userHomeDir));
  }
}
