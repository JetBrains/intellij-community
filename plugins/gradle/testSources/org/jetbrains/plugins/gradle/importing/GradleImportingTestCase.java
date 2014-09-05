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
package org.jetbrains.plugins.gradle.importing;

import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.test.ExternalSystemImportingTestCase;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.gradle.util.GradleVersion;
import org.gradle.wrapper.GradleWrapperMain;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.remote.GradleJavaHelper;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jetbrains.plugins.gradle.tooling.builder.AbstractModelBuilderTest.DistributionLocator;
import static org.jetbrains.plugins.gradle.tooling.builder.AbstractModelBuilderTest.SUPPORTED_GRADLE_VERSIONS;

/**
 * @author Vladislav.Soroka
 * @since 6/30/2014
 */
@RunWith(value = Parameterized.class)
public abstract class GradleImportingTestCase extends ExternalSystemImportingTestCase {

  private static final int GRADLE_DAEMON_TTL_MS = 10000;

  @Rule public TestName name = new TestName();

  @NotNull
  @org.junit.runners.Parameterized.Parameter(0)
  public String gradleVersion;
  private GradleProjectSettings myProjectSettings;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myProjectSettings = new GradleProjectSettings();
    System.setProperty(ExternalSystemExecutionSettings.REMOTE_PROCESS_IDLE_TTL_IN_MS_KEY, String.valueOf(GRADLE_DAEMON_TTL_MS));
    configureWrapper();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      Messages.setTestDialog(TestDialog.DEFAULT);
      FileUtil.delete(BuildManager.getInstance().getBuildSystemDirectory());
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  public String getName() {
    return name.getMethodName() == null ? super.getName() : FileUtil.sanitizeFileName(name.getMethodName());
  }

  @Parameterized.Parameters(name = "{index}: with Gradle-{0}")
  public static Collection<Object[]> data() throws Throwable {
    return Arrays.asList(SUPPORTED_GRADLE_VERSIONS);
  }

  @Override
  protected String getTestsTempDir() {
    return "gradleImportTests";
  }

  @Override
  protected String getExternalSystemConfigFileName() {
    return "build.gradle";
  }

  @Override
  protected void importProject(@NonNls @Language("Groovy") String config) throws IOException {
    super.importProject(config);
  }

  @Override
  protected ExternalProjectSettings getCurrentExternalProjectSettings() {
    return myProjectSettings;
  }

  @Override
  protected ProjectSystemId getExternalSystemId() {
    return GradleConstants.SYSTEM_ID;
  }

  protected VirtualFile createSettingsFile(@NonNls @Language("Groovy") String content) throws IOException {
    return createProjectSubFile("settings.gradle", content);
  }

  private void configureWrapper() throws IOException, URISyntaxException {

    final URI distributionUri = new DistributionLocator().getDistributionFor(GradleVersion.version(gradleVersion));

    myProjectSettings.setDistributionType(DistributionType.DEFAULT_WRAPPED);
    final VirtualFile wrapperJarFrom = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(wrapperJar());
    assert wrapperJarFrom != null;

    final VirtualFile wrapperJarFromTo = createProjectSubFile("gradle/wrapper/gradle-wrapper.jar");
    wrapperJarFromTo.setBinaryContent(wrapperJarFrom.contentsToByteArray());

    Properties properties = new Properties();
    properties.setProperty("distributionBase", "GRADLE_USER_HOME");
    properties.setProperty("distributionPath", "wrapper/dists");
    properties.setProperty("zipStoreBase", "GRADLE_USER_HOME");
    properties.setProperty("zipStorePath", "wrapper/dists");
    properties.setProperty("distributionUrl", distributionUri.toString());

    StringWriter writer = new StringWriter();
    properties.store(writer, null);

    createProjectSubFile("gradle/wrapper/gradle-wrapper.properties", writer.toString());
  }

  private static File wrapperJar() {
    URI location;
    try {
      location = GradleWrapperMain.class.getProtectionDomain().getCodeSource().getLocation().toURI();
    }
    catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
    if (!location.getScheme().equals("file")) {
      throw new RuntimeException(String.format("Cannot determine classpath for wrapper JAR from codebase '%s'.", location));
    }
    return new File(location.getPath());
  }
}
