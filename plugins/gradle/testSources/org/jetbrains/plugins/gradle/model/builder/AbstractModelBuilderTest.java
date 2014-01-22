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
package org.jetbrains.plugins.gradle.model.builder;

import com.intellij.openapi.util.io.FileUtil;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.service.project.ProjectImportAction;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.io.File;
import java.util.Set;

import static org.junit.Assert.assertNotNull;

/**
 * @author Vladislav.Soroka
 * @since 11/29/13
 */
public abstract class AbstractModelBuilderTest {
  private static File ourTempDir;
  protected File testDir;
  protected ProjectImportAction.AllModels allModels;

  @Rule public TestName name = new TestName();

  @Before
  public void setUp() throws Exception {
    ensureTempDirCreated();
    testDir = new File(ourTempDir, name.getMethodName());
    testDir.mkdirs();

    FileUtil.writeToFile(
      new File(testDir, GradleConstants.DEFAULT_SCRIPT_NAME),
      FileUtil.loadTextAndClose(getClass().getResourceAsStream(
        String.format("/%s/%s", name.getMethodName(), GradleConstants.DEFAULT_SCRIPT_NAME))
      )
    );

    FileUtil.writeToFile(
      new File(testDir, GradleConstants.SETTINGS_FILE_NAME),
      FileUtil.loadTextAndClose(getClass().getResourceAsStream(
        String.format("/%s/%s", name.getMethodName(), GradleConstants.SETTINGS_FILE_NAME))
      )
    );

    GradleConnector connector = GradleConnector.newConnector();

    DefaultGradleConnector gradleConnector = (DefaultGradleConnector)connector;
    gradleConnector.forProjectDirectory(testDir);
    ProjectConnection connection = gradleConnector.connect();

    final ProjectImportAction projectImportAction = new ProjectImportAction(true);
    projectImportAction.addExtraProjectModelClasses(getModels());
    BuildActionExecuter<ProjectImportAction.AllModels> buildActionExecutor = connection.action(projectImportAction);
    GradleExecutionHelper.setInitScript(buildActionExecutor, false);

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
