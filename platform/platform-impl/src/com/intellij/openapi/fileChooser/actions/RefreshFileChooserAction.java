/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
  protected void update(FileSystemTree fileChooser, AnActionEvent e) {
  }

  @Override
  protected void actionPerformed(FileSystemTree fileChooser, AnActionEvent e) {
    RefreshQueue.getInstance().refresh(true, true, null, ModalityState.current(), ManagingFS.getInstance().getLocalRoots());
  }
}