// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder;

import com.amazon.ion.IonType;
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.JavaVersion;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import org.codehaus.groovy.runtime.typehandling.ShortTypeHandling;
import org.gradle.internal.impldep.com.google.common.collect.Multimap;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.util.GradleVersion;
import org.hamcrest.CustomMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilderUtil;
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix;
import org.jetbrains.plugins.gradle.model.BuildScriptClasspathModel;
import org.jetbrains.plugins.gradle.model.ClassSetImportModelProvider;
import org.jetbrains.plugins.gradle.model.ClasspathEntryModel;
import org.jetbrains.plugins.gradle.model.ProjectImportAction;
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.service.execution.GradleInitScriptUtil;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule;
import org.jetbrains.plugins.gradle.tooling.internal.init.Init;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

/**
 * @author Vladislav.Soroka
 */
@RunWith(value = Parameterized.class)
public abstract class AbstractModelBuilderTest {

  public static final Pattern TEST_METHOD_NAME_PATTERN = Pattern.compile("(.*)\\[(\\d*: with Gradle-.*)\\]");

  private static File ourTempDir;

  @NotNull
  protected final String gradleVersion;
  protected File testDir;
  protected ProjectImportAction.AllModels allModels;

  @Rule public TestName name = new TestName();
  @Rule public VersionMatcherRule versionMatcherRule = new VersionMatcherRule();

  public AbstractModelBuilderTest(@NotNull String gradleVersion) {
    this.gradleVersion = gradleVersion;
  }

  @Parameterized.Parameters(name = "{index}: with Gradle-{0}")
  public static Iterable<?> data() {
    return Arrays.asList(VersionMatcherRule.SUPPORTED_GRADLE_VERSIONS);
  }


  @Before
  public void setUp() throws Exception {
    assumeThat(gradleVersion, versionMatcherRule.getMatcher());
    assumeGradleCompatibleWithJava(gradleVersion);

    ensureTempDirCreated();

    String methodName = name.getMethodName();
    Matcher m = TEST_METHOD_NAME_PATTERN.matcher(methodName);
    if (m.matches()) {
      methodName = m.group(1);
    }

    testDir = new File(ourTempDir, methodName);
    FileUtil.ensureExists(testDir);

    GradleVersion _gradleVersion = GradleVersion.version(gradleVersion);
    String compileConfiguration = GradleBuildScriptBuilderUtil.isJavaLibraryPluginSupported(_gradleVersion) ? "implementation" : "compile";
    String testCompileConfiguration = GradleBuildScriptBuilderUtil.isJavaLibraryPluginSupported(_gradleVersion)
                                      ? "testImplementation" : "testCompile";
    String integrationTestCompileConfiguration = GradleBuildScriptBuilderUtil.isJavaLibraryPluginSupported(_gradleVersion)
                                                 ? "integrationTestImplementation"
                                                 : "integrationTestCompile";
    try (InputStream buildScriptStream = getClass().getResourceAsStream('/' + methodName + '/' + GradleConstants.DEFAULT_SCRIPT_NAME)) {
      String text = StreamUtil.readText(new InputStreamReader(buildScriptStream, StandardCharsets.UTF_8));
      text = text.replaceAll("<<compile>>", compileConfiguration);
      text = text.replaceAll("<<testCompile>>", testCompileConfiguration);
      text = text.replaceAll("<<integrationTestCompile>>", testCompileConfiguration);
      FileUtil.writeToFile(new File(testDir, GradleConstants.DEFAULT_SCRIPT_NAME), text, StandardCharsets.UTF_8);
    }

    try (InputStream settingsStream = getClass().getResourceAsStream('/' + methodName + '/' + GradleConstants.SETTINGS_FILE_NAME)) {
      if (settingsStream != null) {
        Files.copy(settingsStream, new File(testDir, GradleConstants.SETTINGS_FILE_NAME).toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
    }

    // fix exception of FJP at org.gradle.process.internal.ExecHandleRunner.run => ... => net.rubygrapefruit.platform.internal.DefaultProcessLauncher.start => java.lang.ProcessBuilder.start => java.lang.ProcessHandleImpl.completion
    IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true);
    GradleConnector connector = GradleConnector.newConnector();

    final URI distributionUri = new DistributionLocator().getDistributionFor(_gradleVersion);
    connector.useDistribution(distributionUri);
    connector.forProjectDirectory(testDir);
    int daemonMaxIdleTime = 10;
    try {
      daemonMaxIdleTime = Integer.parseInt(System.getProperty("gradleDaemonMaxIdleTime", "10"));
    }
    catch (NumberFormatException ignore) {
    }

    ((DefaultGradleConnector)connector).daemonMaxIdleTime(daemonMaxIdleTime, TimeUnit.SECONDS);

    try (ProjectConnection connection = connector.connect()) {
      boolean isCompositeBuildsSupported = _gradleVersion.compareTo(GradleVersion.version("3.1")) >= 0;
      final ProjectImportAction projectImportAction = new ProjectImportAction(false, isCompositeBuildsSupported);
      projectImportAction.addProjectImportModelProvider(new ClassSetImportModelProvider(getModels(),
                                                                                        Collections.<Class<?>>singleton(IdeaProject.class)));
      BuildActionExecuter<ProjectImportAction.AllModels> buildActionExecutor = connection.action(projectImportAction);
      GradleExecutionSettings executionSettings = new GradleExecutionSettings(null, null, DistributionType.BUNDLED, false);
      GradleExecutionHelper.attachTargetPathMapperInitScript(executionSettings);
      Path initScript = GradleInitScriptUtil.createMainInitScript(false, getToolingExtensionClasses());
      executionSettings.withArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, initScript.toString());

      buildActionExecutor.withArguments(executionSettings.getArguments());
      String jdkHome = IdeaTestUtil.requireRealJdkHome();
      buildActionExecutor.setJavaHome(new File(jdkHome));
      buildActionExecutor.setJvmArguments("-Xmx128m", "-XX:MaxPermSize=64m");
      allModels = buildActionExecutor.run();
      assertNotNull(allModels);
    }
  }

