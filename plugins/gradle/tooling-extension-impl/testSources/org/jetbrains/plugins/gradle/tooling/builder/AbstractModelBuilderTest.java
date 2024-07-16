// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder;

import com.amazon.ion.IonType;
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.gradle.toolingExtension.GradleToolingExtensionClass;
import com.intellij.gradle.toolingExtension.impl.GradleToolingExtensionImplClass;
import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelFetchAction;
import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelHolderState;
import com.intellij.gradle.toolingExtension.modelProvider.GradleClassBuildModelProvider;
import com.intellij.gradle.toolingExtension.modelProvider.GradleClassProjectModelProvider;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.platform.externalSystem.rt.ExternalSystemRtClass;
import com.intellij.testFramework.ApplicationRule;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import org.codehaus.groovy.runtime.typehandling.ShortTypeHandling;
import org.gradle.internal.impldep.com.google.common.collect.Multimap;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider;
import org.jetbrains.plugins.gradle.service.modelAction.GradleIdeaModelHolder;
import org.jetbrains.plugins.gradle.service.execution.GradleInitScriptUtil;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.tooling.GradleJvmResolver;
import org.jetbrains.plugins.gradle.tooling.TargetJavaVersionWatcher;
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleUtil;
import org.junit.*;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Vladislav.Soroka
 */
@RunWith(Parameterized.class)
public abstract class AbstractModelBuilderTest {

  public static final Pattern TEST_METHOD_NAME_PATTERN = Pattern.compile("(.*)\\[(\\d*: with Gradle-.*)]");

  @Rule public TestName name = new TestName();
  @Rule public VersionMatcherRule versionMatcherRule = new VersionMatcherRule();
  @Rule public TargetJavaVersionWatcher myTargetJavaVersionWatcher = new TargetJavaVersionWatcher();

  @ClassRule public static final ApplicationRule ourApplicationRule = new ApplicationRule();

  protected final @NotNull GradleVersion gradleVersion;

  private static File ourTempDir;
  private File testDir;
  private String gradleJvmHomePath;

  public AbstractModelBuilderTest(@NotNull String gradleVersion) {
    this.gradleVersion = GradleVersion.version(gradleVersion);
  }

  @Parameterized.Parameters(name = "{index}: with Gradle-{0}")
  public static Iterable<?> data() {
    return VersionMatcherRule.getSupportedGradleVersions();
  }

  @Before
  public void setUp() {
    Assume.assumeThat(gradleVersion.getVersion(), versionMatcherRule.getMatcher());

    // fix exception of FJP at org.gradle.process.internal.ExecHandleRunner.run => ... => net.rubygrapefruit.platform.internal.DefaultProcessLauncher.start => java.lang.ProcessBuilder.start => java.lang.ProcessHandleImpl.completion
    IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true);

    setUpGradleJvmHomePath();
    setUpTemporaryTestDirectory();
  }

  @After
  public void tearDown() {
    if (testDir != null) {
      FileUtil.delete(testDir);
    }
  }

  private @NotNull String getTestMethodName() {
    String methodName = name.getMethodName();
    Matcher matcher = TEST_METHOD_NAME_PATTERN.matcher(methodName);
    if (matcher.matches()) {
      return matcher.group(1);
    }
    return methodName;
  }

  private void setUpGradleJvmHomePath() {
    gradleJvmHomePath = GradleJvmResolver.resolveGradleJvmHomePath(gradleVersion, myTargetJavaVersionWatcher.getRestriction());
  }

  private void setUpTemporaryTestDirectory() {
    try {
      if (ourTempDir == null) {
        ourTempDir = new File(FileUtil.getTempDirectory(), "gradleTests");
        FileUtil.delete(ourTempDir);
        FileUtil.ensureExists(ourTempDir);
      }
      testDir = new File(ourTempDir, getTestMethodName());
      FileUtil.delete(testDir);
      FileUtil.ensureExists(testDir);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void createProjectFile(@NotNull String relativePath, @NotNull String content) {
    try {
      File file = testDir.toPath().resolve(relativePath).toFile();
      FileUtil.writeToFile(file, content);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public @NotNull GradleIdeaModelHolder runBuildAction(Class<?>... projectModels) {
    return runBuildAction(GradleClassProjectModelProvider.createAll(projectModels));
  }

  public @NotNull GradleIdeaModelHolder runBuildAction(ProjectImportModelProvider... modelProviders) {
    return runBuildAction(Arrays.asList(modelProviders));
  }

  private @NotNull GradleIdeaModelHolder runBuildAction(@NotNull List<? extends ProjectImportModelProvider> modelProviders) {
    GradleModelFetchAction buildAction = new GradleModelFetchAction()
      .addProjectImportModelProviders(GradleClassBuildModelProvider.createAll(IdeaProject.class))
      .addProjectImportModelProviders(modelProviders);

    Path targetPathMapperInitScript = GradleInitScriptUtil.createTargetPathMapperInitScript();
    Path mainInitScript = GradleInitScriptUtil.createMainInitScript(false, getToolingExtensionClasses());
    ExternalSystemExecutionSettings executionSettings = new GradleExecutionSettings(null, null, DistributionType.BUNDLED, false)
      .withArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, targetPathMapperInitScript.toString())
      .withArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, mainInitScript.toString())
      .withVmOptions("-Xmx128m", "-XX:MaxPermSize=64m", "-Dorg.gradle.warning.mode=fail");

    GradleConnector connector = GradleConnector.newConnector()
      .useDistribution(GradleUtil.getWrapperDistributionUri(gradleVersion))
      .forProjectDirectory(testDir);
    ((DefaultGradleConnector)connector).daemonMaxIdleTime(getDaemonMaxIdleTimeSeconds(), TimeUnit.SECONDS);

    try (ProjectConnection connection = connector.connect()) {
      GradleModelHolderState state = connection.action(buildAction)
        .setStandardError(System.err)
        .setStandardOutput(System.out)
        .setJavaHome(new File(gradleJvmHomePath))
        .withArguments(executionSettings.getArguments())
        .setJvmArguments(executionSettings.getJvmArguments())
        .run();
      Assert.assertNotNull(state);

      GradleIdeaModelHolder models = new GradleIdeaModelHolder();
      models.addState(state);
      return models;
    }
  }

  private static int getDaemonMaxIdleTimeSeconds() {
    try {
      return Integer.parseInt(System.getProperty("gradleDaemonMaxIdleTime", "5"));
    }
    catch (NumberFormatException ignore) {
    }
    return 5;
  }

  @NotNull
  public static Set<Class<?>> getToolingExtensionClasses() {
    return ContainerUtil.newHashSet(
      ExternalSystemRtClass.class, // intellij.platform.externalSystem.rt
      GradleToolingExtensionClass.class, // intellij.gradle.toolingExtension
      GradleToolingExtensionImplClass.class, // intellij.gradle.toolingExtension.impl
      Multimap.class, // repacked gradle guava
      ShortTypeHandling.class, // groovy
      Object2ObjectMap.class, // fastutil
      IonType.class,  // ion-java jar
      SystemInfoRt.class // jar containing classes of `intellij.platform.util.rt` module
    );
  }
}
