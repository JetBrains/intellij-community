// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
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
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemTaskActivator;
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
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.settings.GradleSystemRunningSettings;
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
    return Arrays.asList(new Object[][]{{BASE_GRADLE_VERSION}});
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

  @Test
  public void testTaskTriggersImport() throws Exception {
    importProject(
      withGradleIdeaExtPlugin(
        "import org.jetbrains.gradle.ext.*\n" +
        "idea {\n" +
        "  project.settings {\n" +
        "    taskTriggers {\n" +
        "      beforeSync tasks.getByName('projects'), tasks.getByName('tasks')\n" +
        "    }\n" +
        "  }\n" +
        "}")
    );

    final List<ExternalProjectsManagerImpl.ExternalProjectsStateProvider.TasksActivation> activations =
      ExternalProjectsManagerImpl.getInstance(myProject).getStateProvider().getAllTasksActivation();

    assertSize(1, activations);

    final ExternalProjectsManagerImpl.ExternalProjectsStateProvider.TasksActivation activation = activations.get(0);
    assertEquals(GradleSettings.getInstance(myProject).getLinkedProjectsSettings().iterator().next().getExternalProjectPath(),
                 activation.projectPath);
    final List<String> beforeSyncTasks = activation.state.getTasks(ExternalSystemTaskActivator.Phase.BEFORE_SYNC);

    assertContain(beforeSyncTasks, ":projects", ":tasks");
  }

  @Test
  public void testActionDelegationImport() throws Exception {
    importProject(
      withGradleIdeaExtPlugin(
        "import org.jetbrains.gradle.ext.*\n" +
        "import static org.jetbrains.gradle.ext.ActionDelegationConfig.TestRunner.*\n" +
        "idea {\n" +
        "  project.settings {\n" +
        "    delegateActions {\n" +
        "      delegateBuildRunToGradle = true\n" +
        "      testRunner = CHOOSE_PER_TEST\n" +
        "    }\n" +
        "  }\n" +
        "}")
    );

    GradleSystemRunningSettings settings = GradleSystemRunningSettings.getInstance();

    assertTrue(settings.isUseGradleAwareMake());
    assertEquals(GradleSystemRunningSettings.PreferredTestRunner.CHOOSE_PER_TEST, settings.getPreferredTestRunner());
  }

  private String getGradlePluginPath() {
    return getClass().getResource("/testCompilerConfigurationSettingsImport/gradle-idea-ext.jar").toString();
  }

  @NotNull
  protected String withGradleIdeaExtPlugin(@NonNls @Language("Groovy") String script) {
    return "buildscript {\n" +
           "  repositories {\n" +
           "    mavenCentral()\n" +
           "    mavenLocal()\n" +
           "  }\n" +
           "  dependencies {\n" +
           "     classpath files('" + getGradlePluginPath() + "')\n" +
           "     classpath 'com.google.code.gson:gson:2+'\n" +
           "     classpath 'com.google.guava:guava:25.1-jre'\n" +
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
    return UnknownConfigurationType.getFactory();
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
