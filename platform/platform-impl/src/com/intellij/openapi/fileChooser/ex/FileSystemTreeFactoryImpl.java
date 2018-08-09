// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
  public FileSystemTree createFileSystemTree(Project project, FileChooserDescriptor fileChooserDescriptor) {
    return new FileSystemTreeImpl(project, fileChooserDescriptor);
  }

  @Override
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
