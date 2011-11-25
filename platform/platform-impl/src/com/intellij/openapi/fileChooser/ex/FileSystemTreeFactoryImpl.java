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
package com.intellij.openapi.fileChooser.ex;

import com.intellij.ide.actions.SynchronizeAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.FileSystemTreeFactory;
import com.intellij.openapi.project.Project;

public class FileSystemTreeFactoryImpl implements FileSystemTreeFactory{
  public FileSystemTreeFactoryImpl() {
  }

  public FileSystemTree createFileSystemTree(Project project, FileChooserDescriptor fileChooserDescriptor) {
    return new FileSystemTreeImpl(project, fileChooserDescriptor);
  }

  public DefaultActionGroup createDefaultFileSystemActions(FileSystemTree fileSystemTree) {
    DefaultActionGroup group = new DefaultActionGroup();
    final ActionManager actionManager = ActionManager.getInstance();
    group.add(actionManager.getAction("FileChooser.GotoHome"));
    group.add(actionManager.getAction("FileChooser.GotoProject"));
    group.addSeparator();
    group.add(actionManager.getAction("FileChooser.NewFolder"));
    group.add(actionManager.getAction("FileChooser.Delete"));
    group.addSeparator();
    SynchronizeAction action1 = new SynchronizeAction();
    AnAction original = actionManager.getAction(IdeActions.ACTION_SYNCHRONIZE);
    action1.copyFrom(original);
    action1.registerCustomShortcutSet(original.getShortcutSet(), fileSystemTree.getTree());
    group.add(action1);
    group.addSeparator();
    group.add(actionManager.getAction("FileChooser.ShowHiddens"));

    return group;
  }
}
