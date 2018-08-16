// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;

/**
 * @author yole
 */
public class RefreshFileChooserAction extends FileChooserAction {
  @Override
  protected void update(FileSystemTree fileChooser, AnActionEvent e) {
  }

  @Override
  protected void actionPerformed(FileSystemTree fileChooser, AnActionEvent e) {
    RefreshQueue.getInstance().refresh(true, true, null, ModalityState.current(), ManagingFS.getInstance().getLocalRoots());
  }
}