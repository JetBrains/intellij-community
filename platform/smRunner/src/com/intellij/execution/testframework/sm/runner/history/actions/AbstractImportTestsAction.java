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
package com.intellij.execution.testframework.sm.runner.history.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.testframework.export.TestResultsXmlFormatter;
import com.intellij.execution.testframework.sm.runner.history.ImportedTestRunnableState;
import com.intellij.execution.testframework.sm.runner.SMRunnerConsolePropertiesProvider;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 1. chooses file where test results were saved
 * 2. finds the configuration element saved during export
 * 3. creates corresponding configuration with {@link SMTRunnerConsoleProperties} if configuration implements {@link SMRunnerConsolePropertiesProvider}
 * 
 * Without console properties no navigation, no rerun failed is possible.
 */
public abstract class AbstractImportTestsAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#" + AbstractImportTestsAction.class.getName());
  private static final File TEST_HISTORY_PATH = new File(PathManager.getSystemPath(), "testHistory");
  public static final String TEST_HISTORY_SIZE = "test_history_size";
  private SMTRunnerConsoleProperties myProperties;

  public AbstractImportTestsAction(@Nullable String text, @Nullable String description) {
    super(text, description, null);
  }

  public AbstractImportTestsAction(SMTRunnerConsoleProperties properties, @Nullable String text, @Nullable String description) {
    this(text, description);
    myProperties = properties;
  }

  public static File getTestHistoryRoot(Project project) {
    return new File(TEST_HISTORY_PATH, FileUtil.sanitizeFileName(project.getName()));
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null);
  }

  @Nullable
  public abstract VirtualFile getFile(@NotNull Project project);
  
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    LOG.assertTrue(project != null);
    final VirtualFile file = getFile(project);
    if (file != null) {
      try {
        final ImportRunProfile profile = new ImportRunProfile(file, project);
        SMTRunnerConsoleProperties properties = profile.getProperties();
        if (properties == null) {
          properties = myProperties;
          LOG.info("Failed to detect test framework in " + file.getPath() +
                   "; use " + (properties != null ? properties.getTestFrameworkName() + " from toolbar" : "no properties"));
        }
        final Executor executor = properties != null ? properties.getExecutor() 
                                                     : ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID);
        ExecutionEnvironmentBuilder.create(project, executor, profile).buildAndExecute();
      }
      catch (ExecutionException e1) {
        Messages.showErrorDialog(project, e1.getMessage(), "Import Failed");
      }
    }
  }
  
  public static void adjustHistory(Project project) {
    int historySize;
    try {
      historySize = Math.max(0, Integer.parseInt(PropertiesComponent.getInstance().getValue(TEST_HISTORY_SIZE, "10")));
    }
    catch (NumberFormatException e) {
      historySize = 5;
    }

    final File[] files = getTestHistoryRoot(project).listFiles();
    if (files != null && files.length >= historySize) {
      Arrays.sort(files, new Comparator<File>() {
        @Override
        public int compare(File o1, File o2) {
          final long l1 = o1.lastModified();
          final long l2 = o2.lastModified();
          if (l1 == l2) return 0;
          return l1 < l2 ? -1 : 1;
        }
      });
      FileUtil.delete(files[0]);
    }
  }

  public static class ImportRunProfile implements RunProfile {
    private final VirtualFile myFile;
    private final Project myProject;
    private RunnerAndConfigurationSettingsImpl mySettings;
    private boolean myImported;
    private SMTRunnerConsoleProperties myProperties;

    public ImportRunProfile(VirtualFile file, Project project) {
      myFile = file;
      myProject = project;
      try {
        final Document document = JDOMUtil.loadDocument(VfsUtilCore.virtualToIoFile(myFile));
        final Element config = document.getRootElement().getChild("config");
        if (config != null) {
          mySettings = new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(project));
          try {
            mySettings.readExternal(config);
            final Executor executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID);
            if (executor != null) {
              final RunConfiguration configuration = mySettings.getConfiguration();
              if (configuration instanceof SMRunnerConsolePropertiesProvider) {
                myProperties = ((SMRunnerConsolePropertiesProvider)configuration).createTestConsoleProperties(executor);
              }
            }
          }
          catch (InvalidDataException e) {
            LOG.info(e);
            mySettings = null;
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
        return new ImportedTestRunnableState(this, VfsUtilCore.virtualToIoFile(myFile));
      }
      if (mySettings != null) {
        try {
          final RunConfiguration configuration = mySettings.getConfiguration();
          if (configuration instanceof UserDataHolder) {
            ((UserDataHolder)configuration).putUserData(TestResultsXmlFormatter.SETTINGS, mySettings);
          }
          return configuration.getState(executor, environment);
        }
        catch (Throwable e) {
          LOG.info(e);
          throw new ExecutionException("Unable to run the configuration: settings are corrupted");
        }
      }
      throw new ExecutionException("Unable to run the configuration: failed to detect test framework");
    }

    @Override
    public String getName() {
      return myImported && mySettings != null ? mySettings.getName() : myFile.getNameWithoutExtension();
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return myProperties != null ? myProperties.getConfiguration().getIcon() : null;
    }

    public SMTRunnerConsoleProperties getProperties() {
      return myProperties;
    }

    public Project getProject() {
      return myProject;
    }
  }
}
