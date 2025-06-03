// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing;

import com.intellij.compiler.CompilerTestUtil;
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.externalSystem.importing.ImportSpec;
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.service.execution.TestUnknownSdkResolver;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener;
import com.intellij.openapi.externalSystem.test.JavaExternalSystemImportingTestCase;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.environment.Environment;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkResolver;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.testFramework.io.ExternalResourcesChecker;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.RunAll;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.CurrentJavaVersion;
import com.intellij.util.SmartList;
import org.gradle.StartParameter;
import org.gradle.util.GradleVersion;
import org.gradle.wrapper.PathAssembler;
import org.gradle.wrapper.WrapperConfiguration;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl;
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder;
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix;
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType;
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration;
import org.jetbrains.plugins.gradle.service.execution.GradleUserHomeUtil;
import org.jetbrains.plugins.gradle.service.project.wizard.util.GradleWrapperUtil;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.settings.GradleSystemSettings;
import org.jetbrains.plugins.gradle.tooling.GradleJvmResolver;
import org.jetbrains.plugins.gradle.tooling.JavaVersionRestriction;
import org.jetbrains.plugins.gradle.tooling.TargetJavaVersionWatcher;
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleUtil;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import static org.junit.Assume.assumeThat;

@RunWith(Parameterized.class)
public abstract class GradleImportingTestCase extends JavaExternalSystemImportingTestCase {
  public static final String BASE_GRADLE_VERSION = VersionMatcherRule.BASE_GRADLE_VERSION;
  public static final String GRADLE_JDK_NAME = "Gradle JDK";
  private static final int GRADLE_DAEMON_TTL_MS = 10000;

  @Rule public TestName name = new TestName();

  public VersionMatcherRule versionMatcherRule = asOuterRule(new VersionMatcherRule());
  public TargetJavaVersionWatcher myTargetJavaVersionWatcher = asOuterRule(new TargetJavaVersionWatcher());

  @Parameterized.Parameter
  public @NotNull String gradleVersion;
  private GradleProjectSettings myProjectSettings;
  private String myJdkHome;

  private final List<Sdk> removedSdks = new SmartList<>();
  private PathAssembler.LocalDistribution myDistribution;

  private final Ref<Couple<String>> deprecationError = Ref.create();
  private final StringBuilder deprecationTextBuilder = new StringBuilder();
  private int deprecationTextLineCount = 0;
  private @NotNull Path originalGradleUserHome;

  private @Nullable Disposable myTestDisposable = null;

  @Override
  protected void setUp() throws Exception {
    assumeThat(gradleVersion, versionMatcherRule.getMatcher());
    myProjectSettings = new GradleProjectSettings().withQualifiedModuleNames();

    super.setUp();

    WriteAction.runAndWait(this::configureJdkTable);
    System.setProperty(ExternalSystemExecutionSettings.REMOTE_PROCESS_IDLE_TTL_IN_MS_KEY, String.valueOf(GRADLE_DAEMON_TTL_MS));
    setUpGradleVmOptions();

    ExtensionTestUtil.maskExtensions(UnknownSdkResolver.EP_NAME, List.of(TestUnknownSdkResolver.INSTANCE), getTestDisposable());
    setRegistryPropertyForTest("unknown.sdk.auto", "false");
    TestUnknownSdkResolver.INSTANCE.setUnknownSdkFixMode(TestUnknownSdkResolver.TestUnknownSdkFixMode.REAL_LOCAL_FIX);

    cleanScriptsCacheIfNeeded();

    installGradleJvmConfigurator();
    installExecutionDeprecationChecker();
    originalGradleUserHome = getGradleUserHome();
  }

  protected void installGradleJvmConfigurator() {
    ExternalSystemApiUtil.subscribe(getMyProject(), GradleConstants.SYSTEM_ID, new ExternalSystemSettingsListener<GradleProjectSettings>() {
      @Override
      public void onProjectsLinked(@NotNull Collection<GradleProjectSettings> settings) {
        for (var projectSettings : settings) {
          projectSettings.setGradleJvm(GRADLE_JDK_NAME);
        }
      }
    }, getTestDisposable());
  }

  protected void configureJdkTable() {
    cleanJdkTable();
    ArrayList<Sdk> jdks = new ArrayList<>(Arrays.asList(createJdkFromJavaHome()));
    populateJdkTable(jdks);
  }

