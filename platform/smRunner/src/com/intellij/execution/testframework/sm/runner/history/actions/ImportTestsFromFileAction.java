// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner.history.actions;

import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImportTestsFromFileAction extends AbstractImportTestsAction {
  public ImportTestsFromFileAction(SMTRunnerConsoleProperties properties) {
    super(properties, (properties == null ? "" : "Import ") + "From File ...", "Import tests from file", null);
  }

  @Nullable
  @Override
  public VirtualFile getFile(@NotNull Project project) {
    final FileChooserDescriptor xmlDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor(StdFileTypes.XML);
    xmlDescriptor.setTitle("Choose a File with Tests Result");
    return FileChooser.chooseFile(xmlDescriptor, project, null);
  }
}
