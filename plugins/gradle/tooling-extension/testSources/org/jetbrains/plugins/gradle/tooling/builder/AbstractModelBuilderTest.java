/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.util.io.FileUtil;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ProjectImportAction;
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertNotNull;

/**
 * @author Vladislav.Soroka
 * @since 11/29/13
 */
@RunWith(value = Parameterized.class)
public abstract class AbstractModelBuilderTest {

  public static final String GRADLE_v1_9 = "1.9";
  public static final String GRADLE_v1_10 = "1.10";
  public static final String GRADLE_v1_11 = "1.11-rc-1";
  public static final String GRADLE_v1_12 = "1.12-20140210235036+0000";

  public static final Pattern TEST_METHOD_NAME_PATTERN;

  static {
    TEST_METHOD_NAME_PATTERN = Pattern.compile("(.*)\\[(\\d*)\\]");
  }

  private static File ourTempDir;

  @NotNull
  private final String gradleVersion;
  protected File testDir;
  protected ProjectImportAction.AllModels allModels;

  @Rule public TestName name = new TestName();

  public AbstractModelBuilderTest(@NotNull String gradleVersion) {
    this.gradleVersion = gradleVersion;
  }

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    Object[][] data = new Object[][]{
      {AbstractModelBuilderTest.GRADLE_v1_9},
      {AbstractModelBuilderTest.GRADLE_v1_10},
      {AbstractModelBuilderTest.GRADLE_v1_11},
      {AbstractModelBuilderTest.GRADLE_v1_12}};
    return Arrays.asList(data);
  }


  @Before
  public void setUp() throws Exception {
    ensureTempDirCreated();

    String methodName = name.getMethodName();
    Matcher m = TEST_METHOD_NAME_PATTERN.matcher(methodName);
    if (m.matches()) {
      methodName = m.group(1);
    }

    testDir = new File(ourTempDir, methodName);
    testDir.mkdirs();

    FileUtil.writeToFile(
      new File(testDir, GradleConstants.DEFAULT_SCRIPT_NAME),
      FileUtil.loadTextAndClose(getClass().getResourceAsStream(
                                  String.format("/%s/%s", methodName, GradleConstants.DEFAULT_SCRIPT_NAME))
      )
    );

    FileUtil.writeToFile(
      new File(testDir, GradleConstants.SETTINGS_FILE_NAME),
      FileUtil.loadTextAndClose(getClass().getResourceAsStream(
                                  String.format("/%s/%s", methodName, GradleConstants.SETTINGS_FILE_NAME))
      )
    );

    GradleConnector connector = GradleConnector.newConnector();

    DefaultGradleConnector gradleConnector = (DefaultGradleConnector)connector;
    gradleConnector.useGradleVersion(gradleVersion);
    gradleConnector.forProjectDirectory(testDir);
    ProjectConnection connection = gradleConnector.connect();

    final ProjectImportAction projectImportAction = new ProjectImportAction(false);
    projectImportAction.addExtraProjectModelClasses(getModels());
    BuildActionExecuter<ProjectImportAction.AllModels> buildActionExecutor = connection.action(projectImportAction);
    File initScript = GradleExecutionHelper.generateInitScript(false);
    assertNotNull(initScript);
    buildActionExecutor.withArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, initScript.getAbsolutePath());
    allModels = buildActionExecutor.run();
    assertNotNull(allModels);
  }

  @After
  public void tearDown() throws Exception {
    FileUtil.delete(testDir);
  }

  protected abstract Set<Class> getModels();

  private static void ensureTempDirCreated() {
    if (ourTempDir != null) return;

    ourTempDir = new File(FileUtil.getTempDirectory(), "gradleTests");
    FileUtil.delete(ourTempDir);
    ourTempDir.mkdirs();
  }
}
