/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.execution.*;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.junit.testDiscovery.TestBySource;
import com.intellij.execution.junit.testDiscovery.TestsByChanges;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.SearchForTestsTask;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.junit5.JUnit5IdeaTestRunner;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.rt.execution.junit.IDEAJUnitListener;
import com.intellij.rt.execution.junit.JUnitStarter;
import com.intellij.rt.execution.junit.RepeatCount;
import com.intellij.rt.execution.testFrameworks.ForkedDebuggerHelper;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.engine.JupiterTestEngine;
import org.junit.platform.commons.JUnitException;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.vintage.engine.VintageTestEngine;

import java.io.File;
import java.io.IOException;
import java.util.*;

public abstract class TestObject extends JavaTestFrameworkRunnableState<JUnitConfiguration> {
  private static final String DEBUG_RT_PATH = "idea.junit_rt.path";

  protected static final Logger LOG = Logger.getInstance(TestObject.class);

  private static final String MESSAGE = ExecutionBundle.message("configuration.not.speficied.message");
  @NonNls private static final String JUNIT_TEST_FRAMEWORK_NAME = "JUnit";

  private final JUnitConfiguration myConfiguration;
  protected File myListenersFile;
  public static TestObject fromString(final String id,
                                      final JUnitConfiguration configuration,
                                      @NotNull ExecutionEnvironment environment) {
    if (JUnitConfiguration.TEST_METHOD.equals(id)) {
      return new TestMethod(configuration, environment);
    }
    if (JUnitConfiguration.TEST_CLASS.equals(id)) {
      return new TestClass(configuration, environment);
    }
    if (JUnitConfiguration.TEST_PACKAGE.equals(id)){
      return new TestPackage(configuration, environment);
    }
    if (JUnitConfiguration.TEST_DIRECTORY.equals(id)) {
      return new TestDirectory(configuration, environment);
    }
    if (JUnitConfiguration.TEST_CATEGORY.equals(id)) {
      return new TestCategory(configuration, environment);
    }
    if (JUnitConfiguration.TEST_PATTERN.equals(id)) {
      return new TestsPattern(configuration, environment);
    }
    if (JUnitConfiguration.BY_SOURCE_POSITION.equals(id)) {
      return new TestBySource(configuration, environment);
    }
    if (JUnitConfiguration.BY_SOURCE_CHANGES.equals(id)) {
      return new TestsByChanges(configuration, environment);
    }
    LOG.info(MESSAGE + id);
    return null;
  }

  public Module[] getModulesToCompile() {
    final SourceScope sourceScope = getSourceScope();
    return sourceScope != null ? sourceScope.getModulesToCompile() : Module.EMPTY_ARRAY;
  }

  protected TestObject(JUnitConfiguration configuration, ExecutionEnvironment environment) {
    super(environment);
    myConfiguration = configuration;
  }

  public abstract String suggestActionName();

  public abstract RefactoringElementListener getListener(PsiElement element, JUnitConfiguration configuration);

  public abstract boolean isConfiguredByElement(JUnitConfiguration configuration,
                                                PsiClass testClass,
                                                PsiMethod testMethod,
                                                PsiPackage testPackage,
                                                PsiDirectory testDir);

  public void checkConfiguration() throws RuntimeConfigurationException{
    JavaParametersUtil.checkAlternativeJRE(getConfiguration());
    ProgramParametersUtil.checkWorkingDirectoryExist(getConfiguration(), getConfiguration().getProject(),
                                                     getConfiguration().getConfigurationModule().getModule());
  }

  @Nullable
  public SourceScope getSourceScope() {
    return SourceScope.modules(getConfiguration().getModules());
  }

  @Override
  protected void configureRTClasspath(JavaParameters javaParameters) {
    final String path = System.getProperty(DEBUG_RT_PATH);
    javaParameters.getClassPath().add(path != null ? path : PathUtil.getJarPathForClass(JUnitStarter.class));
  }

  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    JavaParameters javaParameters = super.createJavaParameters();
    javaParameters.setMainClass(JUnitConfiguration.JUNIT_START_CLASS);
    javaParameters.getProgramParametersList().add(JUnitStarter.IDE_VERSION + JUnitStarter.VERSION);

