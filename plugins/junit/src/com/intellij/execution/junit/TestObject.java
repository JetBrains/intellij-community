// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
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
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.rt.execution.junit.IDEAJUnitListener;
import com.intellij.rt.execution.junit.RepeatCount;
import com.intellij.rt.execution.testFrameworks.ForkedDebuggerHelper;
import com.intellij.rt.junit.JUnitStarter;
import com.intellij.spi.SPIFileType;
import com.intellij.spi.psi.SPIClassProviderReferenceElement;
import com.intellij.testIntegration.TestFramework;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.text.VersionComparatorUtil;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import org.jetbrains.annotations.*;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static com.intellij.execution.junit.JUnitExternalLibraryDescriptor.JUNIT5;
import static com.intellij.execution.junit.JUnitExternalLibraryDescriptor.JUNIT6;

public abstract class TestObject extends JavaTestFrameworkRunnableState<JUnitConfiguration> implements PossiblyDumbAware {
  private static final String LAUNCHER_MODULE_NAME = "org.junit.platform.launcher";
  private static final String JUPITER_ENGINE_NAME  = "org.junit.jupiter.engine";
  private static final String VINTAGE_ENGINE_NAME  = "org.junit.vintage.engine";
  private static final String SUITE_ENGINE_NAME    = "org.junit.platform.suite.engine";

  protected static final Logger LOG = Logger.getInstance(TestObject.class);

  private static final @NonNls String DEBUG_RT_PATH = "idea.junit_rt.path";
  private static final @NlsSafe String JUNIT_TEST_FRAMEWORK_NAME = "JUnit";

  private static final @NonNls String DEFAULT_RUNNER = "default";

  private final JUnitConfiguration myConfiguration;
  protected File myListenersFile;

  private final Map<Module, JavaParameters> myAdditionalJarsForModuleFork = new HashMap<>();

  private static final Map<String, String> RUNNER_VERSIONS = Map.of(
    JUnitStarter.JUNIT3_PARAMETER, "3",
    JUnitStarter.JUNIT4_PARAMETER, "4",
    JUnitStarter.JUNIT5_PARAMETER, "5",
    JUnitStarter.JUNIT6_PARAMETER, "6"
  );

