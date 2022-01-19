// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing;

import com.intellij.compiler.CompilerTestUtil;
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.externalSystem.importing.ImportSpec;
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.TestUnknownSdkResolver;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListenerAdapter;
import com.intellij.openapi.externalSystem.test.JavaExternalSystemImportingTestCase;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.environment.Environment;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.JavaHomeFinder;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkResolver;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.RunAll;
import com.intellij.util.PathUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.PathKt;
import com.intellij.util.lang.JavaVersion;
import org.gradle.StartParameter;
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.util.GradleVersion;
import org.gradle.wrapper.GradleWrapperMain;
import org.gradle.wrapper.PathAssembler;
import org.gradle.wrapper.WrapperConfiguration;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JdkVersionDetector;
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilderUtil;
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType;
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.settings.GradleSystemSettings;
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleJvmSupportMatriciesKt;
import org.jetbrains.plugins.gradle.util.GradleUtil;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import static org.jetbrains.plugins.gradle.tooling.VersionMatcherRule.SUPPORTED_GRADLE_VERSIONS;
import static org.jetbrains.plugins.gradle.tooling.builder.AbstractModelBuilderTest.DistributionLocator;
import static org.junit.Assume.assumeThat;

@RunWith(Parameterized.class)
public abstract class GradleImportingTestCase extends JavaExternalSystemImportingTestCase {
  public static final String BASE_GRADLE_VERSION = VersionMatcherRule.BASE_GRADLE_VERSION;
  protected static final String GRADLE_JDK_NAME = "Gradle JDK";
  private static final int GRADLE_DAEMON_TTL_MS = 10000;

  @Rule public TestName name = new TestName();

  public VersionMatcherRule versionMatcherRule = asOuterRule(new VersionMatcherRule());
  @Parameterized.Parameter
  public @NotNull String gradleVersion;
  private GradleProjectSettings myProjectSettings;
  private String myJdkHome;

  private final List<Sdk> removedSdks = new SmartList<>();
  private PathAssembler.LocalDistribution myDistribution;

  @Override
  public void setUp() throws Exception {
    assumeThat(gradleVersion, versionMatcherRule.getMatcher());
    myProjectSettings = new GradleProjectSettings().withQualifiedModuleNames();

    super.setUp();

    WriteAction.runAndWait(this::configureJDKTable);
    System.setProperty(ExternalSystemExecutionSettings.REMOTE_PROCESS_IDLE_TTL_IN_MS_KEY, String.valueOf(GRADLE_DAEMON_TTL_MS));

    ExtensionTestUtil.maskExtensions(UnknownSdkResolver.EP_NAME, List.of(TestUnknownSdkResolver.INSTANCE), getTestRootDisposable());
    setRegistryPropertyForTest("unknown.sdk.auto", "false");
    TestUnknownSdkResolver.INSTANCE.setUnknownSdkFixMode(TestUnknownSdkResolver.TestUnknownSdkFixMode.REAL_LOCAL_FIX);

    cleanScriptsCacheIfNeeded();
  }

