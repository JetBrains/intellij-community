/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.project.DumbAware;

import javax.swing.*;

public abstract class FileChooserAction extends AnAction implements DumbAware {
  protected FileChooserAction() {
    setEnabledInModalContext(true);
  }

  protected FileChooserAction(final String text, final String description, final Icon icon) {
    super(text, description, icon);
    setEnabledInModalContext(true);
  }

  final public void actionPerformed(AnActionEvent e) {
    FileSystemTree tree = e.getData(FileSystemTree.DATA_KEY);
    actionPerformed(tree, e);
  }

  final public void update(AnActionEvent e) {
    FileSystemTree tree = e.getData(FileSystemTree.DATA_KEY);
    if (tree != null) {
      e.getPresentation().setEnabled(true);
      update(tree, e);
    }
    else {
      e.getPresentation().setEnabled(false);
    }
  }

  protected abstract void update(FileSystemTree fileChooser, AnActionEvent e);

  protected abstract void actionPerformed(FileSystemTree fileChooser, AnActionEvent e);
}