  private static final Set<String> STANDARD_JUNIT_ENGINE_CLASSES = Set.of(
    "org.junit.jupiter.engine.JupiterTestEngine",
    "org.junit.vintage.engine.VintageTestEngine",
    "org.junit.platform.launcher.core.SuiteTestEngine",
    "org.junit.platform.suite.engine.SuiteTestEngine"
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
    perModule.computeIfAbsent(module, elemList -> new ArrayList<>()).add(name);
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

  public void checkConfiguration() throws RuntimeConfigurationException{
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
    //include junit6 listeners for the case custom junit 6 engines would be detected on runtime
    javaParameters.getClassPath().addFirst(getJUnitRtFile(JUnitStarter.JUNIT6_PARAMETER));

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
    JavaParameters javaParameters = super.createJavaParameters();

    if (javaParameters.getMainClass() == null) { // for custom main class, e.g. overridden by JUnitDevKitUnitTestingSettings.Companion#apply
      javaParameters.setMainClass(JUnitConfiguration.JUNIT_START_CLASS);
    }
    javaParameters.getProgramParametersList().add(JUnitStarter.IDE_VERSION + JUnitStarter.VERSION);

    final StringBuilder buf = new StringBuilder();
    collectListeners(javaParameters, buf, IDEAJUnitListener.EP_NAME, "\n");
    if (!buf.isEmpty()) {
      try {
        myListenersFile = FileUtil.createTempFile("junit_listeners_", "", true);
        javaParameters.getProgramParametersList().add("@@" + myListenersFile.getPath());
        FileUtil.writeToFile(myListenersFile, buf.toString().getBytes(StandardCharsets.UTF_8));
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    String preferredRunner = getRunner();
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
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    DumbService dumbService = DumbService.getInstance(project);

    String launcherVersion = getLibraryVersion("org.junit.platform.commons.JUnitException", globalSearchScope, project);
    if (launcherVersion == null) {
      LOG.info("Failed to detect junit " + RUNNER_VERSIONS.getOrDefault(runnerName, "5") + " launcher version, please configure explicit dependency");
      return;
    }

    boolean isModularized = ensureOnModulePath &&
                            JavaSdkUtil.isJdkAtLeast(javaParameters.getJdk(), JavaSdkVersion.JDK_1_9) &&
                            ReadAction.nonBlocking(() -> !FilenameIndex.getVirtualFilesByName(PsiJavaModule.MODULE_INFO_FILE,
                                                                                              globalSearchScope)
                              .isEmpty()).executeSynchronously() &&
                            VersionComparatorUtil.compare(launcherVersion, "1.5.0") >= 0;

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
    if (!JUnitUtil.hasPackageWithDirectories(psiFacade, "org.junit.platform.launcher", globalSearchScope)) {
      downloadDependenciesWhenRequired(project, additionalDependencies,
                                       new RepositoryLibraryProperties("org.junit.platform", "junit-platform-launcher", launcherVersion));
    }

    //add standard engines only if no engine api is present
    if (!hasJupiterEnginesAPI(globalSearchScope, psiFacade) || !isCustomJUnit(globalSearchScope,
                                                                            JUnitStarter.JUNIT6_PARAMETER.equals(runnerName)
                                                                            ? JUnitCommonClassNames.ORG_JUNIT_PLATFORM_ENGINE_CANCELLATION_TOKEN
                                                                            : JUnitCommonClassNames.ORG_JUNIT_PLATFORM_ENGINE_TEST_ENGINE)) {
      String defaultMinVersion =  JUnitStarter.JUNIT6_PARAMETER.equals(runnerName) ? JUNIT6.getMinVersion() : JUNIT5.getMinVersion();
      String jupiterVersion = ObjectUtils.notNull(getLibraryVersion(JUnitUtil.TEST5_ANNOTATION, globalSearchScope, project), defaultMinVersion);
      if (JUnitUtil.hasPackageWithDirectories(psiFacade, JUnitUtil.TEST5_PACKAGE_FQN, globalSearchScope)) {
        if (!JUnitUtil.hasPackageWithDirectories(psiFacade, JUPITER_ENGINE_NAME, globalSearchScope)) {
          downloadDependenciesWhenRequired(project, additionalDependencies,
                                           new RepositoryLibraryProperties("org.junit.jupiter", "junit-jupiter-engine", jupiterVersion));
        }
        else if (isModularized) {
          ensureSpecifiedModuleOnModulePath(javaParameters, globalSearchScope, psiFacade, JUPITER_ENGINE_NAME);
        }
      }

      if (JUnitUtil.hasPackageWithDirectories(psiFacade, "org.junit.platform.suite.api", globalSearchScope)) {
        if (!JUnitUtil.hasPackageWithDirectories(psiFacade, SUITE_ENGINE_NAME, globalSearchScope)) {
          String suiteVersion = getLibraryVersion(JUnitCommonClassNames.ORG_JUNIT_PLATFORM_SUITE_API_SUITE, globalSearchScope, project);
          if (suiteVersion != null && VersionComparatorUtil.compare(suiteVersion, "1.8.0") >= 0) {
            downloadDependenciesWhenRequired(project, additionalDependencies,
                                             new RepositoryLibraryProperties("org.junit.platform", "junit-platform-suite-engine", suiteVersion));
          }
        }
        else if (isModularized) {
          ensureSpecifiedModuleOnModulePath(javaParameters, globalSearchScope, psiFacade, SUITE_ENGINE_NAME);
        }
      }

      if (!JUnitUtil.hasPackageWithDirectories(psiFacade, VINTAGE_ENGINE_NAME, globalSearchScope)) {
        if (JUnitUtil.hasPackageWithDirectories(psiFacade, "junit.framework", globalSearchScope)) {
          PsiClass junit4RunnerClass = dumbService.computeWithAlternativeResolveEnabled(
            () -> ReadAction.nonBlocking(() -> psiFacade.findClass("junit.runner.Version", globalSearchScope)).executeSynchronously());
          if (junit4RunnerClass != null && isAcceptableVintageVersion()) {
            String version = VersionComparatorUtil.compare(launcherVersion, "1.1.0") >= 0
                             ? jupiterVersion
                             : "4.12." + StringUtil.getShortName(launcherVersion);
            downloadDependenciesWhenRequired(project, additionalDependencies,
                                             //don't include potentially incompatible hamcrest/junit dependency
                                             new RepositoryLibraryProperties("org.junit.vintage", "junit-vintage-engine", version, false,
                                                                             ContainerUtil.emptyList()));
          }
        }
      }
      else if (isModularized) {
        ensureSpecifiedModuleOnModulePath(javaParameters, globalSearchScope, psiFacade, VINTAGE_ENGINE_NAME);
      }
    }

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

  public static boolean hasJupiterEnginesAPI(GlobalSearchScope globalSearchScope, JavaPsiFacade psiFacade) {
    return JUnitUtil.hasPackageWithDirectories(psiFacade, "org.junit.platform.engine", globalSearchScope);
  }

  private static String getLibraryVersion(String className, GlobalSearchScope globalSearchScope, Project project) {
    VirtualFile root = ReadAction.nonBlocking(() -> {
      PsiClass psiClass = DumbService.getInstance(project).computeWithAlternativeResolveEnabled(() ->
        JavaPsiFacade.getInstance(project).findClass(className, globalSearchScope)
      );
      VirtualFile virtualFile = PsiUtilCore.getVirtualFile(psiClass);
      if (virtualFile == null) return null;

      return ProjectFileIndex.getInstance(project).getClassRootForFile(virtualFile);
    }).executeSynchronously();

    if (root != null && root.getFileSystem() instanceof JarFileSystem) {
      VirtualFile manifestFile = root.findFileByRelativePath(JarFile.MANIFEST_NAME);
      if (manifestFile != null) {
        try (final InputStream inputStream = manifestFile.getInputStream()) {
          Attributes mainAttributes = new Manifest(inputStream).getMainAttributes();
          if ("junit.org".equals(mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_VENDOR))) {
            return mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
          }
        }
        catch (IOException ignored) { }
      }
    }

    return null;
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
      roots = JarRepositoryManager.loadDependenciesSync(project, properties, false, false, null, null,
                                                        targetProgressIndicator != null ? new ProgressIndicatorWrapper(targetProgressIndicator)
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

  private static GlobalSearchScope getScopeForJUnit(@Nullable Module module, Project project) {
    return module != null ? GlobalSearchScope.moduleRuntimeScope(module, true) : GlobalSearchScope.allScope(project);
  }

  public static GlobalSearchScope getScopeForJUnit(JUnitConfiguration configuration) {
    return getScopeForJUnit(configuration.getConfigurationModule().getModule(), configuration.getProject());
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
                                   getScopeForJUnit(module, project),
                                   useModulePath() && module != null && ReadAction.compute(() -> findJavaModule(module, true)) != null);
        if (forkPerModule()) {
          for (Module packageModule : ReadAction.compute(() -> collectPackageModules(configuration.getPackage()))) {
            JavaParameters parameters = new JavaParameters();
            ParamsGroup group = getJigsawOptions(javaParameters);
            if (group != null) {
              parameters.getVMParametersList().addParamsGroup(group.clone());
            }
            parameters.setJdk(javaParameters.getJdk());
            appendJUnitLauncherClasses(preferredRunner, parameters, project,
                                       getScopeForJUnit(packageModule, project),
                                       useModulePath() && packageModule != null && ReadAction.compute(() -> findJavaModule(packageModule, true)) != null);
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
      FileUtil.delete(myListenersFile);
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

  private String myRunner;
  private static final Object LOCK = ObjectUtils.sentinel("JUnitRunner");

  protected @NotNull String getRunner() {
    synchronized (LOCK) {
      if (myRunner == null) {
        myRunner = ProgressManager.getInstance()
          .runProcessWithProgressSynchronously(() -> ReadAction.nonBlocking(this::getRunnerInner).executeSynchronously(),
                                               JUnitBundle.message("dialog.title.preparing.test"),
                                               true, myConfiguration.getProject());
      }
      return myRunner;
    }
  }

  private String getRunner(@NotNull GlobalSearchScope scope, @NotNull Project project) {
    if (JUnitUtil.isJUnit6(scope, project) ||
        isCustomJUnit(scope, JUnitCommonClassNames.ORG_JUNIT_PLATFORM_ENGINE_CANCELLATION_TOKEN)) {
      return JUnitStarter.JUNIT6_PARAMETER;
    }
    else if (JUnitUtil.isJUnit5(scope, project) ||
             isCustomJUnit(scope, JUnitCommonClassNames.ORG_JUNIT_PLATFORM_ENGINE_TEST_ENGINE)) {
      return JUnitStarter.JUNIT5_PARAMETER;
    }
    else {
      return DEFAULT_RUNNER;
    }
  }

  private @NotNull String getRunnerInner() {
    Project project = myConfiguration.getProject();
    LOG.assertTrue(!DumbService.getInstance(project).isAlternativeResolveEnabled());
    final GlobalSearchScope globalSearchScope = getScopeForJUnit(myConfiguration);
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
                              ? JavaExecutionUtil.findMainClass(project, data.getMainClassName(), globalSearchScope) : null;
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
        PsiClass aClass = JavaExecutionUtil.findMainClass(project, name, globalSearchScope);
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

  private boolean isCustomJUnit(GlobalSearchScope globalSearchScope, String jupiterClassName) {
    Project project = myConfiguration.getProject();
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);

    Boolean isCustomJUnitUsingPsi =
      ReadAction.nonBlocking(() -> {
        if (DumbService.isDumb(project)) {
          return null;
        }
        return hasCustomJupiterTestEngineUsingPsi(globalSearchScope, project, psiFacade, jupiterClassName);
      }).executeSynchronously();
    if (isCustomJUnitUsingPsi != null) {
      return isCustomJUnitUsingPsi;
    }
    return findCustomJupiterTestEngineUsingClassLoader(globalSearchScope, psiFacade);
  }

  private boolean findCustomJupiterTestEngineUsingClassLoader(@NotNull GlobalSearchScope globalSearchScope,
                                                              @NotNull JavaPsiFacade psiFacade) {
    boolean hasPlatformEngine = ReadAction.compute(() -> {
        PsiPackage aPackage = psiFacade.findPackage(JUnitCommonClassNames.ORG_JUNIT_PLATFORM_ENGINE);
        return aPackage != null && aPackage.getDirectories(globalSearchScope).length > 0;
      });
    if (!hasPlatformEngine) return false;
    ClassLoader loader = TestClassCollector.createUsersClassLoader(myConfiguration);
    try {
      ServiceLoader<?>
        serviceLoader = ServiceLoader.load(Class.forName(JUnitCommonClassNames.ORG_JUNIT_PLATFORM_ENGINE_TEST_ENGINE, false, loader), loader);
      for (Object engine : serviceLoader) {
        String engineClassName = engine.getClass().getName();
        if (isCustomJupiterTestEngineName(engineClassName)) {
          return true;
        }
      }
      return false;
    }
    catch (Throwable e) {
      return false;
    }
  }

  private static boolean hasCustomJupiterTestEngineUsingPsi(@NotNull GlobalSearchScope globalSearchScope,
                                                            @NotNull Project project,
                                                            @NotNull JavaPsiFacade psiFacade,
                                                            @NotNull String jupiterClassName) {
    PsiClass testEngine = psiFacade.findClass(jupiterClassName, globalSearchScope);
    if (testEngine == null) return false;
    Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(PsiJavaModule.MODULE_INFO_FILE, globalSearchScope);
    if (!files.isEmpty() && ReferencesSearch.search(testEngine, GlobalSearchScope.filesScope(project, files)).anyMatch(ref -> isCustomEngineProvided(testEngine, ref))) {
      return true;
    }
    PsiManager psiManager = PsiManager.getInstance(project);
    GlobalSearchScope scope = GlobalSearchScope.getScopeRestrictedByFileTypes(globalSearchScope, SPIFileType.INSTANCE);
    return FilenameIndex.getVirtualFilesByName(jupiterClassName, scope)
      .stream()
      .map(f -> psiManager.findFile(f))
      .filter(Objects::nonNull)
      .flatMap(f -> PsiTreeUtil.findChildrenOfType(f, SPIClassProviderReferenceElement.class).stream())
      .map(r -> r.resolve())
      .filter(e -> e instanceof PsiClass)
      .map(e -> (PsiClass)e)
      .filter(c -> isCustomJupiterTestEngineName(c.getQualifiedName()))
      .anyMatch(c -> InheritanceUtil.isInheritorOrSelf(c, testEngine, true));
  }

  private static boolean isCustomEngineProvided(PsiClass testEngine, @NotNull PsiReference ref) {
    PsiProvidesStatement providesStatement = PsiTreeUtil.getParentOfType(ref.getElement(), PsiProvidesStatement.class);
    if (providesStatement != null) {
      PsiJavaCodeReferenceElement interfaceReference = providesStatement.getInterfaceReference();
      PsiReferenceList implementationList = providesStatement.getImplementationList();
      return interfaceReference != null && interfaceReference.isReferenceTo(testEngine) &&
             implementationList != null && implementationList.getReferenceElements().length > 0;
    }
    return false;
  }

  private static boolean isCustomJupiterTestEngineName(@Nullable String engineImplClassName) {
    return !STANDARD_JUNIT_ENGINE_CLASSES.contains(engineImplClassName);
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}
