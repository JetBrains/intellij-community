/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.execution.junit2.ui.actions;

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.TestMethods;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.JavaAwareFilter;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Alexey
 */
public class RerunFailedTestsAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.ui.actions.RerunFailedTestsAction");
  private JUnitRunningModel myModel;
  private final JUnitConsoleProperties myConsoleProperties;
  private final RunnerSettings myRunnerSettings;
  private final ConfigurationPerRunnerSettings myConfigurationPerRunnerSettings;

  public RerunFailedTestsAction(final JUnitConsoleProperties consoleProperties,
                                final RunnerSettings runnerSettings,
                                final ConfigurationPerRunnerSettings configurationSettings) {
    super(ExecutionBundle.message("rerun.failed.tests.action.name"),
          ExecutionBundle.message("rerun.failed.tests.action.description"),
          IconLoader.getIcon("/runConfigurations/rerunFailedTests.png"));
    myConsoleProperties = consoleProperties;
    myRunnerSettings = runnerSettings;
    myConfigurationPerRunnerSettings = configurationSettings;
  }

  public void setModel(JUnitRunningModel model) {
    myModel = model;
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(isActive(e));
  }

  private boolean isActive(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return false;
    if (myModel == null || myModel.getRoot() == null) return false;
    List<AbstractTestProxy> failed = getFailedTests();
    return !failed.isEmpty();
  }

  @NotNull private List<AbstractTestProxy> getFailedTests() {
    List<TestProxy> myAllTests = myModel.getRoot().getAllTests();
    return Filter.DEFECTIVE_LEAF.and(JavaAwareFilter.METHOD(myModel.getProject())).select(myAllTests);
  }

  public void actionPerformed(AnActionEvent e) {
    List<AbstractTestProxy> failed = getFailedTests();

    final DataContext dataContext = e.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    JUnitConfiguration configuration = myModel.getProperties().getConfiguration();
    final TestMethods testMethods = new TestMethods(project, configuration, myRunnerSettings, myConfigurationPerRunnerSettings, failed);
    boolean isDebug = myConsoleProperties.isDebug();
    try {
      final RunProfile profile = new MyRunProfile(testMethods, configuration);
      final Executor executor = isDebug ? DefaultDebugExecutor.getDebugExecutorInstance() : DefaultRunExecutor.getRunExecutorInstance();
      final ProgramRunner runner = RunnerRegistry.getInstance().getRunner(executor.getId(), profile);
      assert runner != null;
      runner.execute(executor, new ExecutionEnvironment(profile, myRunnerSettings, myConfigurationPerRunnerSettings, dataContext));
    }
    catch (ExecutionException e1) {
      LOG.error(e1);
    }
    finally{
      testMethods.clear();
    }
  }

  private static class MyRunProfile implements ModuleRunProfile, RunConfiguration {
    private final TestMethods myTestMethods;
    private final RunConfiguration myConfiguration;

    public MyRunProfile(final TestMethods testMethods, final JUnitConfiguration configuration) {
      myTestMethods = testMethods;
      myConfiguration = configuration;
    }

    public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
      myTestMethods.clear();
      return myTestMethods;
    }

    public String getName() {
      return ExecutionBundle.message("rerun.failed.tests.action.name");
    }

    public void checkConfiguration() throws RuntimeConfigurationException {

    }

    @NotNull
    public Module[] getModules() {
      return myTestMethods.getModulesToCompile();
    }
    ///////////////////////////////////Delegates
    public void readExternal(final Element element) throws InvalidDataException {
      myConfiguration.readExternal(element);
    }

    public void writeExternal(final Element element) throws WriteExternalException {
      myConfiguration.writeExternal(element);
    }

    public ConfigurationFactory getFactory() {
      return myConfiguration.getFactory();
    }

    public void setName(final String name) {
      myConfiguration.setName(name);
    }

    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
      return myConfiguration.getConfigurationEditor();
    }

    public Project getProject() {
      return myConfiguration.getProject();
    }

    @NotNull
    public ConfigurationType getType() {
      return myConfiguration.getType();
    }

    public JDOMExternalizable createRunnerSettings(final ConfigurationInfoProvider provider) {
      return myConfiguration.createRunnerSettings(provider);
    }

    public SettingsEditor<JDOMExternalizable> getRunnerSettingsEditor(final ProgramRunner runner) {
      return myConfiguration.getRunnerSettingsEditor(runner);
    }

    public RunConfiguration clone() {
      return myConfiguration.clone();
    }

    public Object getExtensionSettings(final Class<? extends RunConfigurationExtension> extensionClass) {
      return myConfiguration.getExtensionSettings(extensionClass);
    }

    public void setExtensionSettings(final Class<? extends RunConfigurationExtension> extensionClass, final Object value) {
      myConfiguration.setExtensionSettings(extensionClass, value);
    }
  }
}