  public static void assumeGradleCompatibleWithJava(@NotNull String gradleVersion) {
    assumeTrue("Gradle [" + gradleVersion + "] cannot be execution on Java [" + JavaVersion.current() + "]",
               GradleJvmSupportMatrix.isSupported(GradleVersion.version(gradleVersion), JavaVersion.current()));

    if (GradleVersion.version(gradleVersion).getBaseVersion().compareTo(GradleVersion.version("4.8")) < 0) {
      assumeThat(JavaVersion.current().feature, new CustomMatcher<Integer>("Java version older than 9") {
        @Override
        public boolean matches(Object item) {
          return item instanceof Integer && ((Integer)item).compareTo(9) < 0;
        }
      });
    }
  }

  @NotNull
  public static Set<Class<?>> getToolingExtensionClasses() {
    return ContainerUtil.immutableSet(
      // external-system-rt.jar
      ExternalSystemSourceType.class,
      // gradle-tooling-extension-api jar
      ProjectImportAction.class,
      // gradle-tooling-extension-impl jar
      Init.class,
      Multimap.class,
      ShortTypeHandling.class,
      // fastutil
      Object2ObjectMap.class,
      // ion-java jar
      IonType.class,
      // util-rt jat
      SystemInfoRt.class // !!! do not replace it with SystemInfo.class from util module
    );
  }

  @After
  public void tearDown() {
    if (testDir != null) {
      FileUtil.delete(testDir);
    }
  }

  protected abstract Set<Class<?>> getModels();


  protected <T> Map<String, T> getModulesMap(final Class<T> aClass) {
    final DomainObjectSet<? extends IdeaModule> ideaModules = allModels.getModel(IdeaProject.class).getModules();

    final String filterKey = "to_filter";
    final Map<String, T> map = ContainerUtil.map2Map(ideaModules, new Function<IdeaModule, Pair<String, T>>() {
      @Override
      public Pair<String, T> fun(IdeaModule module) {
        final T value = allModels.getModel(module, aClass);
        final String key = value != null ? module.getGradleProject().getPath() : filterKey;
        return Pair.create(key, value);
      }
    });

    map.remove(filterKey);
    return map;
  }