  private void configureJDKTable() throws Exception {
    removedSdks.clear();
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      ProjectJdkTable.getInstance().removeJdk(sdk);
      if (GRADLE_JDK_NAME.equals(sdk.getName())) continue;
      removedSdks.add(sdk);
    }
    VirtualFile jdkHomeDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myJdkHome));
    JavaSdk javaSdk = JavaSdk.getInstance();
    SdkType javaSdkType = javaSdk == null ? SimpleJavaSdkType.getInstance() : javaSdk;
    Sdk jdk = SdkConfigurationUtil.setupSdk(new Sdk[0], jdkHomeDir, javaSdkType, true, null, GRADLE_JDK_NAME);
    assertNotNull("Cannot create JDK for " + myJdkHome, jdk);
    ProjectJdkTable.getInstance().addJdk(jdk);
  }

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    myJdkHome = requireRealJdkHome();
    myDistribution = configureWrapper();
  }

  /**
   * This is a workaround for the following issue on windows:
   * "C:\Users\builduser\.gradle\caches\jars-1\cache.properties (The system cannot find the file specified)"
   */
  private void cleanScriptsCacheIfNeeded() {
    if (SystemInfo.isWindows && isGradleOlderThan("3.5")) {
      String serviceDirectory = GradleSettings.getInstance(myProject).getServiceDirectoryPath();
      File gradleUserHome = serviceDirectory != null ? new File(serviceDirectory) : new BuildLayoutParameters().getGradleUserHomeDir();
      Path cacheFile = Paths.get(gradleUserHome.getPath(), "caches", "jars-1", "cache.properties");
      if (Files.notExists(cacheFile)) {
        PathKt.createFile(cacheFile);
      }
      File scriptsCacheFolder = Paths.get(gradleUserHome.getPath(), "caches", gradleVersion, "scripts").toFile();
      if (FileUtil.delete(scriptsCacheFolder)) {
        LOG.debug("Gradle scripts cache folder has been successfully removed at " + scriptsCacheFolder.getPath());
      }
      else {
        LOG.debug("Gradle scripts cache folder has not been removed at " + scriptsCacheFolder.getPath());
      }
      File scriptsRemappedCacheFolder = Paths.get(gradleUserHome.getPath(), "caches", gradleVersion, "scripts-remapped").toFile();
      if (FileUtil.delete(scriptsRemappedCacheFolder)) {
        LOG.debug("Gradle scripts-remapped cache folder has been successfully removed at " + scriptsRemappedCacheFolder.getPath());
      }
      else {
        LOG.debug("Gradle scripts-remapped cache folder has not been removed at " + scriptsRemappedCacheFolder.getPath());
      }
    }
  }

  @Override
  protected boolean runInDispatchThread() {
    return false;
  }

  @NotNull
  protected GradleVersion getCurrentGradleVersion() {
    return GradleVersion.version(gradleVersion);
  }

  @NotNull
  protected GradleVersion getCurrentGradleBaseVersion() {
    return GradleVersion.version(gradleVersion).getBaseVersion();
  }

  protected void assumeTestJavaRuntime(@NotNull JavaVersion javaRuntimeVersion) {
    int javaVer = javaRuntimeVersion.feature;
    GradleVersion gradleBaseVersion = getCurrentGradleBaseVersion();
    Assume.assumeFalse("Skip integration tests running on JDK " + javaVer + "(>9) for " + gradleBaseVersion + "(<3.0)",
                       javaVer > 9 && gradleBaseVersion.compareTo(GradleVersion.version("3.0")) < 0);
  }

  @NotNull
  private String requireRealJdkHome() {
    if (myWSLDistribution != null) {
      return requireWslJdkHome(myWSLDistribution);
    }
    JavaVersion javaRuntimeVersion = JavaVersion.current();
    assumeTestJavaRuntime(javaRuntimeVersion);
    GradleVersion baseVersion = getCurrentGradleBaseVersion();
    if (!GradleJvmSupportMatriciesKt.isSupported(baseVersion, javaRuntimeVersion)) {
      // fix exception of FJP at JavaHomeFinder.suggestHomePaths => ... => EnvironmentUtil.getEnvironmentMap => CompletableFuture.<clinit>
      IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true);
      List<String> paths = JavaHomeFinder.suggestHomePaths(true);
      for (String path : paths) {
        if (JdkUtil.checkForJdk(path)) {
          JdkVersionDetector.JdkVersionInfo jdkVersionInfo = JdkVersionDetector.getInstance().detectJdkVersionInfo(path);
          if (jdkVersionInfo == null) continue;
          if (GradleJvmSupportMatriciesKt.isSupported(baseVersion, jdkVersionInfo.version)) {
            return path;
          }
        }
      }
      fail("Cannot find JDK for Gradle, checked paths: " + paths);
      return null;
    }
    else {
      return IdeaTestUtil.requireRealJdkHome();
    }
  }

  private String requireWslJdkHome(WSLDistribution distribution) {
    String jdkPath = System.getProperty("wsl.jdk.path");
    if (jdkPath == null) {
      jdkPath = "/usr/lib/jvm/java-11-openjdk-amd64";
    }
    return distribution.getWindowsPath(jdkPath);
  }

  protected void collectAllowedRoots(final List<String> roots, PathAssembler.LocalDistribution distribution) {
  }

  @Override
  public void tearDown() throws Exception {
    if (myJdkHome == null) {
      //super.setUp() wasn't called
      return;
    }
    new RunAll(
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
        CompilerTestUtil.deleteBuildSystemDirectory(myProject);
      },
      super::tearDown
    ).run();
  }

  @Override
  protected void collectAllowedRoots(final List<String> roots) {
    super.collectAllowedRoots(roots);
    roots.add(myJdkHome);
    roots.addAll(collectRootsInside(myJdkHome));
    roots.add(PathManager.getConfigPath());
    String gradleHomeEnv = System.getenv("GRADLE_USER_HOME");
    if (gradleHomeEnv != null) roots.add(gradleHomeEnv);
    String javaHome = Environment.getVariable("JAVA_HOME");
    if (javaHome != null) roots.add(javaHome);

    collectAllowedRoots(roots, myDistribution);
  }

  @Parameterized.Parameters(name = "{index}: with Gradle-{0}")
  public static Collection<Object[]> data() {
    String gradleVersionsString = System.getProperty("gradle.versions.to.run");
    if (gradleVersionsString != null && !gradleVersionsString.isEmpty()) {
      String[] gradleVersionsToRun = gradleVersionsString.split(",");
      return ContainerUtil.map(gradleVersionsToRun, it -> new String[]{it});
    }
    return Arrays.asList(SUPPORTED_GRADLE_VERSIONS);
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

  protected void importProject() {
    importProject((Boolean)null);
  }

  @Override
  protected void importProject(Boolean skipIndexing) {
    ExternalSystemApiUtil.subscribe(myProject, GradleConstants.SYSTEM_ID, new ExternalSystemSettingsListenerAdapter() {
      @Override
      public void onProjectsLinked(@NotNull Collection settings) {
        super.onProjectsLinked(settings);
        final Object item = ContainerUtil.getFirstItem(settings);
        if (item instanceof GradleProjectSettings) {
          ((GradleProjectSettings)item).setGradleJvm(GRADLE_JDK_NAME);
        }
      }
    });
    super.importProject(skipIndexing);
  }

  protected void importProjectUsingSingeModulePerGradleProject(@NonNls @Language("Groovy") String config, Boolean skipIndexing)
    throws IOException {
    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(false);
    importProject(config, skipIndexing);
  }

  protected void importProjectUsingSingeModulePerGradleProject(@NonNls @Language("Groovy") String config) throws IOException {
    importProjectUsingSingeModulePerGradleProject(config, null);
  }

  @Override
  protected void importProject(@NonNls @Language("Groovy") String config, Boolean skipIndexing) throws IOException {
    config = injectRepo(config);
    super.importProject(config, skipIndexing);
  }

  protected void importProject(@NonNls @Language("Groovy") String config) throws IOException {
    importProject(config, null);
  }

  protected @NotNull TestGradleBuildScriptBuilder createBuildScriptBuilder() {
    return new TestGradleBuildScriptBuilder(getCurrentGradleVersion())
      .addPrefix(MAVEN_REPOSITORY_PATCH_PLACE, "");
  }

  protected @NotNull String script(@NotNull Consumer<TestGradleBuildScriptBuilder> configure) {
    return TestGradleBuildScriptBuilder.Companion.buildscript(this, configure);
  }

  protected @NotNull String getJUnitTestAnnotationClass() {
    return GradleBuildScriptBuilderUtil.isSupportedJUnit5(getCurrentGradleVersion())
           ? "org.junit.jupiter.api.Test" : "org.junit.Test";
  }

  @Override
  protected ImportSpec createImportSpec() {
    ImportSpecBuilder importSpecBuilder = new ImportSpecBuilder(super.createImportSpec());
    importSpecBuilder.withArguments("--stacktrace");
    return importSpecBuilder.build();
  }

  private static final String MAVEN_REPOSITORY_PATCH_PLACE = "// Place for Maven repository patch";

  @NotNull
  protected String injectRepo(@NonNls @Language("Groovy") String config) {
    String mavenRepositoryPatch =
      "allprojects {\n" +
      "    repositories {\n" +
      "        maven {\n" +
      "            url 'https://repo.labs.intellij.net/repo1'\n" +
      "        }\n" +
      "    }\n" +
      "}\n";
    if (config.contains(MAVEN_REPOSITORY_PATCH_PLACE)) {
      return config.replace(MAVEN_REPOSITORY_PATCH_PLACE, mavenRepositoryPatch);
    }
    else {
      return mavenRepositoryPatch + config;
    }
  }

  @NotNull
  protected GradleRunConfiguration createEmptyGradleRunConfiguration(@NotNull String name) {
    final RunManagerEx runManager = RunManagerEx.getInstanceEx(myProject);
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

  protected VirtualFile createSettingsFile(@NonNls @Language("Groovy") String content) throws IOException {
    return createProjectSubFile("settings.gradle", content);
  }

  private PathAssembler.LocalDistribution configureWrapper() throws IOException, URISyntaxException {

    final URI distributionUri = new DistributionLocator().getDistributionFor(GradleVersion.version(gradleVersion));

    myProjectSettings.setDistributionType(DistributionType.DEFAULT_WRAPPED);
    final VirtualFile wrapperJarFrom = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(wrapperJar());
    assert wrapperJarFrom != null;

    final VirtualFile wrapperJarFromTo = createProjectSubFile("gradle/wrapper/gradle-wrapper.jar");
    WriteAction.runAndWait(() -> wrapperJarFromTo.setBinaryContent(wrapperJarFrom.contentsToByteArray()));


    Properties properties = new Properties();
    properties.setProperty("distributionBase", "GRADLE_USER_HOME");
    properties.setProperty("distributionPath", "wrapper/dists");
    properties.setProperty("zipStoreBase", "GRADLE_USER_HOME");
    properties.setProperty("zipStorePath", "wrapper/dists");
    properties.setProperty("distributionUrl", distributionUri.toString());

    StringWriter writer = new StringWriter();
    properties.store(writer, null);

    createProjectSubFile("gradle/wrapper/gradle-wrapper.properties", writer.toString());

    String projectPath = getProjectPath();
    WrapperConfiguration wrapperConfiguration = GradleUtil.getWrapperConfiguration(projectPath);
    PathAssembler pathAssembler = new PathAssembler(StartParameter.DEFAULT_GRADLE_USER_HOME, new File(projectPath));
    PathAssembler.LocalDistribution localDistribution = pathAssembler.getDistribution(wrapperConfiguration);

    File zip = localDistribution.getZipFile();
    try {
      if (zip.exists()) {
        ZipFile zipFile = new ZipFile(zip);
        zipFile.close();
      }
    }
    catch (ZipException e) {
      e.printStackTrace();
      System.out.println("Corrupted file will be removed: " + zip.getPath());
      FileUtil.delete(zip);
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    return localDistribution;
  }

  @NotNull
  private static File wrapperJar() {
    return new File(PathUtil.getJarPathForClass(GradleWrapperMain.class));
  }

  protected void assertMergedModuleCompileLibDepScope(String moduleName, String depName) {
    if (isGradleOlderThan("3.4") || isGradleNewerThan("4.5")) {
      assertModuleLibDepScope(moduleName, depName, DependencyScope.COMPILE);
    }
    else {
      assertModuleLibDepScope(moduleName, depName, DependencyScope.PROVIDED, DependencyScope.TEST, DependencyScope.RUNTIME);
    }
  }

  protected void assertMergedModuleCompileModuleDepScope(String moduleName, String depName) {
    if (isGradleOlderThan("3.4") || isGradleNewerThan("4.5")) {
      assertModuleModuleDepScope(moduleName, depName, DependencyScope.COMPILE);
    }
    else {
      assertModuleModuleDepScope(moduleName, depName, DependencyScope.PROVIDED, DependencyScope.TEST, DependencyScope.RUNTIME);
    }
  }

  protected boolean isJavaLibraryPluginSupported() {
    return GradleBuildScriptBuilderUtil.isSupportedJavaLibraryPlugin(getCurrentGradleVersion());
  }

  protected boolean isGradleOlderThan(@NotNull String ver) {
    return getCurrentGradleBaseVersion().compareTo(GradleVersion.version(ver)) < 0;
  }

  protected boolean isGradleOlderOrSameAs(@NotNull String ver) {
    return getCurrentGradleBaseVersion().compareTo(GradleVersion.version(ver)) <= 0;
  }

  protected boolean isGradleNewerOrSameAs(@NotNull String ver) {
    return getCurrentGradleBaseVersion().compareTo(GradleVersion.version(ver)) >= 0;
  }

  protected boolean isGradleNewerThan(@NotNull String ver) {
    return getCurrentGradleBaseVersion().compareTo(GradleVersion.version(ver)) > 0;
  }

  protected boolean isNewDependencyResolutionApplicable() {
    return isGradleNewerOrSameAs("4.5") && getCurrentExternalProjectSettings().isResolveModulePerSourceSet();
  }

  protected String getExtraPropertiesExtensionFqn() {
    return isGradleOlderThan("5.2") ? "org.gradle.api.internal.plugins.DefaultExtraPropertiesExtension"
                                    : "org.gradle.internal.extensibility.DefaultExtraPropertiesExtension";
  }

  protected void enableGradleDebugWithSuspend() {
    GradleSystemSettings.getInstance().setGradleVmOptions("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005");
  }
}
