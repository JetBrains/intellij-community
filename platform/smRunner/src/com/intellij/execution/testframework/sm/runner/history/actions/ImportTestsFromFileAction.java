// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner.history.actions;

import com.intellij.execution.testframework.sm.SmRunnerBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImportTestsFromFileAction extends AbstractImportTestsAction {
  public ImportTestsFromFileAction() {
    super(SmRunnerBundle.message("sm.test.runner.import.test"),
          SmRunnerBundle.message("sm.test.runner.import.test.description"),
          AllIcons.ToolbarDecorator.Import);
  }

  @Nullable
  @Override
  protected VirtualFile getFile(@NotNull Project project) {
    final FileChooserDescriptor xmlDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor(StdFileTypes.XML);
    xmlDescriptor.setTitle(SmRunnerBundle.message("sm.test.runner.import.test.choose.test.file.title"));
    VirtualFile file = FileChooser.chooseFile(xmlDescriptor, project, null);
    if (file != null && !FileTypeRegistry.getInstance().isFileOfType(file, StdFileTypes.XML)) {
      Messages.showWarningDialog(project, 
                                 SmRunnerBundle.message("dialog.message.unable.to.parse.test.results", file.getName()), 
                                 SmRunnerBundle.message("sm.test.runner.import.test"));
      return null;
    }
    return file;
  }
}