  protected void assertBuildClasspath(String projectPath, String... classpath) {
    final Map<String, BuildScriptClasspathModel> classpathModelMap = getModulesMap(BuildScriptClasspathModel.class);
    final BuildScriptClasspathModel classpathModel = classpathModelMap.get(projectPath);

    assertNotNull(classpathModel);

    final List<? extends ClasspathEntryModel> classpathEntryModels = classpathModel.getClasspath().getAll();
    assertEquals(classpath.length, classpathEntryModels.size());

    for (int i = 0, length = classpath.length; i < length; i++) {
      String classpathEntry = classpath[i];
      final ClasspathEntryModel classpathEntryModel = classpathEntryModels.get(i);
      assertNotNull(classpathEntryModel);
      assertEquals(1, classpathEntryModel.getClasses().size());
      final String path = classpathEntryModel.getClasses().iterator().next();
      assertEquals(classpathEntry, new File(path).getName());
    }
  }

  private static void ensureTempDirCreated() throws IOException {
    if (ourTempDir != null) return;

    ourTempDir = new File(FileUtil.getTempDirectory(), "gradleTests");
    FileUtil.delete(ourTempDir);
    FileUtil.ensureExists(ourTempDir);
  }

  public static class DistributionLocator {
    private static final String RELEASE_REPOSITORY_ENV = "GRADLE_RELEASE_REPOSITORY";
    private static final String SNAPSHOT_REPOSITORY_ENV = "GRADLE_SNAPSHOT_REPOSITORY";
    private static final String INTELLIJ_LABS_GRADLE_RELEASE_MIRROR =
      "https://cache-redirector.jetbrains.com/downloads.gradle.org/distributions";
    private static final String INTELLIJ_LABS_GRADLE_SNAPSHOT_MIRROR =
      "https://cache-redirector.jetbrains.com/downloads.gradle.org/distributions-snapshots";
    private static final String GRADLE_RELEASE_REPO = "https://services.gradle.org/distributions";
    private static final String GRADLE_SNAPSHOT_REPO = "https://services.gradle.org/distributions-snapshots";

    @NotNull private final String myReleaseRepoUrl;
    @NotNull private final String mySnapshotRepoUrl;

    public DistributionLocator() {
      this(DistributionLocator.getRepoUrl(false), DistributionLocator.getRepoUrl(true));
    }

    public DistributionLocator(@NotNull String releaseRepoUrl, @NotNull String snapshotRepoUrl) {
      myReleaseRepoUrl = releaseRepoUrl;
      mySnapshotRepoUrl = snapshotRepoUrl;
    }

    @NotNull
    public URI getDistributionFor(@NotNull GradleVersion version) throws URISyntaxException {
      return getDistribution(getDistributionRepository(version), version, "gradle", "bin");
    }

    @NotNull
    private String getDistributionRepository(@NotNull GradleVersion version) {
      return version.isSnapshot() ? mySnapshotRepoUrl : myReleaseRepoUrl;
    }

    private static URI getDistribution(@NotNull String repositoryUrl,
                                       @NotNull GradleVersion version,
                                       @NotNull String archiveName,
                                       @NotNull String archiveClassifier) throws URISyntaxException {
      return new URI(String.format("%s/%s-%s-%s.zip", repositoryUrl, archiveName, version.getVersion(), archiveClassifier));
    }

    @NotNull
    public static String getRepoUrl(boolean isSnapshotUrl) {
      final String envRepoUrl = System.getenv(isSnapshotUrl ? SNAPSHOT_REPOSITORY_ENV : RELEASE_REPOSITORY_ENV);
      if (envRepoUrl != null) return envRepoUrl;

      if (UsefulTestCase.IS_UNDER_TEAMCITY) {
        return isSnapshotUrl ? INTELLIJ_LABS_GRADLE_SNAPSHOT_MIRROR : INTELLIJ_LABS_GRADLE_RELEASE_MIRROR;
      }

      return isSnapshotUrl ? GRADLE_SNAPSHOT_REPO : GRADLE_RELEASE_REPO;
    }
  }
}
