// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeFactoryImpl;
import com.intellij.openapi.project.Project;

/** @deprecated obsolete */
@Deprecated(forRemoval = true)
@SuppressWarnings("unused")
public interface FileSystemTreeFactory {
  FileSystemTree createFileSystemTree(Project project, FileChooserDescriptor fileChooserDescriptor);

  DefaultActionGroup createDefaultFileSystemActions(FileSystemTree fileSystemTree);

  final class SERVICE {
    private SERVICE() { }

    public static FileSystemTreeFactory getInstance() {
      return new FileSystemTreeFactoryImpl();
    }
  }
}
