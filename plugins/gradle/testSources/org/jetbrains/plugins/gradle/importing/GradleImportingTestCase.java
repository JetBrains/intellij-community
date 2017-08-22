/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListenerAdapter;
import com.intellij.openapi.externalSystem.test.ExternalSystemImportingTestCase;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.PathKt;
import org.gradle.StartParameter;
import org.gradle.util.GradleVersion;
import org.gradle.wrapper.GradleWrapperMain;
import org.gradle.wrapper.PathAssembler;
import org.gradle.wrapper.WrapperConfiguration;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule;
import org.jetbrains.plugins.gradle.tooling.builder.AbstractModelBuilderTest;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleUtil;
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
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import static org.jetbrains.plugins.gradle.tooling.builder.AbstractModelBuilderTest.DistributionLocator;
import static org.jetbrains.plugins.gradle.tooling.builder.AbstractModelBuilderTest.SUPPORTED_GRADLE_VERSIONS;
import static org.junit.Assume.assumeThat;

@RunWith(value = Parameterized.class)
public abstract class GradleImportingTestCase extends ExternalSystemImportingTestCase {
  public static final String BASE_GRADLE_VERSION = AbstractModelBuilderTest.BASE_GRADLE_VERSION;
  protected static final String GRADLE_JDK_NAME = "Gradle JDK";
  private static final int GRADLE_DAEMON_TTL_MS = 10000;

  @Rule public TestName name = new TestName();

  @Rule public VersionMatcherRule versionMatcherRule = new VersionMatcherRule();
  @NotNull
  @org.junit.runners.Parameterized.Parameter(0)
  public String gradleVersion;
  private GradleProjectSettings myProjectSettings;
  private String myJdkHome;

  @Override
  public void setUp() throws Exception {
    myJdkHome = IdeaTestUtil.requireRealJdkHome();
    super.setUp();
    assumeThat(gradleVersion, versionMatcherRule.getMatcher());
    new WriteAction() {
      @Override
      protected void run(@NotNull Result result) {
        Sdk oldJdk = ProjectJdkTable.getInstance().findJdk(GRADLE_JDK_NAME);
        if (oldJdk != null) {
          ProjectJdkTable.getInstance().removeJdk(oldJdk);
        }
        VirtualFile jdkHomeDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myJdkHome));
        Sdk jdk = SdkConfigurationUtil.setupSdk(new Sdk[0], jdkHomeDir, JavaSdk.getInstance(), true, null, GRADLE_JDK_NAME);
        assertNotNull("Cannot create JDK for " + myJdkHome, jdk);
        ProjectJdkTable.getInstance().addJdk(jdk);
      }
    }.execute();
    myProjectSettings = new GradleProjectSettings();
    GradleSettings.getInstance(myProject).setGradleVmOptions("-Xmx128m -XX:MaxPermSize=64m");
    System.setProperty(ExternalSystemExecutionSettings.REMOTE_PROCESS_IDLE_TTL_IN_MS_KEY, String.valueOf(GRADLE_DAEMON_TTL_MS));
    configureWrapper();
  }

  @Override
  public void tearDown() throws Exception {
    if (myJdkHome == null) {
      //super.setUp() wasn't called
      return;
    }

    try {
      new WriteAction() {
        @Override
        protected void run(@NotNull Result result) {
          Sdk old = ProjectJdkTable.getInstance().findJdk(GRADLE_JDK_NAME);
          if (old != null) {
            SdkConfigurationUtil.removeSdk(old);
          }
        }
      }.execute();
      Messages.setTestDialog(TestDialog.DEFAULT);
      deleteBuildSystemDirectory();
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected void collectAllowedRoots(final List<String> roots) {
    roots.add(myJdkHome);
    roots.addAll(collectRootsInside(myJdkHome));
    roots.add(PathManager.getConfigPath());
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

  protected void importProjectUsingSingeModulePerGradleProject() {
    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(false);
    importProject();
  }

  @Override
  protected void importProject() {
    ExternalSystemApiUtil.subscribe(myProject, GradleConstants.SYSTEM_ID, new ExternalSystemSettingsListenerAdapter() {
      @Override
      public void onProjectsLinked(@NotNull Collection settings) {
        final Object item = ContainerUtil.getFirstItem(settings);
        if (item instanceof GradleProjectSettings) {
          ((GradleProjectSettings)item).setGradleJvm(GRADLE_JDK_NAME);
        }
      }
    });
    super.importProject();
  }

  protected void importProjectUsingSingeModulePerGradleProject(@NonNls @Language("Groovy") String config) throws IOException {
    getCurrentExternalProjectSettings().setResolveModulePerSourceSet(false);
    importProject(config);
  }

  @Override
  protected void importProject(@NonNls @Language("Groovy") String config) throws IOException {
    config = injectRepo(config);
    super.importProject(config);
  }

  @NotNull
  protected String injectRepo(@NonNls @Language("Groovy") String config) {
    config = "allprojects {\n" +
              "  repositories {\n" +
              "    maven {\n" +
              "        url 'http://maven.labs.intellij.net/repo1'\n" +
              "    }\n" +
              "  }" +
              "}\n" + config;
    return config;
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

  protected boolean isGradle40orNewer() {
    return GradleVersion.version(gradleVersion).compareTo(GradleVersion.version("4.0")) >= 0;
  }

  private void configureWrapper() throws IOException, URISyntaxException {

    final URI distributionUri = new DistributionLocator().getDistributionFor(GradleVersion.version(gradleVersion));

    myProjectSettings.setDistributionType(DistributionType.DEFAULT_WRAPPED);
    final VirtualFile wrapperJarFrom = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(wrapperJar());
    assert wrapperJarFrom != null;

    final VirtualFile wrapperJarFromTo = createProjectSubFile("gradle/wrapper/gradle-wrapper.jar");
    new WriteAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        wrapperJarFromTo.setBinaryContent(wrapperJarFrom.contentsToByteArray());
      }
    }.execute().throwException();


    Properties properties = new Properties();
    properties.setProperty("distributionBase", "GRADLE_USER_HOME");
    properties.setProperty("distributionPath", "wrapper/dists");
    properties.setProperty("zipStoreBase", "GRADLE_USER_HOME");
    properties.setProperty("zipStorePath", "wrapper/dists");
    properties.setProperty("distributionUrl", distributionUri.toString());

    StringWriter writer = new StringWriter();
    properties.store(writer, null);

    createProjectSubFile("gradle/wrapper/gradle-wrapper.properties", writer.toString());

    WrapperConfiguration wrapperConfiguration = GradleUtil.getWrapperConfiguration(getProjectPath());
    PathAssembler.LocalDistribution localDistribution = new PathAssembler(
      StartParameter.DEFAULT_GRADLE_USER_HOME).getDistribution(wrapperConfiguration);

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
  }

  @NotNull
  private static File wrapperJar() {
    return new File(PathUtil.getJarPathForClass(GradleWrapperMain.class));
  }
}
