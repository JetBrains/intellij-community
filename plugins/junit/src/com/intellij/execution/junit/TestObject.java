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
import com.intellij.junit4.JUnit4IdeaTestRunner;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.rt.execution.junit.IDEAJUnitListener;
import com.intellij.rt.execution.junit.JUnitStarter;
import com.intellij.rt.execution.junit.RepeatCount;
import com.intellij.rt.execution.testFrameworks.ForkedDebuggerHelper;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.opentest4j.MultipleFailuresError;

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

    //include junit5 listeners for the case custom junit 5 engines would be detected on runtime
    javaParameters.getClassPath().add(getJUnit5RtFile());
  }

  public static File getJUnit5RtFile() {
    File junit4Rt = new File(PathUtil.getJarPathForClass(JUnit4IdeaTestRunner.class));
    String junit5Name = junit4Rt.getName().replace("junit", "junit5");
    return new File(junit4Rt.getParent(), junit5Name);
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
    GlobalSearchScope globalSearchScope = getScopeForJUnit(getConfiguration().getConfigurationModule().getModule(), project);
    String preferredRunner = getRunner();
    if (JUnitStarter.JUNIT5_PARAMETER.equals(preferredRunner)) {
      final PathsList classPath = javaParameters.getClassPath();
      File lib = new File(PathUtil.getJarPathForClass(MultipleFailuresError.class)).getParentFile();
      File[] files = lib.listFiles();
      if (files != null) {
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        for (File file : files) {
          String fileName = file.getName();
          if (fileName.startsWith("junit-platform-launcher-") && !hasPackageWithDirectories(psiFacade, "org.junit.platform.launcher", globalSearchScope) ||

              fileName.startsWith("junit-platform-") && !hasPackageWithDirectories(psiFacade, "org.junit.platform", globalSearchScope) ||

              fileName.startsWith("junit-platform-engine-") && !hasPackageWithDirectories(psiFacade, "org.junit.platform.engine", globalSearchScope) ||

              fileName.startsWith("junit-jupiter-engine-") && !hasPackageWithDirectories(psiFacade, "org.junit.jupiter.engine", globalSearchScope) &&
                                                               hasPackageWithDirectories(psiFacade, JUnitUtil.TEST5_PACKAGE_FQN, globalSearchScope)) {
            classPath.add(file.getAbsolutePath());
          }
          else if (fileName.startsWith("junit-vintage-engine-") && !hasPackageWithDirectories(psiFacade, "org.junit.vintage", globalSearchScope)) {
            if (hasPackageWithDirectories(psiFacade, "junit.framework", globalSearchScope)) {
              classPath.add(file.getAbsolutePath());
            }
          }
        }
      }
    }
    if (preferredRunner != null) {
      javaParameters.getProgramParametersList().add(preferredRunner);
    }
    
    return javaParameters;
  }

  private static boolean hasPackageWithDirectories(JavaPsiFacade psiFacade,
                                                   String packageQName,
                                                   GlobalSearchScope globalSearchScope) {
    PsiPackage aPackage = psiFacade.findPackage(packageQName);
    return aPackage != null && aPackage.getDirectories(globalSearchScope).length > 0;
  }

  private static GlobalSearchScope getScopeForJUnit(@Nullable Module module, Project project) {
    return module != null ? GlobalSearchScope.moduleRuntimeScope(module, true) : GlobalSearchScope.allScope(project);
  }

  public static GlobalSearchScope getScopeForJUnit(JUnitConfiguration configuration) {
    return getScopeForJUnit(configuration.getConfigurationModule().getModule(),
                            configuration.getProject() );
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

  @Override
  protected boolean isIdBasedTestTree() {
    return JUnitStarter.JUNIT5_PARAMETER.equals(getRunner());
  }

  @NotNull
  @Override
  protected String getForkMode() {
    return getConfiguration().getForkMode();
  }

  protected <T> void addClassesListToJavaParameters(Collection<? extends T> elements,
                                                    Function<T, String> nameFunction,
                                                    String packageName,
                                                    boolean createTempFile, JavaParameters javaParameters) throws CantRunException {
    try {
      if (createTempFile) {
        createTempFiles(javaParameters);
      }

      final Map<Module, List<String>> perModule = forkPerModule() ? new TreeMap<>(
        (o1, o2) -> StringUtil.compare(o1.getName(), o2.getName(), true)) : null;

      final List<String> testNames = new ArrayList<>();

      if (elements.isEmpty() && perModule != null) {
        final SourceScope sourceScope = getSourceScope();
        Project project = getConfiguration().getProject();
        if (sourceScope != null && packageName != null 
            && !JUnitStarter.JUNIT5_PARAMETER.equals(getRunner())) {
          final PsiPackage aPackage = JavaPsiFacade.getInstance(getConfiguration().getProject()).findPackage(packageName);
          if (aPackage != null) {
            final TestSearchScope scope = getScope();
            if (scope != null) {
              final GlobalSearchScope configurationSearchScope = GlobalSearchScopesCore.projectTestScope(project)
                .intersectWith(sourceScope.getGlobalSearchScope());
              final PsiDirectory[] directories = aPackage.getDirectories(configurationSearchScope);
              for (PsiDirectory directory : directories) {
                Module module = ModuleUtilCore.findModuleForFile(directory.getVirtualFile(), project);
                if (module != null) {
                  perModule.put(module, Collections.emptyList());
                }
              }
            }
          }
        }
      }
      
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
      final String filters = JUnitConfiguration.TEST_PATTERN.equals(data.TEST_OBJECT) ? data.getPatternPresentation() : "";
      JUnitStarter.printClassesList(testNames, packageName, category, filters, myTempFile);

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

  private String myRunner;

  protected String getRunner() {
    if (myRunner == null) {
      myRunner = getRunnerInner();
    }
    return myRunner;
  }

  private String getRunnerInner() {
    final GlobalSearchScope globalSearchScope = getScopeForJUnit(myConfiguration);
    JUnitConfiguration.Data data = myConfiguration.getPersistentData();
    Project project = myConfiguration.getProject();
    boolean isMethodConfiguration = JUnitConfiguration.TEST_METHOD.equals(data.TEST_OBJECT);
    boolean isClassConfiguration = JUnitConfiguration.TEST_CLASS.equals(data.TEST_OBJECT);
    final PsiClass psiClass = isMethodConfiguration || isClassConfiguration
                              ? JavaExecutionUtil.findMainClass(project, data.getMainClassName(), globalSearchScope) : null;
    if (psiClass != null) {
      if (JUnitUtil.isJUnit5TestClass(psiClass, false)) {
        return JUnitStarter.JUNIT5_PARAMETER;
      }

      if (isClassConfiguration || JUnitUtil.isJUnit4TestClass(psiClass)) {
        return JUnitStarter.JUNIT4_PARAMETER;
      }

      final String methodName = data.getMethodName();
      final PsiMethod[] methods = psiClass.findMethodsByName(methodName, true);
      for (PsiMethod method : methods) {
        if (JUnitUtil.isTestAnnotated(method)) {
          return JUnitStarter.JUNIT4_PARAMETER;
        }
      }
      return JUnitStarter.JUNIT3_PARAMETER;
    }
    return JUnitUtil.isJUnit5(globalSearchScope, project) || isCustomJUnit5(globalSearchScope) ? JUnitStarter.JUNIT5_PARAMETER : null;
  }

  private boolean isCustomJUnit5(GlobalSearchScope globalSearchScope) {
    Project project = myConfiguration.getProject();
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    if (DumbService.getInstance(project)
          .computeWithAlternativeResolveEnabled(() -> {
            @Nullable PsiClass testEngine = ReadAction.compute(() -> psiFacade.findClass(JUnitCommonClassNames.ORG_JUNIT_PLATFORM_ENGINE_TEST_ENGINE, globalSearchScope));
            return testEngine;
          }) == null) {
      return false;
    }

    ClassLoader loader = TestClassCollector.createUsersClassLoader(myConfiguration);
    try {
      ServiceLoader<?> serviceLoader = ServiceLoader.load(Class.forName(JUnitCommonClassNames.ORG_JUNIT_PLATFORM_ENGINE_TEST_ENGINE, false, loader), loader);
      for (Object engine : serviceLoader) {
        String engineClassName = engine.getClass().getName();
        if (!"org.junit.jupiter.engine.JupiterTestEngine".equals(engineClassName) &&
            !"org.junit.vintage.engine.VintageTestEngine".equals(engineClassName)) {
          return true;
        }
      }
      return false;
    }
    catch (Throwable e) {
      return false;
    }
  }
}
