/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.tooling.builder;

import com.google.common.collect.Multimap;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.codehaus.groovy.runtime.typehandling.ShortTypeHandling;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.BuildScriptClasspathModel;
import org.jetbrains.plugins.gradle.model.ClasspathEntryModel;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.model.ProjectImportAction;
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeThat;

/**
 * @author Vladislav.Soroka
 * @since 11/29/13
 */
@RunWith(value = Parameterized.class)
public abstract class AbstractModelBuilderTest {

  public static final Object[][] SUPPORTED_GRADLE_VERSIONS = {
    {"1.9"}, /*{"1.10"}, {"1.11"},*/ {"1.12"},
    {"2.0"}, /*{"2.1"}, {"2.2"} , {"2.3"}, {"2.4"}, */{"2.5"}, /*{"2.6"}, {"2.7"}, {"2.8"},*/ {"2.9"}, /*{"2.10"}, {"2.11"}, {"2.12"}, {"2.13"}, */{"2.14.1"},
    {"3.0"}, {"3.1"}
  };
  public static final String BASE_GRADLE_VERSION = String.valueOf(SUPPORTED_GRADLE_VERSIONS[SUPPORTED_GRADLE_VERSIONS.length - 1][0]);

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
  public static Collection<Object[]> data() {
    return Arrays.asList(SUPPORTED_GRADLE_VERSIONS);
  }


  @Before
  public void setUp() throws Exception {
    assumeThat(gradleVersion, versionMatcherRule.getMatcher());

    ensureTempDirCreated();

    String methodName = name.getMethodName();
    Matcher m = TEST_METHOD_NAME_PATTERN.matcher(methodName);
    if (m.matches()) {
      methodName = m.group(1);
    }

    testDir = new File(ourTempDir, methodName);
    FileUtil.ensureExists(testDir);

    final InputStream buildScriptStream = getClass().getResourceAsStream("/" + methodName + "/" + GradleConstants.DEFAULT_SCRIPT_NAME);
    try {
      FileUtil.writeToFile(
        new File(testDir, GradleConstants.DEFAULT_SCRIPT_NAME),
        FileUtil.loadTextAndClose(buildScriptStream)
      );
    }
    finally {
      StreamUtil.closeStream(buildScriptStream);
    }

    final InputStream settingsStream = getClass().getResourceAsStream("/" + methodName + "/" + GradleConstants.SETTINGS_FILE_NAME);
    try {
      if(settingsStream != null) {
        FileUtil.writeToFile(
          new File(testDir, GradleConstants.SETTINGS_FILE_NAME),
          FileUtil.loadTextAndClose(settingsStream)
        );
      }
    } finally {
      StreamUtil.closeStream(settingsStream);
    }

    GradleConnector connector = GradleConnector.newConnector();

    final URI distributionUri = new DistributionLocator().getDistributionFor(GradleVersion.version(gradleVersion));
    connector.useDistribution(distributionUri);
    connector.forProjectDirectory(testDir);
    int daemonMaxIdleTime = 10;
    try {
      daemonMaxIdleTime = Integer.parseInt(System.getProperty("gradleDaemonMaxIdleTime", "10"));
    }
    catch (NumberFormatException ignore) {}

    ((DefaultGradleConnector)connector).daemonMaxIdleTime(daemonMaxIdleTime, TimeUnit.SECONDS);
    ProjectConnection connection = connector.connect();

    try {
      final ProjectImportAction projectImportAction = new ProjectImportAction(false);
      projectImportAction.addExtraProjectModelClasses(getModels());
      BuildActionExecuter<ProjectImportAction.AllModels> buildActionExecutor = connection.action(projectImportAction);
      File initScript = GradleExecutionHelper.generateInitScript(false, getToolingExtensionClasses());
      assertNotNull(initScript);
      String jdkHome = IdeaTestUtil.requireRealJdkHome();
      buildActionExecutor.setJavaHome(new File(jdkHome));
      buildActionExecutor.setJvmArguments("-Xmx128m", "-XX:MaxPermSize=64m");
      buildActionExecutor.withArguments("--info", "--recompile-scripts", GradleConstants.INIT_SCRIPT_CMD_OPTION, initScript.getAbsolutePath());
      allModels = buildActionExecutor.run();
      assertNotNull(allModels);
    } finally {
      connection.close();
    }
  }

  @NotNull
  private Set<Class> getToolingExtensionClasses() {
    final Set<Class> classes = ContainerUtil.<Class>set(
      ExternalProject.class,
      // gradle-tooling-extension-api jar
      ProjectImportAction.class,
      // gradle-tooling-extension-impl jar
      ModelBuildScriptClasspathBuilderImpl.class,
      Multimap.class,
      ShortTypeHandling.class
    );

    ContainerUtil.addAllNotNull(classes, doGetToolingExtensionClasses());
    return classes;
  }

  @NotNull
  protected Set<Class> doGetToolingExtensionClasses() {
    return Collections.emptySet();
  }

  @After
  public void tearDown() throws Exception {
    if (testDir != null) {
      FileUtil.delete(testDir);
    }
  }

  protected abstract Set<Class> getModels();


  protected <T> Map<String, T> getModulesMap(final Class<T> aClass) {
    final DomainObjectSet<? extends IdeaModule> ideaModules = allModels.getIdeaProject().getModules();

    final String filterKey = "to_filter";
    final Map<String, T> map = ContainerUtil.map2Map(ideaModules, new Function<IdeaModule, Pair<String, T>>() {
      @Override
      public Pair<String, T> fun(IdeaModule module) {
        final T value = allModels.getExtraProject(module, aClass);
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
      "http://services.gradle.org-mirror.labs.intellij.net/distributions";
    private static final String INTELLIJ_LABS_GRADLE_SNAPSHOT_MIRROR =
      "http://services.gradle.org-mirror.labs.intellij.net/distributions-snapshots";
    private static final String GRADLE_RELEASE_REPO = "http://services.gradle.org/distributions";
    private static final String GRADLE_SNAPSHOT_REPO = "http://services.gradle.org/distributions-snapshots";

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
