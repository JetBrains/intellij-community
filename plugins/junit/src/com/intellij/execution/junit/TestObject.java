// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.junit4.JUnit4IdeaTestRunner;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.text.VersionComparatorUtil;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
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
import java.util.stream.Stream;

public abstract class TestObject extends JavaTestFrameworkRunnableState<JUnitConfiguration> implements PossiblyDumbAware {
  private static final String LAUNCHER_MODULE_NAME = "org.junit.platform.launcher";
  private static final String JUPITER_ENGINE_NAME  = "org.junit.jupiter.engine";
  private static final String VINTAGE_ENGINE_NAME  = "org.junit.vintage.engine";

  protected static final Logger LOG = Logger.getInstance(TestObject.class);

  private static final @NonNls String DEBUG_RT_PATH = "idea.junit_rt.path";
  private static final @NlsSafe String JUNIT_TEST_FRAMEWORK_NAME = "JUnit";

  private static final @NonNls String DEFAULT_RUNNER = "default";

  private final JUnitConfiguration myConfiguration;
  protected File myListenersFile;
  
  private final Map<Module, JavaParameters> myAdditionalJarsForModuleFork = new HashMap<>();

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
          perModule.put(module, composeDirectoryFilter(module));
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
    if (sourceScope != null && packageName != null && JUnitStarter.JUNIT5_PARAMETER.equals(getRunner())) {
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

  @Nullable
  public SourceScope getSourceScope() {
    return SourceScope.modules(getConfiguration().getModules());
  }

  @Override
  protected void configureRTClasspath(JavaParameters javaParameters, Module module) {
    final String path = System.getProperty(DEBUG_RT_PATH);
    javaParameters.getClassPath().addFirst(path != null ? path : PathUtil.getJarPathForClass(JUnitStarter.class));

    //include junit5 listeners for the case custom junit 5 engines would be detected on runtime
    javaParameters.getClassPath().addFirst(getJUnit5RtFile());
    
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

  public static File getJUnit5RtFile() {
    File junit4Rt = new File(PathUtil.getJarPathForClass(JUnit4IdeaTestRunner.class));
    String junit4Name = junit4Rt.getName();
    String junit5Name = junit4Rt.isDirectory() ? junit4Name.replace("junit", "junit.v5")
                                               : junit4Name.replace("junit", "junit5");
    return new File(junit4Rt.getParent(), junit5Name);
  }

  /**
   * Junit 5 searches for tests in the classpath.
   * When 2 modules have e.g. the same package, one depends on another, and tests have to run in single module only,
   * by configuration settings or to avoid repetition in fork by module mode, additional filters per output directories are required.
   */
  protected static List<String> composeDirectoryFilter(@NotNull Module module) {
    return ContainerUtil.map(OrderEnumerator.orderEntries(module)
                               .withoutSdk()
                               .withoutLibraries()
                               .withoutDepModules().classes().getRoots(), root -> "\u002B" + root.getPath());
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

  public void appendJUnit5LauncherClasses(JavaParameters javaParameters,
                                          Project project,
                                          GlobalSearchScope globalSearchScope,
                                          boolean ensureOnModulePath) throws CantRunException {

    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    DumbService dumbService = DumbService.getInstance(project);
    PsiClass classFromCommon = dumbService.computeWithAlternativeResolveEnabled(() -> ReadAction.nonBlocking(() -> psiFacade.findClass("org.junit.platform.commons.JUnitException", globalSearchScope)).executeSynchronously());

    String launcherVersion = getVersion(classFromCommon);
    if (launcherVersion == null) {
      LOG.info("Failed to detect junit 5 launcher version, please configure explicit dependency");
      return;
    }

    boolean isModularized = ensureOnModulePath &&
                            JavaSdkUtil.isJdkAtLeast(javaParameters.getJdk(), JavaSdkVersion.JDK_1_9) &&
                            ReadAction.nonBlocking(() -> FilenameIndex.getFilesByName(project, PsiJavaModule.MODULE_INFO_FILE, globalSearchScope).length > 0).executeSynchronously() &&
                            VersionComparatorUtil.compare(launcherVersion, "1.5.0") >= 0;

    if (isModularized) { //for modularized junit ensure launcher is included in the module graph
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
    if (!hasJUnit5EnginesAPI(globalSearchScope, psiFacade) || !isCustomJUnit5(globalSearchScope)) {
      PsiClass testAnnotation = dumbService.computeWithAlternativeResolveEnabled(() -> ReadAction.nonBlocking(() -> psiFacade.findClass(JUnitUtil.TEST5_ANNOTATION, globalSearchScope)).executeSynchronously());
      String jupiterVersion = ObjectUtils.notNull(getVersion(testAnnotation), "5.0.0");
      if (JUnitUtil.hasPackageWithDirectories(psiFacade, JUnitUtil.TEST5_PACKAGE_FQN, globalSearchScope)) {
        if (!JUnitUtil.hasPackageWithDirectories(psiFacade, JUPITER_ENGINE_NAME, globalSearchScope)) {
          downloadDependenciesWhenRequired(project, additionalDependencies,
                                           new RepositoryLibraryProperties("org.junit.jupiter", "junit-jupiter-engine", jupiterVersion));
        }
        else if (isModularized) {
          ensureSpecifiedModuleOnModulePath(javaParameters, globalSearchScope, psiFacade, JUPITER_ENGINE_NAME);
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
   * junit 4.12+ must be on the classpath for vintage engine to work correctly.
   * Don't add engine when it will fail to detect tests anyway.
   * 
   * Reflection is needed for the case when no sources is attached
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

  public static boolean hasJUnit5EnginesAPI(GlobalSearchScope globalSearchScope, JavaPsiFacade psiFacade) {
    return JUnitUtil.hasPackageWithDirectories(psiFacade, "org.junit.platform.engine", globalSearchScope);
  }

  private static String getVersion(PsiClass classFromCommon) {
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(classFromCommon);
    if (virtualFile != null) {
      ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(classFromCommon.getProject());
      VirtualFile root = index.getClassRootForFile(virtualFile);
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
    }

    return null;
  }

  private void downloadDependenciesWhenRequired(@NotNull Project project,
                                                @NotNull List<String> classPath,
                                                @NotNull RepositoryLibraryProperties properties) throws CantRunException {
    Collection<OrderRoot> roots;
    try {
      Application application = ApplicationManager.getApplication();
      application.assertReadAccessNotAllowed();
      application.assertIsNonDispatchThread();
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
    return JUnitStarter.JUNIT5_PARAMETER.equals(getRunner());
  }

  @NotNull
  @Override
  protected String getForkMode() {
    return getConfiguration().getForkMode();
  }

  /**
   * Dependencies for full & forked per module configurations are downloaded; 
   * 
   * Dependencies for forked configurations are stored to be added later in {@link #appendDownloadedDependenciesForForkedConfigurations(JavaParameters, Module)}
   */
  @Override
  public void downloadAdditionalDependencies(JavaParameters javaParameters) throws ExecutionException {
    super.downloadAdditionalDependencies(javaParameters);

    String preferredRunner = getRunner();
    if (JUnitStarter.JUNIT5_PARAMETER.equals(preferredRunner)) {
      JUnitConfiguration configuration = getConfiguration();
      final Project project = configuration.getProject();
      Module module = configuration.getConfigurationModule().getModule();
      ThrowableComputable<Void, ExecutionException> downloader = () -> {
        appendJUnit5LauncherClasses(javaParameters, project,
                                    getScopeForJUnit(module, project),
                                    useModulePath() && module != null && ReadAction.compute(() -> findJavaModule(module, true)) != null);
        if (forkPerModule()) {
          for (Module packageModule : collectPackageModules(configuration.getPackage())) {
            JavaParameters parameters = new JavaParameters();
            ParamsGroup group = getJigsawOptions(javaParameters);
            if (group != null) {
              parameters.getVMParametersList().addParamsGroup(group.clone());
            }
            parameters.setJdk(javaParameters.getJdk());
            appendJUnit5LauncherClasses(parameters,
                                        project,
                                        getScopeForJUnit(packageModule, project),
                                        useModulePath() &&
                                        packageModule != null &&
                                        ReadAction.compute(() -> findJavaModule(packageModule, true)) != null);
            myAdditionalJarsForModuleFork.put(packageModule, parameters);
          }
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
    if (element instanceof String) {
      SourceScope scope = getSourceScope();
      Project project = getConfiguration().getProject();
      String qName = (String)element;
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
  @NotNull
  protected String getFrameworkName() {
    return JUNIT_TEST_FRAMEWORK_NAME;
  }

  @Override
  @NotNull
  protected String getFrameworkId() {
    return "junit";
  }

  @Override
  protected void passTempFile(ParametersList parametersList, String tempFilePath) {
    parametersList.add(new CompositeParameterTargetedValue().addLocalPart("@").addPathPart(tempFilePath));
  }

  @Override
  @NotNull
  public JUnitConfiguration getConfiguration() {
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

  @NotNull
  protected String getRunner() {
    if (myRunner == null) {
      myRunner = ReadAction.nonBlocking(this::getRunnerInner).executeSynchronously();
    }
    return myRunner;
  }

  @NotNull
  private String getRunnerInner() {
    Project project = myConfiguration.getProject();
    LOG.assertTrue(!DumbService.getInstance(project).isAlternativeResolveEnabled());
    final GlobalSearchScope globalSearchScope = getScopeForJUnit(myConfiguration);
    JUnitConfiguration.Data data = myConfiguration.getPersistentData();
    boolean isMethodConfiguration = JUnitConfiguration.TEST_METHOD.equals(data.TEST_OBJECT);
    boolean isClassConfiguration = JUnitConfiguration.TEST_CLASS.equals(data.TEST_OBJECT);
    final PsiClass psiClass = isMethodConfiguration || isClassConfiguration
                              ? JavaExecutionUtil.findMainClass(project, data.getMainClassName(), globalSearchScope) : null;
    if (psiClass != null) {
      Set<TestFramework> testFrameworks = TestFrameworks.detectApplicableFrameworks(psiClass);
      TestFramework testFramework = ContainerUtil.getFirstItem(testFrameworks);
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
    return JUnitUtil.isJUnit5(globalSearchScope, project) || isCustomJUnit5(globalSearchScope) ? JUnitStarter.JUNIT5_PARAMETER : DEFAULT_RUNNER;
  }

  private boolean isCustomJUnit5(GlobalSearchScope globalSearchScope) {
    Project project = myConfiguration.getProject();
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);

    if (DumbService.isDumb(project)) {
      return findCustomJUnit5TestEngineUsingClassLoader(globalSearchScope, psiFacade);
    }
    else {
      return ReadAction.nonBlocking(() -> findCustomJunit5TestEngineUsingPsi(globalSearchScope, project, psiFacade)).executeSynchronously();
    }
  }

  private boolean findCustomJUnit5TestEngineUsingClassLoader(@NotNull GlobalSearchScope globalSearchScope,
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
        if (isCustomJunit5TestEngineName(engineClassName)) {
          return true;
        }
      }
      return false;
    }
    catch (Throwable e) {
      return false;
    }
  }

  private static boolean findCustomJunit5TestEngineUsingPsi(@NotNull GlobalSearchScope globalSearchScope,
                                                            @NotNull Project project,
                                                            @NotNull JavaPsiFacade psiFacade) {
    PsiClass testEngine = psiFacade.findClass(JUnitCommonClassNames.ORG_JUNIT_PLATFORM_ENGINE_TEST_ENGINE, globalSearchScope);
    if (testEngine == null) return false;
    Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(PsiJavaModule.MODULE_INFO_FILE, globalSearchScope);
    if (!files.isEmpty() && ReferencesSearch.search(testEngine, GlobalSearchScope.filesScope(project, files)).anyMatch(ref -> isCustomEngineProvided(testEngine, ref))) {
      return true;
    }
    GlobalSearchScope scope = GlobalSearchScope.getScopeRestrictedByFileTypes(globalSearchScope, SPIFileType.INSTANCE);
    return Stream.of(FilenameIndex.getFilesByName(project, JUnitCommonClassNames.ORG_JUNIT_PLATFORM_ENGINE_TEST_ENGINE, scope))
                 .flatMap(f -> PsiTreeUtil.findChildrenOfType(f, SPIClassProviderReferenceElement.class).stream())
                 .map(r -> r.resolve())
                 .filter(e -> e instanceof PsiClass)
                 .map(e -> (PsiClass)e)
                 .filter(c -> isCustomJunit5TestEngineName(c.getQualifiedName()))
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

  private static boolean isCustomJunit5TestEngineName(@Nullable String engineImplClassName) {
    return !"org.junit.jupiter.engine.JupiterTestEngine".equals(engineImplClassName) &&
           !"org.junit.vintage.engine.VintageTestEngine".equals(engineImplClassName);
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}