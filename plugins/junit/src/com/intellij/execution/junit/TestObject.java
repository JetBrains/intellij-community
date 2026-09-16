// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.JUnitBundle;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.JavaTestFrameworkRunnableState;
import com.intellij.execution.Location;
import com.intellij.execution.TestClassCollector;
import com.intellij.execution.configurations.CompositeParameterTargetedValue;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.junit.testDiscovery.TestBySource;
import com.intellij.execution.junit.testDiscovery.TestsByChanges;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.TargetProgressIndicator;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.ide.JavaUiBundle;
import com.intellij.jarRepository.JarRepositoryManager;
import com.intellij.java.JavaBundle;
import com.intellij.junit4.JUnit4IdeaTestRunner;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ArchivedCompilationContextUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.rt.execution.junit.IDEAJUnitListener;
import com.intellij.rt.execution.junit.RepeatCount;
import com.intellij.rt.execution.testFrameworks.ForkedDebuggerHelper;
import com.intellij.rt.junit.JUnitStarter;
import com.intellij.testIntegration.TestFramework;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.text.VersionComparatorUtil;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.intellij.execution.junit.JUnitLauncherDependencies.LAUNCHER_MODULE_NAME;
import static com.intellij.execution.junit.JUnitLauncherDependencies.isCustomJUnit;

public abstract class TestObject extends JavaTestFrameworkRunnableState<JUnitConfiguration> implements PossiblyDumbAware {
  protected static final Logger LOG = Logger.getInstance(TestObject.class);

  private static final @NonNls String DEBUG_RT_PATH = "idea.junit_rt.path";
  private static final @NlsSafe String JUNIT_TEST_FRAMEWORK_NAME = "JUnit";

  private static final @NonNls String DEFAULT_RUNNER = "default";
  private static final int DEFAULT_SHUTDOWN_TIMEOUT = 600;

  private final JUnitConfiguration myConfiguration;
  protected Path myListenersFile;

  private final Map<Module, JavaParameters> myAdditionalJarsForModuleFork = new HashMap<>();

  static final Map<String, String> RUNNER_VERSIONS = Map.of(
    JUnitStarter.JUNIT3_PARAMETER, "3",
    JUnitStarter.JUNIT4_PARAMETER, "4",
    JUnitStarter.JUNIT5_PARAMETER, "5",
    JUnitStarter.JUNIT6_PARAMETER, "6"
  );

  protected static final Set<String> JUPITER_RUNNERS = Set.of(JUnitStarter.JUNIT5_PARAMETER, JUnitStarter.JUNIT6_PARAMETER);

  protected TestObject(JUnitConfiguration configuration, ExecutionEnvironment environment) {
    super(environment);
    myConfiguration = configuration;
  }

  protected <T> void addClassesListToJavaParameters(Collection<? extends T> elements,
                                                    Function<? super T, String> nameFunction,
                                                    String packageName,
                                                    boolean createTempFile, JavaParameters javaParameters) {
    JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    addClassesListToJavaParameters(elements, nameFunction, packageName, createTempFile, javaParameters,
                                   JUnitConfiguration.TEST_PATTERN.equals(data.TEST_OBJECT) ? data.getPatternPresentation() : "");
  }

