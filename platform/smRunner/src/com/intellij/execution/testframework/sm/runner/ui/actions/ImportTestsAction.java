/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.testframework.sm.runner.ui.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.testframework.history.ImportedTestRunnableState;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ImportTestsAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#" + ImportTestsAction.class.getName());
  private final SMTRunnerConsoleProperties myProperties;

  public ImportTestsAction(SMTRunnerConsoleProperties properties) {
    super("Import Tests", "Import tests from file", AllIcons.ToolbarDecorator.Import);
    myProperties = properties;
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final FileChooserDescriptor xmlDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor(StdFileTypes.XML);
    xmlDescriptor.setTitle("Choose a File with Tests Result");
    final Project project = e.getProject();
    LOG.assertTrue(project != null);
    final VirtualFile file = FileChooser.chooseFile(xmlDescriptor, project, null);
    if (file != null) {
      try {
        ExecutionEnvironmentBuilder.create(project, myProperties.getExecutor(), new ImportRunProfile(file)).buildAndExecute();
      }
      catch (ExecutionException e1) {
        Messages.showErrorDialog(project, e1.getMessage(), "Import Failed");
      }
    }
  }

  private class ImportRunProfile implements RunProfile {
    private final VirtualFile myFile;

    public ImportRunProfile(VirtualFile file) {
      myFile = file;
    }

    @Nullable
    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException {
      return new ImportedTestRunnableState(myProperties, VfsUtilCore.virtualToIoFile(myFile));
    }

    @Override
    public String getName() {
      return myFile.getNameWithoutExtension();
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return myProperties.getConfiguration().getIcon();
    }
  }
}
