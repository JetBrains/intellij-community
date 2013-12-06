/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.execution.junit2.segments.Extractor;
import com.intellij.execution.junit2.ui.JUnitTreeConsoleView;
import com.intellij.execution.junit2.ui.TestsPacketsReceiver;
import com.intellij.execution.junit2.ui.actions.RerunFailedTestsAction;
import com.intellij.execution.junit2.ui.model.CompletionEvent;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.junit2.ui.model.RootTestInfo;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.*;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.*;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.rt.execution.junit.IDEAJUnitListener;
import com.intellij.rt.execution.junit.JUnitStarter;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public abstract class TestObject implements JavaCommandLine {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit.TestObject");

  private static final String MESSAGE = ExecutionBundle.message("configuration.not.speficied.message");
  @NonNls private static final String JUNIT_TEST_FRAMEWORK_NAME = "JUnit";

  protected JavaParameters myJavaParameters;
  private final Project myProject;
  protected final JUnitConfiguration myConfiguration;
  protected final ExecutionEnvironment myEnvironment;
  protected File myTempFile = null;
  protected File myWorkingDirsFile = null;
  public File myListenersFile;

  public static TestObject fromString(final String id,
                                      final Project project,
                                      final JUnitConfiguration configuration,
                                      ExecutionEnvironment environment) {
    if (JUnitConfiguration.TEST_METHOD.equals(id))
      return new TestMethod(project, configuration, environment);
    if (JUnitConfiguration.TEST_CLASS.equals(id))
      return new TestClass(project, configuration, environment);
    if (JUnitConfiguration.TEST_PACKAGE.equals(id))
      return new TestPackage(project, configuration, environment);
    else if (JUnitConfiguration.TEST_DIRECTORY.equals(id)) {
      return new TestDirectory(project, configuration, environment);
    }
    if (JUnitConfiguration.TEST_PATTERN.equals(id)) {
      return new TestsPattern(project, configuration, environment);
    }
    return NOT_CONFIGURED;
  }

  public Module[] getModulesToCompile() {
    final SourceScope sourceScope = getSourceScope();
    return sourceScope != null ? sourceScope.getModulesToCompile() : Module.EMPTY_ARRAY;
  }

  protected TestObject(final Project project,
                       final JUnitConfiguration configuration,
                       ExecutionEnvironment environment) {
    myProject = project;
    myConfiguration = configuration;
    myEnvironment = environment;
  }

  public abstract String suggestActionName();

  public RunnerSettings getRunnerSettings() {
    return myEnvironment.getRunnerSettings();
  }

  public abstract RefactoringElementListener getListener(PsiElement element, JUnitConfiguration configuration);

  public abstract boolean isConfiguredByElement(JUnitConfiguration configuration,
                                                PsiClass testClass,
                                                PsiMethod testMethod,
                                                PsiPackage testPackage, 
                                                PsiDirectory testDir);

  protected void configureModule(final JavaParameters parameters, final RunConfigurationModule configurationModule, final String mainClassName)
    throws CantRunException {
    int classPathType = JavaParametersUtil.getClasspathType(configurationModule, mainClassName, true);
    JavaParametersUtil.configureModule(configurationModule, parameters, classPathType,
                                       myConfiguration.isAlternativeJrePathEnabled() ? myConfiguration.getAlternativeJrePath() : null);
  }

  private static final TestObject NOT_CONFIGURED = new TestObject(null, null, null) {
    @Override
    public RefactoringElementListener getListener(final PsiElement element, final JUnitConfiguration configuration) {
      return null;
    }

    @Override
    public String suggestActionName() {
      throw new RuntimeException(String.valueOf(myConfiguration));
    }

    @Override
    public boolean isConfiguredByElement(final JUnitConfiguration configuration,
                                         PsiClass testClass,
                                         PsiMethod testMethod,
                                         PsiPackage testPackage,
                                         PsiDirectory testDir) {
      return false;
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
      throw new RuntimeConfigurationError(MESSAGE);
    }

    @Override
    public JavaParameters getJavaParameters() throws ExecutionException {
      throw new ExecutionException(MESSAGE);
    }

    @Override
    protected void initialize() throws ExecutionException {
      throw new ExecutionException(MESSAGE);
    }
  };

  public void checkConfiguration() throws RuntimeConfigurationException{
    JavaParametersUtil.checkAlternativeJRE(myConfiguration);
    ProgramParametersUtil.checkWorkingDirectoryExist(myConfiguration, myConfiguration.getProject(), myConfiguration.getConfigurationModule().getModule());
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
                              : ProjectRootManager.getInstance(myProject).getProjectSdk());
    }

    myJavaParameters.getClassPath().add(JavaSdkUtil.getIdeaRtJarPath());
    myJavaParameters.getClassPath().add(PathUtil.getJarPathForClass(JUnitStarter.class));
    if (Registry.is("junit_sm_runner", false)) {
      myJavaParameters.getClassPath().add(PathUtil.getJarPathForClass(ServiceMessageTypes.class));
    }
    myJavaParameters.getProgramParametersList().add(JUnitStarter.IDE_VERSION + JUnitStarter.VERSION);
    for (RunConfigurationExtension ext : Extensions.getExtensions(RunConfigurationExtension.EP_NAME)) {
      ext.updateJavaParameters(myConfiguration, myJavaParameters, getRunnerSettings());
    }

    final Object[] listeners = Extensions.getExtensions(IDEAJUnitListener.EP_NAME);
    final StringBuilder buf = new StringBuilder();
    for (final Object listener : listeners) {
      boolean enabled = true;
      for (RunConfigurationExtension ext : Extensions.getExtensions(RunConfigurationExtension.EP_NAME)) {
        if (ext.isListenerDisabled(myConfiguration, listener, getRunnerSettings())) {
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
        myListenersFile = FileUtil.createTempFile("junit_listeners_", "");
        myListenersFile.deleteOnExit();
        myJavaParameters.getProgramParametersList().add("@@" + myListenersFile.getPath());
        FileUtil.writeToFile(myListenersFile, buf.toString().getBytes(CharsetToolkit.UTF8_CHARSET));
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  @Override
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

  @Override
  public ExecutionResult execute(final Executor executor, @NotNull final ProgramRunner runner) throws ExecutionException {
    final boolean smRunner = Registry.is("junit_sm_runner", false);
    if (smRunner) {
      myJavaParameters.getVMParametersList().add("-Didea.junit.sm_runner");
    }
    final JUnitProcessHandler handler = createHandler(executor);
    final RunnerSettings runnerSettings = getRunnerSettings();
    JavaRunConfigurationExtensionManager.getInstance().attachExtensionsToProcess(myConfiguration, handler, runnerSettings);
    if (smRunner) {
      return useSmRunner(executor, handler);
    }
    final TestProxy unboundOutputRoot = new TestProxy(new RootTestInfo());
    final JUnitConsoleProperties consoleProperties = new JUnitConsoleProperties(myConfiguration, executor);
    final JUnitTreeConsoleView consoleView = new JUnitTreeConsoleView(consoleProperties, myEnvironment, unboundOutputRoot);
    consoleView.initUI();
    consoleView.attachToProcess(handler);
    unboundOutputRoot.setPrinter(consoleView.getPrinter());
    Disposer.register(consoleView, unboundOutputRoot);
    final TestsPacketsReceiver packetsReceiver = new TestsPacketsReceiver(consoleView, unboundOutputRoot) {
      @Override
      public synchronized void notifyStart(TestProxy root) {
        if (!isRunning()) return;
        super.notifyStart(root);
        unboundOutputRoot.addChild(root);
        if (myConfiguration.isSaveOutputToFile()) {
          unboundOutputRoot.setOutputFilePath(myConfiguration.getOutputFilePath());
        }
        final JUnitRunningModel model = getModel();
        if (model != null) {
          handler.getOut().setDispatchListener(model.getNotifier());
          Disposer.register(model, new Disposable() {
            @Override
            public void dispose() {
              handler.getOut().setDispatchListener(DispatchListener.DEAF);
            }
          });
          consoleView.attachToModel(model);
        }
      }
    };
    Disposer.register(consoleView, packetsReceiver);

    final DeferredActionsQueue queue = new DeferredActionsQueueImpl();
    handler.getOut().setPacketDispatcher(packetsReceiver, queue);
    handler.getErr().setPacketDispatcher(packetsReceiver, queue);

    handler.addProcessListener(new ProcessAdapter() {
      private boolean myStarted = false;
      @Override
      public void startNotified(ProcessEvent event) {
        myStarted = true;
      }

      @Override
      public void processTerminated(ProcessEvent event) {
        handler.removeProcessListener(this);
        if (myTempFile != null) {
          FileUtil.delete(myTempFile);
        }
        if (myListenersFile != null) {
          FileUtil.delete(myListenersFile);
        }
        final Runnable runnable = new Runnable() {
          @Override
          public void run() {
            unboundOutputRoot.flush();
            packetsReceiver.checkTerminated();
            final JUnitRunningModel model = packetsReceiver.getModel();
            notifyByBalloon(model, myStarted, consoleProperties);
          }
        };
        handler.getOut().addRequest(runnable, queue);
      }

      @Override
      public void onTextAvailable(final ProcessEvent event, final Key outputType) {
        final String text = event.getText();
        final ConsoleViewContentType consoleViewType = ConsoleViewContentType.getConsoleViewType(outputType);
        final Printable printable = new Printable() {
          @Override
          public void printOn(final Printer printer) {
            printer.print(text, consoleViewType);
          }
        };
        final Extractor extractor;
        if (consoleViewType == ConsoleViewContentType.ERROR_OUTPUT ||
            consoleViewType == ConsoleViewContentType.SYSTEM_OUTPUT) {
          extractor = handler.getErr();
        }
        else {
          extractor = handler.getOut();
        }
        extractor.getEventsDispatcher().processOutput(printable);
      }
    });

    final RerunFailedTestsAction rerunFailedTestsAction = new RerunFailedTestsAction(consoleView);
    rerunFailedTestsAction.init(consoleProperties, myEnvironment);
    rerunFailedTestsAction.setModelProvider(new Getter<TestFrameworkRunningModel>() {
      @Override
      public TestFrameworkRunningModel get() {
        return packetsReceiver.getModel();
      }
    });

    final DefaultExecutionResult result = new DefaultExecutionResult(consoleView, handler);
    result.setRestartActions(rerunFailedTestsAction);
    return result;
  }

  private ExecutionResult useSmRunner(Executor executor, JUnitProcessHandler handler) {
    TestConsoleProperties testConsoleProperties = new SMTRunnerConsoleProperties(myConfiguration, JUNIT_TEST_FRAMEWORK_NAME, executor);

    testConsoleProperties.setIfUndefined(TestConsoleProperties.HIDE_PASSED_TESTS, false);

    BaseTestsOutputConsoleView smtConsoleView = SMTestRunnerConnectionUtil.createConsoleWithCustomLocator(
      JUNIT_TEST_FRAMEWORK_NAME,
      testConsoleProperties,
      myEnvironment, null);


    Disposer.register(myProject, smtConsoleView);

    final ConsoleView consoleView = smtConsoleView;
    consoleView.attachToProcess(handler);

    final RerunFailedTestsAction rerunFailedTestsAction = new RerunFailedTestsAction(consoleView);
    rerunFailedTestsAction.init(testConsoleProperties, myEnvironment);
    rerunFailedTestsAction.setModelProvider(new Getter<TestFrameworkRunningModel>() {
      @Override
      public TestFrameworkRunningModel get() {
        return ((SMTRunnerConsoleView)consoleView).getResultsViewer();
      }
    });

    final DefaultExecutionResult result = new DefaultExecutionResult(consoleView, handler);
    result.setRestartActions(rerunFailedTestsAction);
    return result;
  }

  protected void notifyByBalloon(JUnitRunningModel model, boolean started, JUnitConsoleProperties consoleProperties) {
    String comment;
    if (model != null) {
      final CompletionEvent done = model.getProgress().getDone();
      comment = done != null ? done.getComment() : null;
    }
    else {
      comment = null;
    }
    TestsUIUtil.notifyByBalloon(myProject, started, model != null ? model.getRoot() : null, consoleProperties, comment);
  }

  protected JUnitProcessHandler createHandler(Executor executor) throws ExecutionException {
    appendForkInfo(executor);
    return JUnitProcessHandler.runCommandLine(CommandLineBuilder.createFromJavaParameters(myJavaParameters, myProject, true));
  }

  private boolean forkPerModule() {
    final String workingDirectory = myConfiguration.getWorkingDirectory();
    return JUnitConfiguration.TEST_PACKAGE.equals(myConfiguration.getPersistentData().TEST_OBJECT) &&
           myConfiguration.getPersistentData().getScope() != TestSearchScope.SINGLE_MODULE &&
           ("$" + PathMacroUtil.MODULE_DIR_MACRO_NAME + "$").equals(workingDirectory);
  }

  private void appendForkInfo(Executor executor) throws ExecutionException {
    final String forkMode = myConfiguration.getForkMode();
    if (Comparing.strEqual(forkMode, "none")) {
      final String workingDirectory = myConfiguration.getWorkingDirectory();
      if (!JUnitConfiguration.TEST_PACKAGE.equals(myConfiguration.getPersistentData().TEST_OBJECT) ||
          myConfiguration.getPersistentData().getScope() == TestSearchScope.SINGLE_MODULE ||
          !("$" + PathMacroUtil.MODULE_DIR_MACRO_NAME + "$").equals(workingDirectory)) {
        return;
      }
    }

    if (getRunnerSettings() != null) {
      final String actionName = executor.getActionName();
      throw new CantRunException(actionName + " is disabled in fork mode.<br/>Please change fork mode to &lt;none&gt; to " + actionName.toLowerCase() + ".");
    }

    final JavaParameters javaParameters = getJavaParameters();
    final Sdk jdk = javaParameters.getJdk();
    if (jdk == null) {
      throw new ExecutionException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"));
    }

    try {
      final File tempFile = FileUtil.createTempFile("command.line", "", true);
      final PrintWriter writer = new PrintWriter(tempFile, CharsetToolkit.UTF8);
      try {
        writer.println(((JavaSdkType)jdk.getSdkType()).getVMExecutablePath(jdk));
        for (String vmParameter : javaParameters.getVMParametersList().getList()) {
          writer.println(vmParameter);
        }
        writer.println("-classpath");
        writer.println(javaParameters.getClassPath().getPathsString());
      }
      finally {
        writer.close();
      }

      myJavaParameters.getProgramParametersList().add("@@@" + forkMode + ',' + tempFile.getAbsolutePath());
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  protected <T> void addClassesListToJavaParameters(Collection<? extends T> elements, Function<T, String> nameFunction, String packageName,
                                                boolean createTempFile,
                                                boolean junit4) {
    try {
      if (createTempFile) {
        myTempFile = FileUtil.createTempFile("idea_junit", ".tmp");
        myTempFile.deleteOnExit();
        myJavaParameters.getProgramParametersList().add("@" + myTempFile.getAbsolutePath());
      }

      final Map<String, List<String>> perModule = forkPerModule() ? new TreeMap<String, List<String>>() : null;
      final PrintWriter writer = new PrintWriter(myTempFile, CharsetToolkit.UTF8);
      try {
        writer.println(packageName);
        final List<String> testNames = new ArrayList<String>();
        for (final T element : elements) {
          final String name = nameFunction.fun(element);
          if (name == null) {
            LOG.error("invalid element " + element);
            return;
          }

          if (perModule != null && element instanceof PsiElement) {
            final Module module = ModuleUtilCore.findModuleForPsiElement((PsiElement)element);
            if (module != null) {
              final String moduleDir = PathMacroUtil.getModuleDir(module.getModuleFilePath());
              List<String> list = perModule.get(moduleDir);
              if (list == null) {
                list = new ArrayList<String>();
                perModule.put(moduleDir, list);
              }
              list.add(name);
            }
          } else {
            testNames.add(name);
          }
        }
        if (perModule != null) {
          for (List<String> perModuleClasses : perModule.values()) {
            Collections.sort(perModuleClasses);
            testNames.addAll(perModuleClasses);
          }
        } else {
          Collections.sort(testNames); //sort tests in FQN order
        }
        for (String testName : testNames) {
          writer.println(testName);
        }
      }
      finally {
        writer.close();
      }

      if (perModule != null && perModule.size() > 1) {
        final PrintWriter wWriter = new PrintWriter(myWorkingDirsFile, CharsetToolkit.UTF8);
        try {
          wWriter.println(packageName);
          for (String workingDir : perModule.keySet()) {
            wWriter.println(workingDir);
            final List<String> classNames = perModule.get(workingDir);
            wWriter.println(classNames.size());
            for (String className : classNames) {
              wWriter.println(className);
            }
          }
        } finally {
          wWriter.close();
        }
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
