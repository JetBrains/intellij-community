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

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.sm.runner.SMRunnerConsolePropertiesProvider;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.history.ImportedTestRunnableState;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.FilenameFilter;
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
  public static final String TEST_HISTORY_SIZE = "test_history_size";
  private SMTRunnerConsoleProperties myProperties;

  public AbstractImportTestsAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  public AbstractImportTestsAction(SMTRunnerConsoleProperties properties, @Nullable String text, @Nullable String description, @Nullable Icon icon) {
    this(text, description, icon);
    myProperties = properties;
  }

  public static int getHistorySize() {
    int historySize;
    try {
      historySize = Math.max(0, Integer.parseInt(PropertiesComponent.getInstance().getValue(TEST_HISTORY_SIZE, "10")));
    }
    catch (NumberFormatException e) {
      historySize = 10;
    }
    return historySize;
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
        ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.create(project, executor, profile);
        ExecutionTarget target = profile.getTarget();
        if (target != null) {
          builder = builder.target(target);
        }
        final RunConfiguration initialConfiguration = profile.getInitialConfiguration();
        final ProgramRunner runner =
          initialConfiguration != null ? RunnerRegistry.getInstance().getRunner(executor.getId(), initialConfiguration) : null;
        if (runner != null) {
          builder = builder.runner(runner);
        }
        builder.buildAndExecute();
      }
      catch (ExecutionException e1) {
        Messages.showErrorDialog(project, e1.getMessage(), "Import Failed");
      }
    }
  }
  
  public static void adjustHistory(Project project) {
    int historySize = getHistorySize();

    final File[] files = TestStateStorage.getTestHistoryRoot(project).listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return name.endsWith(".xml");
      }
    });
    if (files != null && files.length >= historySize + 1) {
      Arrays.sort(files, new Comparator<File>() {
        @Override
        public int compare(File o1, File o2) {
          final long l1 = o1.lastModified();
          final long l2 = o2.lastModified();
          if (l1 == l2) return FileUtil.compareFiles(o1, o2);
          return l1 < l2 ? -1 : 1;
        }
      });
      FileUtil.delete(files[0]);
    }
  }

  public static class ImportRunProfile implements RunProfile {
    private final VirtualFile myFile;
    private final Project myProject;
    private RunConfiguration myConfiguration;
    private boolean myImported;
    private SMTRunnerConsoleProperties myProperties;
    private String myTargetId;

    public ImportRunProfile(VirtualFile file, Project project) {
      myFile = file;
      myProject = project;
      try {
        final Document document = JDOMUtil.loadDocument(VfsUtilCore.virtualToIoFile(myFile));
        final Element config = document.getRootElement().getChild("config");
        if (config != null) {
          String configTypeId = config.getAttributeValue("configId");
          if (configTypeId != null) {
            final ConfigurationType configurationType = ConfigurationTypeUtil.findConfigurationType(configTypeId);
            if (configurationType != null) {
              myConfiguration = configurationType.getConfigurationFactories()[0].createTemplateConfiguration(project);
              myConfiguration.setName(config.getAttributeValue("name"));
              myConfiguration.readExternal(config);

              final Executor executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID);
              if (executor != null) {
                if (myConfiguration instanceof SMRunnerConsolePropertiesProvider) {
                  myProperties = ((SMRunnerConsolePropertiesProvider)myConfiguration).createTestConsoleProperties(executor);
                }
              }
            }
          }
          myTargetId = config.getAttributeValue("target");
        }
      }
      catch (Exception ignore) {
      }
    }

    public ExecutionTarget getTarget() {
      if (myTargetId != null) {
        if (DefaultExecutionTarget.INSTANCE.getId().equals(myTargetId)) {
          return DefaultExecutionTarget.INSTANCE;
        }
        final RunnerAndConfigurationSettingsImpl settings = 
          new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(myProject), myConfiguration, false);
        for (ExecutionTargetProvider provider : Extensions.getExtensions(ExecutionTargetProvider.EXTENSION_NAME)) {
          for (ExecutionTarget target : provider.getTargets(myProject, settings)) {
            if (myTargetId.equals(target.getId())) {
              return target;
            }
          }
        }
        return null;
      }
      return DefaultExecutionTarget.INSTANCE;
    }

    @Nullable
    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException {
      if (!myImported) {
        myImported = true;
        return new ImportedTestRunnableState(this, VfsUtilCore.virtualToIoFile(myFile));
      }
      if (myConfiguration != null) {
        try {
          return myConfiguration.getState(executor, environment);
        }
        catch (Throwable e) {
          if (myTargetId != null && getTarget() == null) {
            throw new ExecutionException("The target " + myTargetId + " does not exist");
          }

          LOG.info(e);
          throw new ExecutionException("Unable to run the configuration: settings are corrupted");
        }
      }
      throw new ExecutionException("Unable to run the configuration: failed to detect test framework");
    }

    @Override
    public String getName() {
      return myImported && myConfiguration != null ? myConfiguration.getName() : myFile.getNameWithoutExtension();
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return myProperties != null ? myProperties.getConfiguration().getIcon() : null;
    }

    public SMTRunnerConsoleProperties getProperties() {
      return myProperties;
    }

    public RunConfiguration getInitialConfiguration() {
      return myConfiguration;
    }

    public Project getProject() {
      return myProject;
    }
  }
}
