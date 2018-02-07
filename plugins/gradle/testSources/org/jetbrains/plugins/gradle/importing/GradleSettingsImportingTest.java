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

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.JavaApplicationRunConfigurationImporter;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.UnknownConfigurationType;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemBeforeRunTask;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.settings.FacetConfigurationImporter;
import com.intellij.openapi.externalSystem.service.project.settings.RunConfigurationImporter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.openapi.externalSystem.service.project.settings.ConfigurationDataService.EXTERNAL_SYSTEM_CONFIGURATION_IMPORT_ENABLED;

/**
 * Created by Nikita.Skvortsov
 * date: 18.09.2017.
 */
public class GradleSettingsImportingTest extends GradleImportingTestCase {


  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  @Parameterized.Parameters(name = "with Gradle-{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{{"4.5"}});
  }

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    Registry.get(EXTERNAL_SYSTEM_CONFIGURATION_IMPORT_ENABLED).setValue(true);
  }

  @After
  @Override
  public void tearDown() throws Exception {
    try {
      Registry.get(EXTERNAL_SYSTEM_CONFIGURATION_IMPORT_ENABLED).resetToDefault();
    } finally {
      super.tearDown();
    }
  }

  @Test
  public void testInspectionSettingsImport() throws Exception {
    importProject(
      withGradleIdeaExtPlugin(
        "import org.jetbrains.gradle.ext.*\n" +
        "idea {\n" +
        "  project.settings {\n" +
        "    inspections {\n" +
        "    }\n" +
        "  }\n" +
        "}")
    );

    final InspectionProfileImpl profile = InspectionProfileManager.getInstance(myProject).getCurrentProfile();
    assertEquals("Gradle Imported", profile.getName());
  }

  @Test
  public void testCodeStyleSettingsImport() throws Exception {
    importProject(
      withGradleIdeaExtPlugin(
        "import org.jetbrains.gradle.ext.*\n" +
      "idea {\n" +
      "  project.settings {\n" +
      "    codeStyle {\n" +
      "      hardWrapAt = 200\n" +
      "    }\n" +
      "  }\n" +
      "}")
    );

    final CodeStyleScheme scheme = CodeStyleSchemes.getInstance().getCurrentScheme();
    final CodeStyleSettings settings = scheme.getCodeStyleSettings();

    assertEquals("Gradle Imported", scheme.getName());
    assertFalse(scheme.isDefault());

    assertEquals(200, settings.getDefaultRightMargin());
  }

  @Test
  public void testCompilerConfigurationSettingsImport() throws Exception {

    importProject(
      withGradleIdeaExtPlugin(
      "idea {\n" +
      "  project.settings {\n" +
      "    compiler {\n" +
      "      resourcePatterns '!*.java;!*.class'\n" +
      "      clearOutputDirectory false\n" +
      "      addNotNullAssertions false\n" +
      "      autoShowFirstErrorInEditor false\n" +
      "      displayNotificationPopup false\n" +
      "      enableAutomake false\n" +
      "      parallelCompilation true\n" +
      "      rebuildModuleOnDependencyChange false\n" +
      "    }\n" +
      "  }\n" +
      "}")
    );

    final CompilerConfigurationImpl compilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);
    final CompilerWorkspaceConfiguration workspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(myProject);

    assertSameElements(compilerConfiguration.getResourceFilePatterns(), "!*.class", "!*.java");
    assertFalse(workspaceConfiguration.CLEAR_OUTPUT_DIRECTORY);
    assertFalse(compilerConfiguration.isAddNotNullAssertions());
    assertFalse(workspaceConfiguration.AUTO_SHOW_ERRORS_IN_EDITOR);
    assertFalse(workspaceConfiguration.DISPLAY_NOTIFICATION_POPUP);
    assertFalse(workspaceConfiguration.MAKE_PROJECT_ON_SAVE);
    assertTrue(workspaceConfiguration.PARALLEL_COMPILATION);
    assertFalse(workspaceConfiguration.REBUILD_ON_DEPENDENCY_CHANGE);
  }

  @Test
  public void testApplicationRunConfigurationSettingsImport() throws Exception {
    TestRunConfigurationImporter testExtension = new TestRunConfigurationImporter("application");
    ExtensionPoint<RunConfigurationImporter> ep = Extensions.getRootArea().getExtensionPoint(RunConfigurationImporter.EP_NAME);
    ep.reset();
    ep.registerExtension(testExtension);

    createSettingsFile("rootProject.name = 'moduleName'");
    importProject(
      withGradleIdeaExtPlugin(
      "import org.jetbrains.gradle.ext.*\n" +
      "idea {\n" +
      "  project.settings {\n" +
      "    runConfigurations {\n" +
      "       app1(Application) {\n" +
      "           mainClass = 'my.app.Class'\n" +
      "           jvmArgs =   '-Xmx1g'\n" +
      "           moduleName = 'moduleName'\n" +
      "       }\n" +
      "       app2(Application) {\n" +
      "           mainClass = 'my.app.Class2'\n" +
      "           moduleName = 'moduleName'\n" +
      "       }\n" +
      "    }\n" +
      "  }\n" +
      "}")
    );

    final Map<String, Map<String, Object>> configs = testExtension.getConfigs();

    assertContain(new ArrayList<>(configs.keySet()), "app1", "app2");
    Map<String, Object> app1Settings = configs.get("app1");
    Map<String, Object> app2Settings = configs.get("app2");

    assertEquals("my.app.Class", app1Settings.get("mainClass"));
    assertEquals("my.app.Class2", app2Settings.get("mainClass"));
    assertEquals("-Xmx1g", app1Settings.get("jvmArgs"));
    assertNull(app2Settings.get("jvmArgs"));
  }


  @Test
  public void testDefaultRCSettingsImport() throws Exception {
    RunConfigurationImporter appcConfigImporter = new JavaApplicationRunConfigurationImporter();
    ExtensionPoint<RunConfigurationImporter> ep = Extensions.getRootArea().getExtensionPoint(RunConfigurationImporter.EP_NAME);
    ep.reset();
    ep.registerExtension(appcConfigImporter);

    importProject(
      withGradleIdeaExtPlugin(
        "import org.jetbrains.gradle.ext.*\n" +
        "idea {\n" +
        "  project.settings {\n" +
        "    runConfigurations {\n" +
        "       defaults(Application) {\n" +
        "           jvmArgs = '-DmyKey=myVal'\n" +
        "       }\n" +
        "    }\n" +
        "  }\n" +
        "}")
    );

    final RunManager runManager = RunManager.getInstance(myProject);
    final RunnerAndConfigurationSettings template = runManager.getConfigurationTemplate(appcConfigImporter.getConfigurationFactory());
    final String parameters = ((ApplicationConfiguration)template.getConfiguration()).getVMParameters();

    assertNotNull(parameters);
    assertTrue(parameters.contains("-DmyKey=myVal"));
  }

  @Test
  public void testDefaultsAreUsedDuringImport() throws Exception {
    RunConfigurationImporter appcConfigImporter = new JavaApplicationRunConfigurationImporter();
    ExtensionPoint<RunConfigurationImporter> ep = Extensions.getRootArea().getExtensionPoint(RunConfigurationImporter.EP_NAME);
    ep.reset();
    ep.registerExtension(appcConfigImporter);

    createSettingsFile("rootProject.name = 'moduleName'");
    importProject(
      withGradleIdeaExtPlugin(
        "import org.jetbrains.gradle.ext.*\n" +
        "idea {\n" +
        "  project.settings {\n" +
        "    runConfigurations {\n" +
        "       defaults(Application) {\n" +
        "           jvmArgs = '-DmyKey=myVal'\n" +
        "       }\n" +
        "       'My Run'(Application) {\n" +
        "           mainClass = 'my.app.Class'\n" +
        "           moduleName = 'moduleName'\n" +
        "       }\n" +
        "    }\n" +
        "  }\n" +
        "}")
    );

    final RunManager runManager = RunManager.getInstance(myProject);
    final RunnerAndConfigurationSettings template = runManager.getConfigurationTemplate(appcConfigImporter.getConfigurationFactory());
    final String parameters = ((ApplicationConfiguration)template.getConfiguration()).getVMParameters();

    assertNotNull(parameters);
    assertTrue(parameters.contains("-DmyKey=myVal"));

    final ApplicationConfiguration myRun = (ApplicationConfiguration)runManager.findConfigurationByName("My Run").getConfiguration();
    assertNotNull(myRun);
    final String actualParams = myRun.getVMParameters();
    assertNotNull(actualParams);
    assertTrue(actualParams.contains("-DmyKey=myVal"));
    assertEquals("my.app.Class", myRun.getMainClassName());
  }

  @Test
  public void testBeforeRunTaskImport() throws Exception {
    RunConfigurationImporter appcConfigImporter = new JavaApplicationRunConfigurationImporter();
    ExtensionPoint<RunConfigurationImporter> ep = Extensions.getRootArea().getExtensionPoint(RunConfigurationImporter.EP_NAME);
    ep.reset();
    ep.registerExtension(appcConfigImporter);

    createSettingsFile("rootProject.name = 'moduleName'");
    importProject(
      withGradleIdeaExtPlugin(
        "import org.jetbrains.gradle.ext.*\n" +
        "idea {\n" +
        "  project.settings {\n" +
        "    runConfigurations {\n" +
        "       'My Run'(Application) {\n" +
        "           mainClass = 'my.app.Class'\n" +
        "           moduleName = 'moduleName'\n" +
        "           beforeRun {\n" +
        "               gradle(GradleTask) { task = tasks['projects'] }\n" +
        "           }\n" +
        "       }\n" +
        "    }\n" +
        "  }\n" +
        "}")
    );

    final RunManagerEx runManager = RunManagerEx.getInstanceEx(myProject);
    final ApplicationConfiguration myRun = (ApplicationConfiguration)runManager.findConfigurationByName("My Run").getConfiguration();
    assertNotNull(myRun);

    final List<BeforeRunTask> tasks = runManager.getBeforeRunTasks(myRun);
    assertSize(2, tasks);
    final BeforeRunTask gradleBeforeRunTask = tasks.get(1);
    assertInstanceOf(gradleBeforeRunTask, ExternalSystemBeforeRunTask.class);
    final ExternalSystemTaskExecutionSettings settings = ((ExternalSystemBeforeRunTask)gradleBeforeRunTask).getTaskExecutionSettings();
    assertContain(settings.getTaskNames(), "projects");
    assertEquals(":", settings.getExternalProjectPath());
  }


  @Test
  public void testFacetSettingsImport() throws Exception {

    TestFacetConfigurationImporter testExtension = new TestFacetConfigurationImporter("spring");
    ExtensionPoint<FacetConfigurationImporter> ep = Extensions.getRootArea().getExtensionPoint(FacetConfigurationImporter.EP_NAME);
    ep.reset();
    ep.registerExtension(testExtension);

    importProject(
      withGradleIdeaExtPlugin(
        "import org.jetbrains.gradle.ext.*\n" +
      "idea {\n" +
      "  module.settings {\n" +
      "    facets {\n" +
      "       spring(SpringFacet) {\n" +
      "         contexts {\n" +
      "            myParent {\n" +
      "              file = 'parent_ctx.xml'\n" +
      "            }\n" +
      "            myChild {\n" +
      "              file = 'child_ctx.xml'\n" +
      "              parent = 'myParent'" +
      "            }\n" +
      "         }\n" +
      "       }\n" +
      "    }\n" +
      "  }\n" +
      "}")
    );

    final Map<String, Map<String, Object>> facetConfigs = testExtension.getConfigs();

    assertContain(new ArrayList<>(facetConfigs.keySet()), "spring");
    List<Map<String, Object>> springCtxConfigs = (List<Map<String, Object>>)facetConfigs.get("spring").get("contexts");

    assertContain(springCtxConfigs.stream().map((Map m) -> m.get("name")).collect(Collectors.toList()), "myParent", "myChild");

    Map<String, Object> parentSettings = springCtxConfigs.stream()
      .filter((Map m) -> m.get("name").equals("myParent"))
      .findFirst()
      .get();
    Map<String, Object> childSettings = springCtxConfigs.stream()
      .filter((Map m) -> m.get("name").equals("myChild"))
      .findFirst()
      .get();

    assertEquals("parent_ctx.xml", parentSettings.get("file"));
    assertEquals("child_ctx.xml", childSettings.get("file"));
    assertEquals("myParent", childSettings.get("parent"));
  }

  private String getGradlePluginPath() {
    return getClass().getResource("/testCompilerConfigurationSettingsImport/gradle-idea-ext.jar").toString();
  }

  @NotNull
  private String withGradleIdeaExtPlugin(@NonNls @Language("Groovy") String script) {
    return "buildscript {\n" +
           "  repositories {\n" +
           "    mavenLocal()\n" +
           "  }\n" +
           "  dependencies {\n" +
           "     classpath files('" + getGradlePluginPath() + "')\n" +
           "     classpath 'com.google.code.gson:gson:2+'\n" +
           "  }\n" +
           "}\n" +
           "apply plugin: 'org.jetbrains.gradle.plugin.idea-ext'\n" +
           script;
  }

}


