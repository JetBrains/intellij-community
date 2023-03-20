// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.ex;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.FileSystemTreeFactory;
import com.intellij.openapi.project.Project;

/** @deprecated obsolete */
@Deprecated(forRemoval = true)
public class FileSystemTreeFactoryImpl implements FileSystemTreeFactory {
  @Override
  public FileSystemTree createFileSystemTree(Project project, FileChooserDescriptor fileChooserDescriptor) {
    return new FileSystemTreeImpl(project, fileChooserDescriptor);
  }

  @Override
  public DefaultActionGroup createDefaultFileSystemActions(FileSystemTree fileSystemTree) {
    return (DefaultActionGroup)ActionManager.getInstance().getAction("FileChooserToolbar");
  }
}
