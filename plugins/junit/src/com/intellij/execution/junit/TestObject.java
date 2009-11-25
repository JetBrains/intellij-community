/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.junit;

import com.intellij.ExtensionPoints;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.junit2.ui.JUnitTreeConsoleView;
import com.intellij.execution.junit2.ui.actions.RerunFailedTestsAction;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.*;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.rt.execution.junit.IDEAJUnitListener;
import com.intellij.rt.execution.junit.JUnitStarter;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class TestObject implements JavaCommandLine {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit.TestObject");

  private static final String MESSAGE = ExecutionBundle.message("configuration.not.speficied.message");

  protected JavaParameters myJavaParameters;
  private final Project myProject;
  protected final JUnitConfiguration myConfiguration;
  private final RunnerSettings myRunnerSettings;
  private final ConfigurationPerRunnerSettings myConfigurationSettings;
  protected File myTempFile = null;

  public static TestObject fromString(final String id,
                                      final Project project,
                                      final JUnitConfiguration configuration,
                                      RunnerSettings runnerSettings, ConfigurationPerRunnerSettings configurationSettings) {
    if (JUnitConfiguration.TEST_METHOD.equals(id))
      return new TestMethod(project, configuration, runnerSettings, configurationSettings);
    if (JUnitConfiguration.TEST_CLASS.equals(id))
      return new TestClass(project, configuration, runnerSettings, configurationSettings);
    if (JUnitConfiguration.TEST_PACKAGE.equals(id))
      return new TestPackage(project, configuration, runnerSettings, configurationSettings);
    return NOT_CONFIGURED;
  }

  public Module[] getModulesToCompile() {
    final SourceScope sourceScope = getSourceScope();
    return sourceScope != null ? sourceScope.getModulesToCompile() : Module.EMPTY_ARRAY;
  }

  protected TestObject(final Project project,
                       final JUnitConfiguration configuration,
                       RunnerSettings runnerSettings,
                       ConfigurationPerRunnerSettings configurationSettings) {
    myProject = project;
    myConfiguration = configuration;
    myRunnerSettings = runnerSettings;
    myConfigurationSettings = configurationSettings;
  }

  public abstract String suggestActionName();

  public RunnerSettings getRunnerSettings() {
    return myRunnerSettings;
  }

  public ConfigurationPerRunnerSettings getConfigurationSettings() {
    return myConfigurationSettings;
  }

  public abstract RefactoringElementListener getListener(PsiElement element, JUnitConfiguration configuration);

  public abstract boolean isConfiguredByElement(JUnitConfiguration configuration, PsiElement element);

  protected void configureModule(final JavaParameters parameters, final RunConfigurationModule configurationModule, final String mainClassName)
    throws CantRunException {
    int classPathType = JavaParametersUtil.getClasspathType(configurationModule, mainClassName, true);
    JavaParametersUtil.configureModule(configurationModule, parameters, classPathType,
                                       myConfiguration.isAlternativeJrePathEnabled() ? myConfiguration.getAlternativeJrePath() : null);
  }

  private static final TestObject NOT_CONFIGURED = new TestObject(null, null, null, null) {
    public RefactoringElementListener getListener(final PsiElement element, final JUnitConfiguration configuration) {
      return null;
    }

    public String suggestActionName() {
      throw new RuntimeException(String.valueOf(myConfiguration));
    }

    public boolean isConfiguredByElement(final JUnitConfiguration configuration, final PsiElement element) {
      return false;
    }

    public void checkConfiguration() throws RuntimeConfigurationException {
      throw new RuntimeConfigurationError(MESSAGE);
    }

    public ExecutionResult execute() throws ExecutionException {
      throw createExecutionException();
    }

    public JavaParameters getJavaParameters() throws ExecutionException {
      throw createExecutionException();
    }

    protected void initialize() throws ExecutionException {
      throw createExecutionException();
    }

    protected ProcessHandler startProcess() throws ExecutionException {
      throw createExecutionException();
    }
  };

  private static ExecutionException createExecutionException() {
    return new ExecutionException(MESSAGE);
  }

  public void checkConfiguration() throws RuntimeConfigurationException{
    if (myConfiguration.isAlternativeJrePathEnabled()){
      if (myConfiguration.getAlternativeJrePath() == null ||
          myConfiguration.getAlternativeJrePath().length() == 0 ||
          !JavaSdk.checkForJre(myConfiguration.getAlternativeJrePath())){
        throw new RuntimeConfigurationWarning(
          ExecutionBundle.message("jre.path.is.not.valid.jre.home.error.mesage", myConfiguration.getAlternativeJrePath()));
      }
    }
  }

  public SourceScope getSourceScope() {
    return SourceScope.modulesWithDependencies(myConfiguration.getModules());
  }

  protected void initialize() throws ExecutionException {
    myJavaParameters.setupEnvs(myConfiguration.getPersistentData().getEnvs(), myConfiguration.getPersistentData().PASS_PARENT_ENVS);
    JavaParametersUtil.configureConfiguration(myJavaParameters, myConfiguration);
    myJavaParameters.setMainClass(JUnitConfiguration.JUNIT_START_CLASS);
    final Module module = myConfiguration.getConfigurationModule().getModule();
    if (myJavaParameters.getJdk() == null){
      myJavaParameters.setJdk(module != null
                              ? ModuleRootManager.getInstance(module).getSdk()
                              : ProjectRootManager.getInstance(myProject).getProjectJdk());
    }

    JavaSdkUtil.addRtJar(myJavaParameters.getClassPath());
    myJavaParameters.getClassPath().add(PathUtil.getJarPathForClass(JUnitStarter.class));
    myJavaParameters.getProgramParametersList().add(JUnitStarter.IDE_VERSION + JUnitStarter.VERSION);
    for (RunConfigurationExtension ext : Extensions.getExtensions(RunConfigurationExtension.EP_NAME)) {
      ext.updateJavaParameters(myConfiguration, myJavaParameters, myRunnerSettings);
    }

    final Object[] listeners = Extensions.getExtensions(IDEAJUnitListener.EP_NAME);
    final StringBuffer buf = new StringBuffer();
    for (final Object listener : listeners) {
      if (!((IDEAJUnitListener)listener).isEnabled(myConfiguration)) continue;
      final Class classListener = listener.getClass();
      buf.append(classListener.getName()).append("\n");
      myJavaParameters.getClassPath().add(PathUtil.getJarPathForClass(classListener));
    }
    if (buf.length() > 0) {
      try {
        final File tempFile = FileUtil.createTempFile("junitlisteners", "");
        tempFile.deleteOnExit();
        myJavaParameters.getProgramParametersList().add("@@" + tempFile.getPath());
        FileUtil.writeToFile(tempFile, buf.toString().getBytes());
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  public JavaParameters getJavaParameters() throws ExecutionException {
    if (myJavaParameters == null) {
      myJavaParameters = new JavaParameters();
      initialize();
      final Module module = myConfiguration.getConfigurationModule().getModule();
      final Object[] patchers = Extensions.getExtensions(ExtensionPoints.JUNIT_PATCHER);
      for (Object patcher : patchers) {
        ((JUnitPatcher)patcher).patchJavaParameters(module, myJavaParameters);
      }
    }
    return myJavaParameters;
  }

  public ExecutionResult execute(final Executor executor, @NotNull final ProgramRunner runner) throws ExecutionException {
    final JUnitConsoleProperties consoleProperties = new JUnitConsoleProperties(myConfiguration);
    final JUnitTreeConsoleView consoleView = new JUnitTreeConsoleView(consoleProperties, getRunnerSettings(), getConfigurationSettings());
    consoleView.initUI();
    final ProcessHandler handler = startProcess(consoleView);
    consoleView.attachToProcess(handler);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return new DefaultExecutionResult(null, handler);
    }

    RerunFailedTestsAction rerunFailedTestsAction = new RerunFailedTestsAction(consoleView.getComponent());
    rerunFailedTestsAction.init(consoleProperties, myRunnerSettings, myConfigurationSettings);
    rerunFailedTestsAction.setModelProvider(new Getter<TestFrameworkRunningModel>() {
      public TestFrameworkRunningModel get() {
        return consoleView.getModel();
      }
    });

    final DefaultExecutionResult result = new DefaultExecutionResult(consoleView, handler);
    result.setRestartActions(rerunFailedTestsAction);
    return result;
  }

  private ProcessHandler startProcess(final JUnitTreeConsoleView consoleView) throws ExecutionException {
    final JUnitProcessHandler handler = JUnitProcessHandler.runJava(getJavaParameters(), myProject);
    for(RunConfigurationExtension ext: Extensions.getExtensions(RunConfigurationExtension.EP_NAME)) {
        ext.handleStartProcess(myConfiguration, handler);
      }
    handler.addProcessListener(new ProcessAdapter() {
      public void processTerminated(final ProcessEvent event) {
        if (myTempFile != null) {
          myTempFile.delete();
        }
        
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            if (myProject.isDisposed()) return;
            final JUnitRunningModel model = consoleView.getModel();
            final int failed = model != null ? Filter.DEFECTIVE_LEAF.and(JavaAwareFilter.METHOD(myProject)).select(model.getRoot().getAllTests()).size() : -1;

            final TestConsoleProperties properties = consoleView.getProperties();
            if (properties == null) return;
            final String testRunDebugId = properties.isDebug() ? ToolWindowId.DEBUG : ToolWindowId.RUN;
            final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
            if (!Comparing.strEqual(toolWindowManager.getActiveToolWindowId(), testRunDebugId)) {
              toolWindowManager.notifyByBalloon(testRunDebugId,
                                                                       failed == -1 ? MessageType.WARNING : (failed > 0 ? MessageType.ERROR : MessageType.INFO),
                                                                       failed == -1 ? ExecutionBundle.message("test.not.started.progress.text") : (failed > 0 ? failed + " " + ExecutionBundle.message("junit.runing.info.tests.failed.label") :  ExecutionBundle.message("junit.runing.info.tests.passed.label")) , null, null);
            }
          }
        });
      }
    });
    return handler;
  }


  protected void addClassesListToJavaParameters(Collection<? extends PsiElement> elements, Function<PsiElement, String> nameFunction, String packageName,
                                                boolean createTempFile,
                                                boolean junit4) {
    try {
      if (createTempFile) {
        myTempFile = File.createTempFile("idea_junit", ".tmp");
        myTempFile.deleteOnExit();
        myJavaParameters.getProgramParametersList().add("@" + myTempFile.getAbsolutePath());
      }

      final PrintWriter writer = new PrintWriter(new FileWriter(myTempFile));
      try {
        writer.println(junit4 ? JUnitStarter.JUNIT4_PARAMETER : "-junit3");
        writer.println(packageName);
        final List<String> testNames = new ArrayList<String>();
        for (final PsiElement element : elements) {
          final String name = nameFunction.fun(element);
          if (name == null) {
            LOG.error("invalid element " + element);
            return;
          }
          testNames.add(name);
        }
        Collections.sort(testNames); //sort tests in FQN order
        for (String testName : testNames) {
          writer.println(testName);
        }
      }
      finally {
        writer.close();
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public void clear() {
    myJavaParameters = null;
  }
}
