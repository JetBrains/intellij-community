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
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
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
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;

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
        ExecutionEnvironmentBuilder.create(project, myProperties.getExecutor(), new ImportRunProfile(file, project)).buildAndExecute();
      }
      catch (ExecutionException e1) {
        Messages.showErrorDialog(project, e1.getMessage(), "Import Failed");
      }
    }
  }

  private class ImportRunProfile implements RunProfile {
    private final VirtualFile myFile;
    private RunnerAndConfigurationSettingsImpl mySettings;
    private boolean myImported;

    public ImportRunProfile(VirtualFile file, Project project) {
      myFile = file;
      try {
        final Document document = JDOMUtil.loadDocument(VfsUtilCore.virtualToIoFile(myFile));
        final Element config = document.getRootElement().getChild("config");
        if (config != null) {
          mySettings = new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(project));
          try {
            mySettings.readExternal(config);
          }
          catch (InvalidDataException e) {
            LOG.error(e);
          }
        }
      }
      catch (Exception ignore) {
      }
    }

    @Nullable
    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException {
      if (!myImported) {
        myImported = true;
        return new ImportedTestRunnableState(myProperties, VfsUtilCore.virtualToIoFile(myFile));
      }
      if (mySettings != null) {
        return mySettings.getConfiguration().getState(executor, environment);
      }
      throw new ExecutionException("Unable to run the configuration");
    }

    @Override
    public String getName() {
      return myImported && mySettings != null ? mySettings.getName() : myFile.getNameWithoutExtension();
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return myProperties.getConfiguration().getIcon();
    }
  }
}