    final StringBuilder buf = new StringBuilder();
    collectListeners(javaParameters, buf, IDEAJUnitListener.EP_NAME, "\n");
    if (buf.length() > 0) {
      try {
        myListenersFile = FileUtil.createTempFile("junit_listeners_", "", true);
        javaParameters.getProgramParametersList().add("@@" + myListenersFile.getPath());
        FileUtil.writeToFile(myListenersFile, buf.toString().getBytes(CharsetToolkit.UTF8_CHARSET));
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    final Project project = getConfiguration().getProject();
    final SourceScope sourceScope = getSourceScope();
    if (isJUnit5(getConfiguration().getConfigurationModule().getModule(), sourceScope, project)) {
      javaParameters.getProgramParametersList().add(JUnitStarter.JUNIT5_PARAMETER);
      javaParameters.getClassPath().add(PathUtil.getJarPathForClass(JUnit5IdeaTestRunner.class));

      final PathsList classPath = javaParameters.getClassPath();
      classPath.add(PathUtil.getJarPathForClass(TestExecutionListener.class));
      classPath.add(PathUtil.getJarPathForClass(JupiterTestEngine.class));
      classPath.add(PathUtil.getJarPathForClass(JUnitException.class));
      classPath.add(PathUtil.getJarPathForClass(TestEngine.class));
      classPath.add(PathUtil.getJarPathForClass(JUnitPlatform.class));
      try {
        JUnitUtil.getTestCaseClass(sourceScope);
        classPath.add(PathUtil.getJarPathForClass(VintageTestEngine.class));
      }
      catch (JUnitUtil.NoJUnitException ignore) {
      }
    }
    
    return javaParameters;
  }

  public static boolean isJUnit5(@Nullable Module module, @Nullable SourceScope sourceScope, Project project) {
    return JUnitUtil.isJUnit5(module != null ? GlobalSearchScope.moduleRuntimeScope(module, true)
                                             : sourceScope != null ? sourceScope.getLibrariesScope() : GlobalSearchScope.allScope(project),
                              project);
  }

  @NotNull
  protected OSProcessHandler createHandler(Executor executor) throws ExecutionException {
    appendForkInfo(executor);
    final String repeatMode = getConfiguration().getRepeatMode();
    if (!RepeatCount.ONCE.equals(repeatMode)) {
      final int repeatCount = getConfiguration().getRepeatCount();
      final String countString = RepeatCount.N.equals(repeatMode) && repeatCount > 0
                                 ? RepeatCount.getCountString(repeatCount)
                                 : repeatMode;
      getJavaParameters().getProgramParametersList().add(countString);
    }

    final OSProcessHandler processHandler = new KillableColoredProcessHandler(createCommandLine());
    ProcessTerminatedListener.attach(processHandler);
    final SearchForTestsTask searchForTestsTask = createSearchingForTestsTask();
    if (searchForTestsTask != null) {
      searchForTestsTask.attachTaskToProcess(processHandler);
    }
    return processHandler;
  }

  @NotNull
  @Override
  protected String getForkMode() {
    return getConfiguration().getForkMode();
  }

  protected <T> void addClassesListToJavaParameters(Collection<? extends T> elements, Function<T, String> nameFunction, String packageName,
                                                    boolean createTempFile, JavaParameters javaParameters) throws CantRunException {
    try {
      if (createTempFile) {
        createTempFiles(javaParameters);
      }

      final Map<Module, List<String>> perModule = forkPerModule() ? new TreeMap<>(
        (o1, o2) -> StringUtil.compare(o1.getName(), o2.getName(), true)) : null;

      final List<String> testNames = new ArrayList<>();

      for (final T element : elements) {
        final String name = nameFunction.fun(element);
        if (name == null) {
          continue;
        }

        final PsiElement psiElement = retrievePsiElement(element);
        if (perModule != null && psiElement != null) {
          final Module module = ModuleUtilCore.findModuleForPsiElement(psiElement);
          if (module != null) {
            List<String> list = perModule.get(module);
            if (list == null) {
              list = new ArrayList<>();
              perModule.put(module, list);
            }
            list.add(name);
          }
        }
        else {
          testNames.add(name);
        }
      }
      final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
      if (perModule != null) {
        for (List<String> perModuleClasses : perModule.values()) {
          Collections.sort(perModuleClasses);
          testNames.addAll(perModuleClasses);
        }
      }
      else if (JUnitConfiguration.TEST_PACKAGE.equals(data.TEST_OBJECT)) {
        Collections.sort(testNames); //sort tests in FQN order
      }

      final String category = JUnitConfiguration.TEST_CATEGORY.equals(data.TEST_OBJECT) ? data.getCategory() : "";
      JUnitStarter.printClassesList(testNames, packageName, category, myTempFile);

      writeClassesPerModule(packageName, javaParameters, perModule);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  protected PsiElement retrievePsiElement(Object element) {
    return element instanceof PsiElement ? (PsiElement)element : null;
  }

  @Override
  protected void deleteTempFiles() {
    super.deleteTempFiles();
    if (myListenersFile != null) {
      FileUtil.delete(myListenersFile);
    }
  }

  @NotNull
  protected String getFrameworkName() {
    return JUNIT_TEST_FRAMEWORK_NAME;
  }

  @NotNull
  protected String getFrameworkId() {
    return "junit";
  }

  protected void passTempFile(ParametersList parametersList, String tempFilePath) {
    parametersList.add("@" + tempFilePath);
  }

  @NotNull
  public JUnitConfiguration getConfiguration() {
    return myConfiguration;
  }

  @Override
  protected TestSearchScope getScope() {
    return getConfiguration().getPersistentData().getScope();
  }

  protected void passForkMode(String forkMode, File tempFile, JavaParameters parameters) throws ExecutionException {
    parameters.getProgramParametersList().add("@@@" + forkMode + ',' + tempFile.getAbsolutePath());
    if (getForkSocket() != null) {
      parameters.getProgramParametersList().add(ForkedDebuggerHelper.DEBUG_SOCKET + getForkSocket().getLocalPort());
    }
  }
}