  protected void cleanJdkTable() {
    removedSdks.clear();
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      ProjectJdkTable.getInstance().removeJdk(sdk);
      if (GRADLE_JDK_NAME.equals(sdk.getName())) continue;
      removedSdks.add(sdk);
    }
  }

  protected void populateJdkTable(@NotNull List<Sdk> jdks) {
    for (Sdk jdk : jdks) {
      ProjectJdkTable.getInstance().addJdk(jdk);
    }
  }

  protected void configureGradleVmOptions(@NotNull Set<String> options) {
    if (isGradleAtLeast("7.0") && !isWarningsAllowed()) {
      options.add("-Dorg.gradle.warning.mode=fail");
    }
  }

  private @NotNull Set<String> getGradleVmOptions() {
    Set<String> options = new HashSet<>();
    configureGradleVmOptions(options);
    return options;
  }

  private void setUpGradleVmOptions() {
    GradleSystemSettings settings = GradleSystemSettings.getInstance();
    String defaultVmOptions = Objects.requireNonNullElse(settings.getGradleVmOptions(), "");

    Set<String> requiredVmOptions = getGradleVmOptions();
    String effectiveVmOptions = String.format("%s %s", defaultVmOptions, Strings.join(requiredVmOptions, " ")).trim();

    settings.setGradleVmOptions(effectiveVmOptions);
  }

  private static void tearDownGradleVmOptions() {
    GradleSystemSettings settings = GradleSystemSettings.getInstance();
    settings.setGradleVmOptions("");
  }

  private Sdk createJdkFromJavaHome() {
    VirtualFile jdkHomeDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myJdkHome));
    JavaSdk javaSdk = JavaSdk.getInstance();
    SdkType javaSdkType = javaSdk == null ? SimpleJavaSdkType.getInstance() : javaSdk;
    Sdk jdk = SdkConfigurationUtil.setupSdk(new Sdk[0], jdkHomeDir, javaSdkType, true, null, GRADLE_JDK_NAME);
    assertNotNull("Cannot create JDK for " + myJdkHome, jdk);
    return jdk;
  }

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    myJdkHome = requireRealJdkHome();
    myDistribution = configureWrapper();
  }

  protected Path getGradleUserHome() {
    String serviceDirectory = GradleSettings.getInstance(getMyProject()).getServiceDirectoryPath();
    return serviceDirectory != null ? Path.of(serviceDirectory) : GradleUserHomeUtil.gradleUserHomeDir().toPath();
  }

  /**
   * This is a workaround for the following issue on windows:
   * "C:\Users\builduser\.gradle\caches\jars-1\cache.properties (The system cannot find the file specified)"
   */
  private void cleanScriptsCacheIfNeeded() {
    if (SystemInfo.isWindows && isGradleOlderThan("3.5")) {
      Path gradleUserHome = getGradleUserHome();
      Path cacheFile = gradleUserHome.resolve("caches/jars-1/cache.properties");
      if (Files.notExists(cacheFile)) {
        try {
          Files.createFile(NioFiles.createParentDirectories(cacheFile));
        }
        catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
      Path scriptsCacheFolder = gradleUserHome.resolve("caches").resolve(gradleVersion).resolve("scripts");
      try {
        NioFiles.deleteRecursively(scriptsCacheFolder);
        LOG.debug("Gradle scripts cache folder has been successfully removed at " + scriptsCacheFolder);
      }
      catch (IOException e) {
        LOG.debug("Gradle scripts cache folder has not been removed at " + scriptsCacheFolder);
      }
      Path scriptsRemappedCacheFolder = gradleUserHome.resolve("caches").resolve(gradleVersion).resolve("scripts-remapped");
      try {
        NioFiles.deleteRecursively(scriptsRemappedCacheFolder);
        LOG.debug("Gradle scripts-remapped cache folder has been successfully removed at " + scriptsRemappedCacheFolder);
      }
      catch (IOException e) {
        LOG.debug("Gradle scripts-remapped cache folder has not been removed at " + scriptsRemappedCacheFolder);
      }
    }
  }

  @Override
  protected boolean runInDispatchThread() {
    return false;
  }

  @NotNull
  public GradleVersion getCurrentGradleVersion() {
    return GradleVersion.version(gradleVersion);
  }

  @NotNull
  protected GradleVersion getCurrentGradleBaseVersion() {
    return GradleVersion.version(gradleVersion).getBaseVersion();
  }

  @NotNull
  private String requireRealJdkHome() {
    if (getMyWSLDistribution() != null) {
      return requireWslJdkHome(getMyWSLDistribution());
    }
    return requireJdkHome();
  }

  private static String requireWslJdkHome(@NotNull WSLDistribution distribution) {
    String jdkPath = System.getProperty("wsl.jdk.path");
    if (jdkPath == null) {
      jdkPath = "/usr/lib/jvm/java-11-openjdk-amd64";
    }
    return distribution.getWindowsPath(jdkPath);
  }

  public @NotNull String requireJdkHome() {
    return requireJdkHome(getCurrentGradleVersion(), myTargetJavaVersionWatcher.getRestriction());
  }

  public static @NotNull String requireJdkHome(
    @NotNull GradleVersion gradleVersion,
    @NotNull JavaVersionRestriction javaVersionRestriction
  ) {
    if (GradleJvmSupportMatrix.isSupported(gradleVersion, CurrentJavaVersion.currentJavaVersion()) &&
        !javaVersionRestriction.isRestricted(gradleVersion, CurrentJavaVersion.currentJavaVersion())) {
      return IdeaTestUtil.requireRealJdkHome();
    }
    // fix exception of FJP at JavaHomeFinder.suggestHomePaths => ... => EnvironmentUtil.getEnvironmentMap => CompletableFuture.<clinit>
    IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true);
    return GradleJvmResolver.resolveGradleJvmHomePath(gradleVersion, javaVersionRestriction);
  }

  protected void collectAllowedRoots(final List<String> roots, PathAssembler.LocalDistribution distribution) {
  }

  @Override
  public void tearDown() throws Exception {
    if (myJdkHome == null) {
      //super.setUpInWriteAction() wasn't called

      RunAll.runAll(
        () -> Disposer.dispose(getTestDisposable()),
        () -> super.tearDown()
      );

      return;
    }

    RunAll.runAll(
      () -> {
        WriteAction.runAndWait(() -> {
          Arrays.stream(ProjectJdkTable.getInstance().getAllJdks()).forEach(ProjectJdkTable.getInstance()::removeJdk);
          for (Sdk sdk : removedSdks) {
            SdkConfigurationUtil.addSdk(sdk);
          }
          removedSdks.clear();
        });
      },
      () -> {
        TestDialogManager.setTestDialog(TestDialog.DEFAULT);
        CompilerTestUtil.deleteBuildSystemDirectory(getMyProject());
      },
      () -> deprecationError.set(null),
      () -> tearDownGradleVmOptions(),
      () -> resetGradleUserHomeIfNeeded(),
      () -> Disposer.dispose(getTestDisposable()),
      () -> super.tearDown()
    );
  }

  private @NotNull Disposable getTestDisposable() {
    if (myTestDisposable == null) {
      myTestDisposable = Disposer.newDisposable();
    }
    return myTestDisposable;
  }

  @Override
  protected void collectAllowedRoots(final List<String> roots) {
    super.collectAllowedRoots(roots);
    roots.add(myJdkHome);
    roots.addAll(collectRootsInside(myJdkHome));
    roots.add(PathManager.getConfigPath());
    String gradleHomeEnv = Environment.getVariable("GRADLE_USER_HOME");
    if (gradleHomeEnv != null) roots.add(gradleHomeEnv);
    String javaHome = Environment.getVariable("JAVA_HOME");
    if (javaHome != null) roots.add(javaHome);

    collectAllowedRoots(roots, myDistribution);
  }

  @Parameterized.Parameters(name = "{index}: with Gradle-{0}")
  public static Iterable<?> data() {
    return VersionMatcherRule.getSupportedGradleVersions();
  }

  @Override
  protected String getTestsTempDir() {
    return "tmp";
  }

  @Override
  protected String getExternalSystemConfigFileName() {
    return "build.gradle";
  }

  protected void importProjectUsingSingeModulePerGradleProject() {
    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(false);
    importProject();
  }

  protected void importProjectUsingSingeModulePerGradleProject(@NonNls String config) throws IOException {
    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(false);
    importProject(config);
  }

  @Override
  protected void importProject(@NotNull String config, @Nullable Boolean skipIndexing) throws IOException {
    if (UsefulTestCase.IS_UNDER_TEAMCITY) {
      config = injectRepo(config);
    }
    super.importProject(config, skipIndexing);
    handleDeprecationError(deprecationError.get());
  }

  protected void handleDeprecationError(Couple<String> errorInfo) {
    if (errorInfo == null) return;
    handleImportFailure(errorInfo.first, errorInfo.second);
  }

  private void installExecutionDeprecationChecker() {
    var notificationManager = ExternalSystemProgressNotificationManager.getInstance();
    var notificationListener = new ExternalSystemTaskNotificationListener() {
      @Override
      public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, @NotNull ProcessOutputType processOutputType) {
        if (text.contains("This is scheduled to be removed in Gradle")
            || text.contains("Deprecated Gradle features were used in this build")) {
          deprecationTextLineCount = 30;
        }
        if (deprecationTextLineCount > 0) {
          deprecationTextBuilder.append(text);
          deprecationTextLineCount--;
          if (deprecationTextLineCount == 0) {
            deprecationError.set(Couple.of("Deprecation warning from Gradle", deprecationTextBuilder.toString()));
            deprecationTextBuilder.setLength(0);
          }
        }
      }
    };
    notificationManager.addNotificationListener(notificationListener, getTestDisposable());
  }

  @Override
  protected void handleImportFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
    var combinedMessage = errorMessage + "\n" + errorDetails;
    if (combinedMessage.contains("org.gradle.wrapper.Download.download") && combinedMessage.contains("java.net.SocketException")) {
      ExternalResourcesChecker.reportUnavailability("Gradle distribution service", null);
    }
    super.handleImportFailure(errorMessage, errorDetails);
  }

  public void importProject(@NonNls String config) throws IOException {
    importProject(config, null);
  }

  protected @NotNull TestGradleBuildScriptBuilder createBuildScriptBuilder() {
    return new TestGradleBuildScriptBuilder(getCurrentGradleVersion())
      .addPrefix(MAVEN_REPOSITORY_PATCH_PLACE, "");
  }

  public @NotNull String script(@NotNull Consumer<TestGradleBuildScriptBuilder> configure) {
    var builder = createBuildScriptBuilder();
    configure.accept(builder);
    return builder.generate();
  }

  public @NotNull String settingsScript(@NotNull Consumer<GradleSettingScriptBuilder<?>> configure) {
    var builder = GradleSettingScriptBuilder.create(getCurrentGradleVersion(), GradleDsl.GROOVY);
    configure.accept(builder);
    return builder.generate();
  }

  public @Nullable String getGradleJdkHome() {
    return myJdkHome;
  }

  @Override
  protected ImportSpec createImportSpec() {
    ImportSpecBuilder importSpecBuilder = new ImportSpecBuilder(super.createImportSpec());
    importSpecBuilder.withArguments("--stacktrace");
    return importSpecBuilder.build();
  }

  private static final String MAVEN_REPOSITORY_PATCH_PLACE = "// Place for Maven repository patch";

  @NotNull
  protected String injectRepo(@NonNls String config) {
    String mavenRepositoryPatch =
      """
        allprojects {
            repositories {
                maven {
                    url = 'https://repo.labs.intellij.net/repo1'
                }
            }
        }
        """;
    if (config.contains(MAVEN_REPOSITORY_PATCH_PLACE)) {
      return config.replace(MAVEN_REPOSITORY_PATCH_PLACE, mavenRepositoryPatch);
    }
    else {
      return mavenRepositoryPatch + config;
    }
  }

  @NotNull
  protected GradleRunConfiguration createEmptyGradleRunConfiguration(@NotNull String name) {
    final RunManagerEx runManager = RunManagerEx.getInstanceEx(getMyProject());
    final RunnerAndConfigurationSettings settings = runManager.createConfiguration(name, GradleExternalTaskConfigurationType.class);
    return (GradleRunConfiguration)settings.getConfiguration();
  }

  @Override
  protected GradleProjectSettings getCurrentExternalProjectSettings() {
    return myProjectSettings;
  }

  @Override
  protected ProjectSystemId getExternalSystemId() {
    return GradleConstants.SYSTEM_ID;
  }

  protected VirtualFile createSettingsFile(@NonNls String content) throws IOException {
    return createProjectSubFile("settings.gradle", content);
  }

  /**
   * Produces settings content and creates necessary directories.
   * @param projects list of sub-project to create
   * @return a block of `include 'project-name'` lines for settings.gradle
   */
  protected String including(@NonNls String... projects) {
    return including(getMyProjectRoot(), projects);
  }

  protected String including(VirtualFile root, @NonNls String... projects) {
    return new TestGradleSettingsScriptHelper(root.toNioPath(), projects).build();
  }


  private PathAssembler.LocalDistribution configureWrapper() {

    myProjectSettings.setDistributionType(DistributionType.DEFAULT_WRAPPED);

    // Cannot generate Gradle wrapper using virtual files system.
    // Because the K2MppHighlightingIntegrationTest.testJvmMultifileClass test implicitly depends on the VFS cache.
    // Calling the for VFS refresh after Gradle wrapper generation using Java NIO API also fails this KMP test
    GradleWrapperUtil.generateGradleWrapper(getMyProjectRoot().toNioPath(), getCurrentGradleVersion());

    // VfsUtil.markDirtyAndRefresh(false, true, true, myProjectRoot)

    WrapperConfiguration wrapperConfiguration = GradleUtil.getWrapperConfiguration(getMyProjectRoot().toNioPath());
    PathAssembler pathAssembler = new PathAssembler(StartParameter.DEFAULT_GRADLE_USER_HOME, new File(getProjectPath()));
    PathAssembler.LocalDistribution localDistribution = pathAssembler.getDistribution(wrapperConfiguration);

    File zip = localDistribution.getZipFile();
    try {
      if (zip.exists()) {
        try {
          new ZipFile(zip).close();
        }
        catch (ZipException e) {
          e.printStackTrace();
          System.out.println("Corrupted file will be removed: " + zip);
          Files.delete(zip.toPath());
        }
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    return localDistribution;
  }

  protected void assertMergedModuleCompileLibDepScope(String moduleName, String depName) {
    assertModuleLibDepScope(moduleName, depName, DependencyScope.COMPILE);
  }

  protected void assertMergedModuleCompileModuleDepScope(String moduleName, String depName) {
    assertModuleModuleDepScope(moduleName, depName, DependencyScope.COMPILE);
  }

  protected boolean isGradleOlderThan(@NotNull String ver) {
    return GradleVersionUtil.isGradleOlderThan(getCurrentGradleBaseVersion(), ver);
  }

  protected boolean isGradleAtLeast(@NotNull String ver) {
    return GradleVersionUtil.isGradleAtLeast(getCurrentGradleBaseVersion(), ver);
  }

  protected void enableGradleDebugWithSuspend() {
    GradleSystemSettings settings = GradleSystemSettings.getInstance();
    String options = String.format("%s %s",
                                   Objects.requireNonNullElse(settings.getGradleVmOptions(), ""),
                                   "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"
    );
    settings.setGradleVmOptions(options);
  }

  protected Boolean isWarningsAllowed() {
    return false;
  }

  protected void overrideGradleUserHome(@NotNull String relativeUserHomePath) throws IOException {
    String gradleUserHome = "%s/%s".formatted(getMyTestDir().getPath(), relativeUserHomePath);
    String gradleCachedFolderName = "gradle-%s-bin".formatted(gradleVersion);
    File cachedGradleDistribution = findGradleDistributionInCache(gradleCachedFolderName);
    if (cachedGradleDistribution != null) {
      File targetGradleDistribution = Path.of(gradleUserHome + "/wrapper/dists/" + gradleCachedFolderName)
        .toFile();
      FileUtil.copyDir(cachedGradleDistribution, targetGradleDistribution);
    }
    GradleSettings.getInstance(getMyProject()).setServiceDirectoryPath(gradleUserHome);
  }

  protected void resetGradleUserHomeIfNeeded() {
    if (!originalGradleUserHome.equals(getGradleUserHome())) {
      String normalizedOldGradleUserHome = originalGradleUserHome.normalize().toString();
      String canonicalOldGradleUserHome = FileUtil.toCanonicalPath(normalizedOldGradleUserHome);
      GradleSettings.getInstance(getMyProject()).setServiceDirectoryPath(canonicalOldGradleUserHome);
    }
  }

  @Nullable
  private static File findGradleDistributionInCache(String gradleCachedFolderName) {
    Path pathToGradleWrapper = StartParameter.DEFAULT_GRADLE_USER_HOME.toPath().resolve("wrapper/dists/" + gradleCachedFolderName);
    File gradleWrapperFile = pathToGradleWrapper.toFile();
    if (gradleWrapperFile.exists()) {
      return gradleWrapperFile;
    }
    return null;
  }

  protected String convertToLibraryName(VirtualFile fsRoot) {
    return "Gradle: " + fsRoot.getName();
  }
}
