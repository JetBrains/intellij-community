package com.intellij.execution.junit;

import com.intellij.ExtensionPoints;
import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageSuite;
import com.intellij.coverage.DefaultCoverageFileProvider;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.junit2.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.junit2.ui.JUnitTreeConsoleView;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ex.PathUtilEx;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.rt.execution.junit.JUnitStarter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;

public abstract class TestObject implements JavaCommandLine {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit.TestObject");

  private static final String MESSAGE = ExecutionBundle.message("configuration.not.speficied.message");

  protected JavaParameters myJavaParameters;
  private final Project myProject;
  protected final JUnitConfiguration myConfiguration;
  private RunnerSettings myRunnerSettings;
  private ConfigurationPerRunnerSettings myConfigurationSettings;
  private File myTempFile = null;
  private CoverageSuite myCurrentCoverageSuite;

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
                                       myConfiguration.ALTERNATIVE_JRE_PATH_ENABLED ? myConfiguration.ALTERNATIVE_JRE_PATH : null);
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
    if (myConfiguration.ALTERNATIVE_JRE_PATH_ENABLED){
      if (myConfiguration.ALTERNATIVE_JRE_PATH == null ||
          myConfiguration.ALTERNATIVE_JRE_PATH.length() == 0 ||
          !JavaSdk.checkForJre(myConfiguration.ALTERNATIVE_JRE_PATH)){
        throw new RuntimeConfigurationWarning(
          ExecutionBundle.message("jre.path.is.not.valid.jre.home.error.mesage", myConfiguration.ALTERNATIVE_JRE_PATH));
      }
    }
  }

  public SourceScope getSourceScope() {
    return SourceScope.modulesWithDependencies(myConfiguration.getModules());
  }

  protected void initialize() throws ExecutionException {
    EnvironmentVariablesComponent.setupEnvs(myJavaParameters,
                                            myConfiguration.getPersistentData().ENV_VARIABLES, myConfiguration.getPersistentData().PASS_PARENT_ENVS);
    JavaParametersUtil.configureConfiguration(myJavaParameters, myConfiguration);
    myJavaParameters.setMainClass(JUnitConfiguration.JUNIT_START_CLASS);
    final Module module = myConfiguration.getConfigurationModule().getModule();
    if (myJavaParameters.getJdk() == null){
      myJavaParameters.setJdk(module != null
                              ? ModuleRootManager.getInstance(module).getJdk()
                              : ProjectRootManager.getInstance(myProject).getProjectJdk());
    }
    final Object[] patchers = Extensions.getExtensions(ExtensionPoints.JUNIT_PATCHER);
    for (Object patcher : patchers) {
      ((JUnitPatcher)patcher).patchJavaParameters(module, myJavaParameters);
    }
    PathUtilEx.addRtJar(myJavaParameters.getClassPath());
    PathUtilEx.addJunit4RtJar(myJavaParameters.getClassPath());
    myJavaParameters.getProgramParametersList().add(JUnitStarter.IDE_VERSION + JUnitStarter.VERSION);
    if (!(myRunnerSettings.getData() instanceof DebuggingRunnerData) && myConfiguration.isCoverageEnabled()) {
      final String coverageFileName = myConfiguration.getCoverageFilePath();
      final long lastCoverageTime = System.currentTimeMillis();
      final CoverageDataManager coverageDataManager = CoverageDataManager.getInstance(myProject);
      myCurrentCoverageSuite = coverageDataManager.addCoverageSuite(
        myConfiguration.getName(),
        new DefaultCoverageFileProvider(coverageFileName),
        myConfiguration.getCoveragePatterns(), lastCoverageTime,
        !myConfiguration.isMergeWithPreviousResults()
      );
      myConfiguration.appendCoverageArgument(myJavaParameters);
    }
  }

  public JavaParameters getJavaParameters() throws ExecutionException {
    if (myJavaParameters == null) {
      myJavaParameters = new JavaParameters();
      initialize();
    }
    return myJavaParameters;
  }

  public ExecutionResult execute() throws ExecutionException {
    final ProcessHandler handler = startProcess();
    final ConsoleView consoleView;

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      consoleView = null;
    }
    else {
      consoleView = new JUnitTreeConsoleView(new JUnitConsoleProperties(myConfiguration),getRunnerSettings(), getConfigurationSettings());
      consoleView.attachToProcess(handler);
    }

    return new DefaultExecutionResult(consoleView, handler, AnAction.EMPTY_ARRAY);
  }

  private ProcessHandler startProcess() throws ExecutionException {
    final ProcessHandler handler = JUnitProcessHandler.runJava(getJavaParameters());
    handler.addProcessListener(new ProcessAdapter() {
      public void processTerminated(final ProcessEvent event) {
        if (myTempFile != null) {
          myTempFile.delete();
        }
        final CoverageDataManager coverageDataManager = CoverageDataManager.getInstance(myProject);
        if (myCurrentCoverageSuite != null) {
          coverageDataManager.coverageGathered(myCurrentCoverageSuite);
        }
      }
    });
    return handler;
  }


  protected void addClassesListToJavaParameters(Collection<? extends PsiElement> elements, String packageName) {
    try {
      myTempFile = File.createTempFile("idea_junit", ".tmp");
      myTempFile.deleteOnExit();
      myJavaParameters.getProgramParametersList().add("@" + myTempFile.getAbsolutePath());

      final PrintWriter writer = new PrintWriter(new FileWriter(myTempFile));
      try {
        writer.println(packageName);
        for (final PsiElement element : elements) {
          final String name;
          if (element instanceof PsiClass) {
            name = ExecutionUtil.getRuntimeQualifiedName((PsiClass)element);
          }
          else if (element instanceof PsiMethod){
            PsiMethod method = (PsiMethod)element;
            name = ExecutionUtil.getRuntimeQualifiedName(method.getContainingClass()) + "," + method.getName();
          }
          else {
            LOG.error("invalid element " + element);
            return;
          }
          writer.println(name);
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