class TestRunConfigurationImporter implements RunConfigurationImporter {

  private final String myTypeName;
  private final Map<String, Map<String, Object>> myConfigs = new HashMap<>();

  public TestRunConfigurationImporter(@NotNull String typeName) {
    myTypeName = typeName;
  }

  @Override
  public void process(@NotNull Project project, @NotNull RunConfiguration runConfig, @NotNull Map<String, Object> cfg,
                      @NotNull IdeModifiableModelsProvider modelsProvider) {
    myConfigs.put(runConfig.getName(), cfg);
  }

  @Override
  public boolean canImport(@NotNull String typeName) {
    return myTypeName.equals(typeName);
  }

  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return UnknownConfigurationType.FACTORY;
  }

  public Map<String, Map<String, Object>> getConfigs() {
    return myConfigs;
  }
}


class TestFacetConfigurationImporter implements FacetConfigurationImporter<Facet> {

  private final String myTypeName;

  private final Map<String, Map<String, Object>> myConfigs = new HashMap<>();

  TestFacetConfigurationImporter(@NotNull String typeName) {
    myTypeName = typeName;
  }

  @NotNull
  @Override
  public Collection<Facet> process(@NotNull Module module, @NotNull String name, @NotNull Map<String, Object> cfg, @NotNull FacetManager facetManager) {
    myConfigs.put(name, cfg);
    return Collections.emptySet();
  }

  @Override
  public boolean canHandle(@NotNull String typeName) {
    return myTypeName.equals(typeName);
  }

  public Map<String, Map<String, Object>> getConfigs() {
    return myConfigs;
  }
}
