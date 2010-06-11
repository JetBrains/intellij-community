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
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.segments.DeferredActionsQueue;
import com.intellij.execution.junit2.segments.DeferredActionsQueueImpl;
import com.intellij.execution.junit2.segments.DispatchListener;
import com.intellij.execution.junit2.ui.JUnitTreeConsoleView;
import com.intellij.execution.junit2.ui.TestsPacketsReceiver;
import com.intellij.execution.junit2.ui.actions.RerunFailedTestsAction;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.*;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.rt.execution.junit.IDEAJUnitListener;
import com.intellij.rt.execution.junit.JUnitStarter;
import com.intellij.util.Function;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

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
  public File myListenersFile;

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
    if (JUnitConfiguration.TEST_PATTERN.equals(id)) {
      return new TestsPattern(project, configuration, runnerSettings, configurationSettings);
    }
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

  public abstract boolean isConfiguredByElement(JUnitConfiguration configuration,
                                                PsiClass testClass,
                                                PsiMethod testMethod,
                                                PsiPackage testPackage);

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

    public boolean isConfiguredByElement(final JUnitConfiguration configuration,
                                         PsiClass testClass,
                                         PsiMethod testMethod,
                                         PsiPackage testPackage) {
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
    JavaParametersUtil.configureConfiguration(myJavaParameters, myConfiguration);
    myJavaParameters.setMainClass(JUnitConfiguration.JUNIT_START_CLASS);
    final Module module = myConfiguration.getConfigurationModule().getModule();
    if (myJavaParameters.getJdk() == null){
      myJavaParameters.setJdk(module != null
                              ? ModuleRootManager.getInstance(module).getSdk()
                              : ProjectRootManager.getInstance(myProject).getProjectJdk());
    }

    myJavaParameters.getClassPath().add(JavaSdkUtil.getIdeaRtJarPath());
    myJavaParameters.getClassPath().add(PathUtil.getJarPathForClass(JUnitStarter.class));
    myJavaParameters.getProgramParametersList().add(JUnitStarter.IDE_VERSION + JUnitStarter.VERSION);
    for (RunConfigurationExtension ext : Extensions.getExtensions(RunConfigurationExtension.EP_NAME)) {
      ext.updateJavaParameters(myConfiguration, myJavaParameters, myRunnerSettings);
    }

    final Object[] listeners = Extensions.getExtensions(IDEAJUnitListener.EP_NAME);
    final StringBuffer buf = new StringBuffer();
    for (final Object listener : listeners) {
      boolean enabled = true;
      for (RunConfigurationExtension ext : Extensions.getExtensions(RunConfigurationExtension.EP_NAME)) {
        if (ext.isListenerDisabled(myConfiguration, listener)) {
          enabled = false;
          break;
        }
      }
      if (enabled) {
        final Class classListener = listener.getClass();
        buf.append(classListener.getName()).append("\n");
        myJavaParameters.getClassPath().add(PathUtil.getJarPathForClass(classListener));
      }
    }
    if (buf.length() > 0) {
      try {
        myListenersFile = FileUtil.createTempFile("junitlisteners", "");
        myListenersFile.deleteOnExit();
        myJavaParameters.getProgramParametersList().add("@@" + myListenersFile.getPath());
        FileUtil.writeToFile(myListenersFile, buf.toString().getBytes());
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
    final JUnitProcessHandler handler = JUnitProcessHandler.runJava(getJavaParameters(), myProject);
    for(final RunConfigurationExtension ext: Extensions.getExtensions(RunConfigurationExtension.EP_NAME)) {
      ext.handleStartProcess(myConfiguration, handler);
    }
    final JUnitConsoleProperties consoleProperties = new JUnitConsoleProperties(myConfiguration);
    final JUnitTreeConsoleView consoleView = new JUnitTreeConsoleView(consoleProperties, getRunnerSettings(), getConfigurationSettings());
    consoleView.initUI();
    consoleView.attachToProcess(handler);

    final TestsPacketsReceiver packetsReceiver = new TestsPacketsReceiver(consoleView) {
      @Override
      public void notifyStart(TestProxy root) {
        super.notifyStart(root);
        final JUnitRunningModel model = getModel();
        if (model != null) {
          handler.getOut().setDispatchListener(model.getNotifier());
          Disposer.register(model, new Disposable() {
            public void dispose() {
              handler.getOut().setDispatchListener(DispatchListener.DEAF);
            }
          });
          consoleView.attachToModel(model);
        }
      }
    };

    final DeferredActionsQueue queue = new DeferredActionsQueueImpl();
    handler.getOut().setPacketDispatcher(packetsReceiver, queue);
    handler.getErr().setPacketDispatcher(packetsReceiver, queue);

    handler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(ProcessEvent event) {
        handler.removeProcessListener(this);
        if (myTempFile != null) {
          FileUtil.delete(myTempFile);
        }
        if (myListenersFile != null) {
          FileUtil.delete(myListenersFile);
        }
        IJSwingUtilities.invoke(new Runnable() {
          public void run() {
            packetsReceiver.checkTerminated();
            final JUnitRunningModel model = packetsReceiver.getModel();
            TestsUIUtil.notifyByBalloon(myProject, model != null ? model.getRoot() : null, consoleProperties);
          }
        });
      }

      @Override
      public void onTextAvailable(final ProcessEvent event, final Key outputType) {
        final String text = event.getText();
        final ConsoleViewContentType consoleViewType = ConsoleViewContentType.getConsoleViewType(outputType);
        final TestProxy currentTest = packetsReceiver.getCurrentTest();
        if (currentTest != null) {
          currentTest.onOutput(text, consoleViewType);
        }
        else {
          consoleView.getPrinter().onNewAvailable(new ExternalOutput(text, consoleViewType));
        }
      }
    });

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return new DefaultExecutionResult(null, handler);
    }

    final RerunFailedTestsAction rerunFailedTestsAction = new RerunFailedTestsAction(consoleView.getComponent());
    rerunFailedTestsAction.init(consoleProperties, myRunnerSettings, myConfigurationSettings);
    rerunFailedTestsAction.setModelProvider(new Getter<TestFrameworkRunningModel>() {
      public TestFrameworkRunningModel get() {
        return packetsReceiver.getModel();
      }
    });

    final DefaultExecutionResult result = new DefaultExecutionResult(consoleView, handler);
    result.setRestartActions(rerunFailedTestsAction);
    return result;
  }


  protected <T> void addClassesListToJavaParameters(Collection<? extends T> elements, Function<T, String> nameFunction, String packageName,
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
        for (final T element : elements) {
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