  protected <T> void addClassesListToJavaParameters(Collection<? extends T> elements,
                                                    Function<? super T, String> nameFunction,
                                                    String packageName,
                                                    boolean createTempFile,
                                                    JavaParameters javaParameters,
                                                    @NlsSafe String filters) {
    try {
      if (createTempFile) {
        createTempFiles(javaParameters);
      }

      final Map<Module, List<String>> perModule = forkPerModule() ? new TreeMap<>((o1, o2) -> StringUtil.compare(o1.getName(), o2.getName(), true)) : null;

      final List<String> testNames = new ArrayList<>();

      if (elements.isEmpty() && perModule != null) {
        for (Module module : collectPackageModules(packageName)) {
          perModule.put(module, new ArrayList<>(composeDirectoryFilter(module)));
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
            fillForkModule(perModule, module, name);
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

      final String category = JUnitConfiguration.TEST_CATEGORY.equals(data.TEST_OBJECT) ? data.getCategory() :
                              JUnitConfiguration.TEST_TAGS.equals(data.TEST_OBJECT) ? data.getTags().replaceAll(" ", "")
                                                                                    : "";
      JUnitStarter.printClassesList(testNames, packageName, category, filters, myTempFile);

      writeClassesPerModule(packageName, javaParameters, perModule, filters);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private Set<Module> collectPackageModules(String packageName) {
    Set<Module> result = new HashSet<>();
    final SourceScope sourceScope = getSourceScope();
    final Project project = getConfiguration().getProject();
    if (sourceScope != null && packageName != null && JUPITER_RUNNERS.contains(getRunner())) {
      final PsiPackage aPackage = JavaPsiFacade.getInstance(getConfiguration().getProject()).findPackage(packageName);
      if (aPackage != null) {
        final TestSearchScope scope = getScope();
        if (scope != null) {
          final GlobalSearchScope configurationSearchScope = GlobalSearchScopesCore.projectTestScope(project)
            .intersectWith(sourceScope.getGlobalSearchScope());
          final PsiDirectory[] directories = aPackage.getDirectories(configurationSearchScope);
          for (PsiDirectory directory : directories) {
            ContainerUtil.addIfNotNull(result, ModuleUtilCore.findModuleForFile(directory.getVirtualFile(), project));
          }
        }
      }
    }
    return result;
  }

  protected void fillForkModule(Map<Module, List<String>> perModule, Module module, String name) {
    perModule.computeIfAbsent(module, _ -> new ArrayList<>()).add(name);
  }

  public Module[] getModulesToCompile() {
    final SourceScope sourceScope = getSourceScope();
    return sourceScope != null ? sourceScope.getModulesToCompile() : Module.EMPTY_ARRAY;
  }

  public abstract @NlsActions.ActionText String suggestActionName();

  public abstract RefactoringElementListener getListener(PsiElement element);

  public abstract boolean isConfiguredByElement(JUnitConfiguration configuration,
                                                PsiClass testClass,
                                                PsiMethod testMethod,
                                                PsiPackage testPackage,
                                                PsiDirectory testDir);

  public void checkConfiguration() throws RuntimeConfigurationException {
    JavaParametersUtil.checkAlternativeJRE(getConfiguration());
    ProgramParametersUtil.checkWorkingDirectoryExist(getConfiguration(), getConfiguration().getProject(),
                                                     getConfiguration().getConfigurationModule().getModule());
  }

  public @Nullable SourceScope getSourceScope() {
    return SourceScope.modules(getConfiguration().getModules());
  }

  @Override
  protected void configureRTClasspath(JavaParameters javaParameters, Module module) throws CantRunException {
    final String path = System.getProperty(DEBUG_RT_PATH);
    Sdk jdk = javaParameters.getJdk();
    JavaSdkVersion jdkVersion = JavaSdkVersionUtil.getJavaSdkVersion(jdk);
    if (jdkVersion != null && !JavaSdkUtil.isJdkAtLeast(jdk, JavaSdkVersion.JDK_1_8)) {
      throw new CantRunException(JavaBundle.message("error.message.ide.does.not.support.starting.processes.using.old.java",
                                                    jdkVersion.getDescription()));
    }
    javaParameters.getClassPath().addFirst(path != null ? path : getJUnitRtPath().getAbsolutePath());

    //include junit5 listeners for the case custom junit 5 engines would be detected on runtime
    javaParameters.getClassPath().addFirst(getJUnitRtFile(JUnitStarter.JUNIT5_PARAMETER));

    // Add junit6_rt.jar to the classpath if the runner is -junit6
    String runner = getRunner();
    if (runner.equals(JUnitStarter.JUNIT6_PARAMETER)) {
      javaParameters.getClassPath().addFirst(getJUnitRtFile(JUnitStarter.JUNIT6_PARAMETER));
    }

    appendDownloadedDependenciesForForkedConfigurations(javaParameters, module);
  }

  private void appendDownloadedDependenciesForForkedConfigurations(JavaParameters javaParameters, Module module) {
    if (module != null) {
      JavaParameters parameters = myAdditionalJarsForModuleFork.get(module);
      if (parameters != null) {
        boolean toModulePath = parameters.getClassPath().isEmpty();
        PathsList sourcePath = toModulePath ? parameters.getModulePath() : parameters.getClassPath();
        PathsList targetPath = toModulePath ? javaParameters.getModulePath() : javaParameters.getClassPath();
        for (String dependencyPath : sourcePath.getPathList()) {
          targetPath.addFirst(dependencyPath);
        }
        ParamsGroup group = getJigsawOptions(parameters);
        if (group != null) {
          getOrCreateJigsawOptions(javaParameters).addParameters(group.getParameters());
        }
      }
    }
  }

  public static File getJUnitRtFile(@NotNull String runner) throws CantRunException {
    String version = RUNNER_VERSIONS.getOrDefault(runner, "5");
    File junit4Rt = getJUnitRtPath();
    if (version.equals("3") || version.equals("4")) {
      return junit4Rt;
    }

    // guess by module name, flat classloaders
    String junitCurrentModuleName = "intellij.junit.v" + version + ".rt";
    if (junit4Rt.isDirectory()) {
      return new File(junit4Rt.getParent(), junitCurrentModuleName);
    }
    else {
      var relevantJarsRoot = ArchivedCompilationContextUtil.getArchivedCompiledClassesLocation();
      Map<String, String> mapping = ArchivedCompilationContextUtil.getArchivedCompiledClassesMapping();
      if (relevantJarsRoot != null && junit4Rt.toPath().startsWith(relevantJarsRoot) && mapping != null) {
        return new File(mapping.get("production/" + junitCurrentModuleName));
      }
    }

    // fallback to idea test runner jar location, production-like classloaders
    String junitCurrentIdeaTestRunnerClassName = "com.intellij.junit" + version + ".JUnit" + version + "IdeaTestRunner";
    Class<?> junitCurrentIdeaTestRunnerClass;
    try {
      junitCurrentIdeaTestRunnerClass = Class.forName(junitCurrentIdeaTestRunnerClassName, false, TestObject.class.getClassLoader());
    }
    catch (ClassNotFoundException e) {
      throw new CantRunException(JUnitBundle.message("dialog.message.failed.to.resolve.junit.rt.jar.class.0.not.found", junitCurrentIdeaTestRunnerClassName), e);
    }

    return new File(PathUtil.getJarPathForClass(junitCurrentIdeaTestRunnerClass));
  }

  public static File getJUnitRtPath() {
    String currentPath = PathUtil.getJarPathForClass(TestObject.class);
    String currentUrl = VfsUtil.getUrlForLibraryRoot(new File(currentPath));
    if (StandardFileSystems.FILE_PROTOCOL.equals(VirtualFileManager.extractProtocol(currentUrl))) {  // JPS compilation
      File rtDir = new File(new File(currentPath).getParentFile(), "intellij.junit.rt");
      if (rtDir.isDirectory()) {
        return rtDir;
      }
    }
    else if (StandardFileSystems.JAR_PROTOCOL.equals(VirtualFileManager.extractProtocol(currentUrl))) {  // Bazel compilation
      File rtJar = new File(new File(new File(currentPath).getParentFile().getParentFile(), "junit_rt"), "junit-rt.jar");
      if (rtJar.isFile()) {
        return rtJar;
      }
    }
    return new File(PathUtil.getJarPathForClass(JUnit4IdeaTestRunner.class));
  }

  /**
   * Junit 5/6 searches for tests in the classpath.
   * When 2 modules have e.g. the same package, one depends on another, and tests have to run in a single module only,
   * by configuration settings or to avoid repetition in fork by module mode; additional filters per output directories are required.
   */
  protected static @Unmodifiable List<String> composeDirectoryFilter(@NotNull Module module) {
    return ContainerUtil.map(OrderEnumerator.orderEntries(module)
                               .withoutSdk()
                               .withoutLibraries()
                               .withoutDepModules().classes().getRoots(), root -> "\u002B" + root.getPath());
  }

  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    String preferredRunner = getRunner();
    JavaParameters javaParameters = super.createJavaParameters();

    int timeout = Registry.intValue("idea.test.graceful.shutdown.timeout.seconds", DEFAULT_SHUTDOWN_TIMEOUT);
    if (timeout != DEFAULT_SHUTDOWN_TIMEOUT) {
      javaParameters.getVMParametersList().addProperty("idea.test.graceful.shutdown.timeout.seconds", String.valueOf(timeout));
    }

    if (!Registry.is("test.use.suite.duration")) {
      javaParameters.getVMParametersList().addProperty("test.use.suite.duration", "false");
    }

    if (javaParameters.getMainClass() == null) { // for custom main class, e.g. overridden by JUnitDevKitUnitTestingSettings.Companion#apply
      javaParameters.setMainClass(JUnitConfiguration.JUNIT_START_CLASS);
    }
    javaParameters.getProgramParametersList().add(JUnitStarter.IDE_VERSION + JUnitStarter.VERSION);

    final StringBuilder buf = new StringBuilder();
    collectListeners(javaParameters, buf, IDEAJUnitListener.EP_NAME, "\n");
    if (!buf.isEmpty()) {
      try {
        myListenersFile = Files.createTempFile("junit_listeners_", "");
        javaParameters.getProgramParametersList().add("@@" + myListenersFile);
        Files.writeString(myListenersFile, buf.toString());
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    if (!DEFAULT_RUNNER.equals(preferredRunner)) {
      javaParameters.getProgramParametersList().add(preferredRunner);
    }

    return javaParameters;
  }

  @TestOnly
  public JavaParameters createJavaParameters4Tests() throws ExecutionException {
    JavaParameters parameters = createJavaParameters();
    downloadAdditionalDependencies(parameters);
    return parameters;
  }

  public void appendJUnitLauncherClasses(String runnerName,
                                         JavaParameters javaParameters,
                                         Project project,
                                         GlobalSearchScope globalSearchScope,
                                         boolean ensureOnModulePath) throws CantRunException {
    boolean modulePathAllowed = ensureOnModulePath && JavaSdkUtil.isJdkAtLeast(javaParameters.getJdk(), JavaSdkVersion.JDK_1_9);
    JUnitLauncherDependencies dependencies = JUnitLauncherDependencies.detect(project, globalSearchScope, runnerName, modulePathAllowed);
    if (dependencies == null) return;

    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    boolean isModularized = dependencies.isModularized();

    if (isModularized) { //for modularized junit ensure the launcher is included in the module graph
      ParamsGroup group = getOrCreateJigsawOptions(javaParameters);
      ParametersList vmParametersList = group.getParametersList();
      if (!vmParametersList.hasParameter(LAUNCHER_MODULE_NAME)) {
        vmParametersList.add("--add-modules");
        vmParametersList.add(LAUNCHER_MODULE_NAME);

        ensureSpecifiedModuleOnModulePath(javaParameters, globalSearchScope, psiFacade, LAUNCHER_MODULE_NAME);
      }
    }

    final List<String> additionalDependencies = new ArrayList<>();
    dependencies.resolve(this::isAcceptableVintageVersion, new JUnitLauncherDependencies.Downloader() {
      @Override
      public void download(@NotNull RepositoryLibraryProperties properties) throws CantRunException {
        downloadDependenciesWhenRequired(project, additionalDependencies, properties);
      }

      @Override
      public void putModuleOnPath(@NotNull String moduleName) {
        ensureSpecifiedModuleOnModulePath(javaParameters, globalSearchScope, psiFacade, moduleName);
      }
    });

    //add downloaded dependencies before everything else to avoid dependencies conflicts on org.junit.platform.common e.g. with spring boot test
    final PathsList targetList = isModularized ? javaParameters.getModulePath() : javaParameters.getClassPath();
    for (int i = additionalDependencies.size() - 1; i >= 0; i--) {
      targetList.addFirst(additionalDependencies.get(i));
    }
  }

  private static void ensureSpecifiedModuleOnModulePath(JavaParameters javaParameters,
                                                        GlobalSearchScope globalSearchScope,
                                                        JavaPsiFacade psiFacade,
                                                        String moduleName) {
    ReadAction.run(() -> DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
      PsiJavaModule launcherModule = psiFacade.findModule(moduleName, globalSearchScope);
      if (launcherModule != null) {
        JavaParametersUtil.putDependenciesOnModulePath(javaParameters, launcherModule, true);
      }
    }));
  }

  /**
   * junit 4.12+ must be on the classpath for the vintage engine to work correctly.
   * Don't add engine when it fails to detect tests anyway.
   * <p>
   * Reflection is needed for the case when no sources are attached
   */
  private boolean isAcceptableVintageVersion() {
    ClassLoader loader = TestClassCollector.createUsersClassLoader(myConfiguration);
    try {
      Class<?> aClass = loader.loadClass("junit.runner.Version");
      Method id = aClass.getDeclaredMethod("id");
      Object result = id.invoke(null);
      return result instanceof String && VersionComparatorUtil.compare("4.12", (String)result) <= 0;
    }
    catch (Throwable e) {
      LOG.debug(e);
      return false;
    }
  }

  private void downloadDependenciesWhenRequired(@NotNull Project project,
                                                @NotNull List<String> classPath,
                                                @NotNull RepositoryLibraryProperties properties) throws CantRunException {
    Collection<OrderRoot> roots;
    //noinspection IncorrectCancellationExceptionHandling
    try {
      Application application = ApplicationManager.getApplication();
      application.assertIsNonDispatchThread();
      ThreadingAssertions.assertNoOwnReadAccess();
      TargetProgressIndicator targetProgressIndicator = getTargetProgressIndicator();
      if (targetProgressIndicator != null) {
        String title = JavaUiBundle.message("jar.repository.manager.dialog.resolving.dependencies.title", 1);
        targetProgressIndicator.addSystemLine(title);
      }
      roots = JarRepositoryManager.loadDependenciesSync(
        project, properties, false, false, null, null,
        targetProgressIndicator != null
        ? new ProgressIndicatorWrapper(targetProgressIndicator)
        : ObjectUtils.notNull(ProgressManager.getInstance().getProgressIndicator(), new DumbProgressIndicator()));
    }
    catch (ProcessCanceledException e) {
      roots = Collections.emptyList();
    }
    catch (Throwable e) {
      LOG.error(e);
      roots = Collections.emptyList();
    }
    if (roots.isEmpty()) {
      throw new CantRunException(JUnitBundle.message("dialog.message.failed.to.resolve.maven.id", properties.getMavenId()));
    }
    for (OrderRoot root : roots) {
      if (root.getType() == OrderRootType.CLASSES) {
        String path = PathUtil.getLocalPath(root.getFile());
        if (!classPath.contains(path)) {
          classPath.add(path);
        }
      }
    }
  }

  /**
   * @deprecated use {@link JUnitUtil#getScope(Module, Project)} instead
   */
  @Deprecated(forRemoval = true)
  public static GlobalSearchScope getScopeForJUnit(JUnitConfiguration configuration) {
    return JUnitUtil.getScope(configuration.getConfigurationModule().getModule(), configuration.getProject());
  }

  @Override
  public void appendRepeatMode() throws ExecutionException {
    final String repeatMode = getConfiguration().getRepeatMode();
    if (!RepeatCount.ONCE.equals(repeatMode)) {
      final int repeatCount = getConfiguration().getRepeatCount();
      final String countString = RepeatCount.N.equals(repeatMode) && repeatCount > 0
                                 ? RepeatCount.getCountString(repeatCount)
                                 : repeatMode;
      getJavaParameters().getProgramParametersList().add(countString);
    }
  }

  @Override
  protected boolean useModulePath() {
    return getConfiguration().isUseModulePath();
  }

  @Override
  protected boolean isIdBasedTestTree() {
    return JUPITER_RUNNERS.contains(getRunner());
  }

  @Override
  protected @NotNull String getForkMode() {
    return getConfiguration().getForkMode();
  }

  @Override
  protected boolean isPrintAsyncStackTraceForExceptions() {
    return getConfiguration().isPrintAsyncStackTraceForExceptions();
  }

  /**
   * Dependencies for full & forked per module configurations are downloaded;
   * <p>
   * Dependencies for forked configurations are stored to be added later in {@link #appendDownloadedDependenciesForForkedConfigurations(JavaParameters, Module)}
   */
  @Override
  public void downloadAdditionalDependencies(JavaParameters javaParameters) throws ExecutionException {
    super.downloadAdditionalDependencies(javaParameters);

    String preferredRunner = getRunner();
    if (JUPITER_RUNNERS.contains(preferredRunner)) {
      JUnitConfiguration configuration = getConfiguration();
      final Project project = configuration.getProject();
      Module module = configuration.getConfigurationModule().getModule();
      ThrowableComputable<Void, ExecutionException> downloader = () -> {
        appendJUnitLauncherClasses(preferredRunner, javaParameters, project,
                                   JUnitUtil.getScope(module, project),
                                   useModulePath() && module != null && ReadAction.compute(() -> findJavaModule(module, true) != null || findJavaModule(module, false) != null));
        if (forkPerModule()) {
          for (Module packageModule : ReadAction.compute(() -> collectPackageModules(configuration.getPackage()))) {
            JavaParameters parameters = new JavaParameters();
            ParamsGroup group = getJigsawOptions(javaParameters);
            if (group != null) {
              parameters.getVMParametersList().addParamsGroup(group.clone());
            }
            parameters.setJdk(javaParameters.getJdk());
            appendJUnitLauncherClasses(preferredRunner, parameters, project,
                                       JUnitUtil.getScope(packageModule, project),
                                       useModulePath() && packageModule != null && ReadAction.compute(() -> findJavaModule(packageModule, true) != null || findJavaModule(packageModule, false) != null));
            myAdditionalJarsForModuleFork.put(packageModule, parameters);
          }
        }

        String disabledCondition = ReadAction.nonBlocking(() -> {
          if (DumbService.isDumb(project)) {
            return null;
          }
          return DisabledConditionUtil.getDisabledConditionValue(myConfiguration);
        }).executeSynchronously();

        if (disabledCondition != null) {
          javaParameters.getVMParametersList().add("-Djunit.jupiter.conditions.deactivate=" + disabledCondition);
        }

        return null;
      };
      if (ApplicationManager.getApplication().isDispatchThread()) {
        ProgressManager.getInstance()
          .runProcessWithProgressSynchronously(downloader, JUnitBundle.message("progress.title.download.additional.dependencies"), true, getConfiguration().getProject());
      }
      else {
        downloader.compute();
      }
    }
  }

  public static TestObject fromString(final String id,
                                      final JUnitConfiguration configuration,
                                      @NotNull ExecutionEnvironment environment) {
    if (JUnitConfiguration.TEST_METHOD.equals(id)) {
      return new TestMethod(configuration, environment);
    }
    if (JUnitConfiguration.TEST_CLASS.equals(id)) {
      return new TestClass(configuration, environment);
    }
    if (JUnitConfiguration.TEST_PACKAGE.equals(id)) {
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
    if (JUnitConfiguration.TEST_UNIQUE_ID.equals(id)) {
      return new TestUniqueId(configuration, environment);
    }
    if (JUnitConfiguration.TEST_TAGS.equals(id)) {
      return new TestTags(configuration, environment);
    }
    if (JUnitConfiguration.BY_SOURCE_POSITION.equals(id)) {
      return new TestBySource(configuration, environment);
    }
    if (JUnitConfiguration.BY_SOURCE_CHANGES.equals(id)) {
      return new TestsByChanges(configuration, environment);
    }
    LOG.info(JUnitBundle.message("configuration.not.specified.message", id));
    return null;
  }

  protected PsiElement retrievePsiElement(Object element) {
    if (element instanceof String qName) {
      SourceScope scope = getSourceScope();
      Project project = getConfiguration().getProject();
      int idx = qName.indexOf(',');
      String className = idx > 0 ? qName.substring(0, idx) : qName;
      return DumbService.getInstance(project).computeWithAlternativeResolveEnabled(() -> JavaPsiFacade.getInstance(project).findClass(className, scope != null ? scope.getGlobalSearchScope() : GlobalSearchScope.projectScope(project)));

    }
    if (element instanceof Location) {
      return ((Location<?>)element).getPsiElement();
    }
    return element instanceof PsiElement ? (PsiElement)element : null;
  }

  @Override
  protected void deleteTempFiles() {
    super.deleteTempFiles();
    if (myListenersFile != null) {
      try {
        Files.deleteIfExists(myListenersFile);
      }
      catch (IOException ignored) {
      }
    }
  }

  @Override
  protected @NotNull String getFrameworkName() {
    return JUNIT_TEST_FRAMEWORK_NAME;
  }

  @Override
  protected @NotNull String getFrameworkId() {
    return "junit";
  }

  @Override
  protected void passTempFile(ParametersList parametersList, String tempFilePath) {
    parametersList.add(new CompositeParameterTargetedValue().addLocalPart("@").addPathPart(tempFilePath));
  }

  @Override
  public @NotNull JUnitConfiguration getConfiguration() {
    return myConfiguration;
  }

  @Override
  protected TestSearchScope getScope() {
    return getConfiguration().getPersistentData().getScope();
  }

  @Override
  protected void passForkMode(String forkMode, File tempFile, JavaParameters parameters) {
    parameters.getProgramParametersList().add("@@@" + forkMode + ',' + tempFile.getAbsolutePath());
    if (getForkSocket() != null) {
      parameters.getProgramParametersList().add(ForkedDebuggerHelper.DEBUG_SOCKET + getForkSocket().getLocalPort());
    }
  }

  private final AtomicReference<String> myRunner = new AtomicReference<>(null);

  protected @NotNull String getRunner() {
    String cached = myRunner.get();
    if (cached != null) return cached;

    Supplier<String> runner = () -> {
      if (ApplicationManager.getApplication().isDispatchThread()) {
        return ProgressManager.getInstance()
          .runProcessWithProgressSynchronously(() -> ReadAction.nonBlocking(this::getRunnerInner).executeSynchronously(),
                                               JUnitBundle.message("dialog.title.preparing.test"),
                                               true, myConfiguration.getProject());
      }
      else if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
        return ReadAction.nonBlocking(this::getRunnerInner).executeSynchronously();
      }
      else {
        return getRunnerInner();
      }
    };
    myRunner.compareAndSet(null, runner.get());
    return myRunner.get();
  }

  private String getRunner(@NotNull GlobalSearchScope scope, @NotNull Project project) {
    if (JUnitUtil.isJUnit6(scope, project) ||
        isCustomJUnit(project, scope, JUnitCommonClassNames.ORG_JUNIT_PLATFORM_ENGINE_CANCELLATION_TOKEN)) {
      return JUnitStarter.JUNIT6_PARAMETER;
    }
    else if (JUnitUtil.isJUnit5(scope, project) ||
             isCustomJUnit(project, scope, JUnitCommonClassNames.ORG_JUNIT_PLATFORM_ENGINE_TEST_ENGINE)) {
      return JUnitStarter.JUNIT5_PARAMETER;
    }
    else {
      return DEFAULT_RUNNER;
    }
  }

  @RequiresBackgroundThread
  private @NotNull String getRunnerInner() {
    Project project = myConfiguration.getProject();
    LOG.assertTrue(!DumbService.getInstance(project).isAlternativeResolveEnabled());
    final GlobalSearchScope globalSearchScope = JUnitUtil.getScope(myConfiguration.getConfigurationModule().getModule(), myConfiguration.getProject());
    JUnitConfiguration.Data data = myConfiguration.getPersistentData();
    if (JUnitConfiguration.TEST_CATEGORY.equals(data.TEST_OBJECT)) {
      return JUnitStarter.JUNIT4_PARAMETER;
    }
    if (JUnitConfiguration.TEST_TAGS.equals(data.TEST_OBJECT)) {
      return getRunner(globalSearchScope, project);
    }

    boolean isMethodConfiguration = JUnitConfiguration.TEST_METHOD.equals(data.TEST_OBJECT);
    boolean isClassConfiguration = JUnitConfiguration.TEST_CLASS.equals(data.TEST_OBJECT);
    final PsiClass psiClass = isMethodConfiguration || isClassConfiguration
                              ? DumbService.getInstance(project).computeWithAlternativeResolveEnabled(
      () -> JavaExecutionUtil.findMainClass(project, data.getMainClassName(), globalSearchScope))
                              : null;

    if (psiClass != null) {
      Set<TestFramework> testFrameworks = TestFrameworks.detectApplicableFrameworks(psiClass);
      TestFramework testFramework = ContainerUtil.getFirstItem(testFrameworks);
      if (testFramework instanceof JUnit6Framework ||
          testFrameworks.size() > 1 && ContainerUtil.find(testFrameworks, f -> f instanceof JUnit6Framework) != null) {
        return JUnitStarter.JUNIT6_PARAMETER;
      }
      if (testFramework instanceof JUnit5Framework ||
          testFrameworks.size() > 1 && ContainerUtil.find(testFrameworks, f -> f instanceof JUnit5Framework) != null) {
        return JUnitStarter.JUNIT5_PARAMETER;
      }
      if (testFramework instanceof JUnit4Framework) {
        return JUnitStarter.JUNIT4_PARAMETER;
      }
      if (testFramework instanceof JUnit3Framework) {
        return isClassConfiguration ? JUnitStarter.JUNIT4_PARAMETER : JUnitStarter.JUNIT3_PARAMETER;
      }
      if (testFramework instanceof JUnitTestFramework && !((JUnitTestFramework)testFramework).shouldRunSingleClassAsJUnit5(project, globalSearchScope)) {
        return JUnitStarter.JUNIT4_PARAMETER;
      }
    }

    if (JUnitConfiguration.TEST_PATTERN.equals(data.TEST_OBJECT)) {
      if (ContainerUtil.and(data.getPatterns(), name -> {
        PsiClass aClass = DumbService.getInstance(project).computeWithAlternativeResolveEnabled(() -> JavaExecutionUtil.findMainClass(project, name, globalSearchScope));
        if (aClass == null) {
          return false;
        }
        TestFramework framework = TestFrameworks.detectFramework(aClass);
        return framework instanceof JUnit4Framework || framework instanceof JUnit3Framework;
      })) {
        return JUnitStarter.JUNIT4_PARAMETER;
      }
    }

    return getRunner(globalSearchScope, project);
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}
