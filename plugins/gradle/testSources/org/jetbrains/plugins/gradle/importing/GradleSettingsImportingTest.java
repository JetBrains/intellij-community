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
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemBeforeRunTask;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemTaskActivator;
import com.intellij.openapi.externalSystem.service.project.manage.SourceFolderManager;
import com.intellij.openapi.externalSystem.service.project.manage.SourceFolderManagerImpl;
import com.intellij.openapi.externalSystem.service.project.settings.FacetConfigurationImporter;
import com.intellij.openapi.externalSystem.service.project.settings.RunConfigurationImporter;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.settings.GradleSettingsService;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.settings.TestRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.openapi.externalSystem.service.project.settings.ConfigurationDataService.EXTERNAL_SYSTEM_CONFIGURATION_IMPORT_ENABLED;

/**
 * Created by Nikita.Skvortsov
 * date: 18.09.2017.
 */
public class GradleSettingsImportingTest extends GradleImportingTestCase {

  public static final String IDEA_EXT_PLUGIN_VERSION = "0.5";

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
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
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
        "      myInspection { enabled = false }\n" +
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
    assertEquals(FileUtil.toSystemIndependentName(getProjectPath()),
                 FileUtil.toSystemIndependentName(settings.getExternalProjectPath()));
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

    if (extPluginVersionIsAtLeast("0.5")) {
      assertContain(beforeSyncTasks, "projects", "tasks");
    } else {
      assertContain(beforeSyncTasks, ":projects", ":tasks");
    }
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

    GradleSettingsService settingsService = GradleSettingsService.getInstance(myProject);
    String projectPath = getCurrentExternalProjectSettings().getExternalProjectPath();
    assertTrue(settingsService.isDelegatedBuildEnabled(projectPath));
    assertEquals(TestRunner.CHOOSE_PER_TEST, settingsService.getTestRunner(projectPath));
  }

  @Test
  public void testSavePackagePrefixAfterReOpenProject() throws IOException {
    @Language("Groovy") String buildScript = new GradleBuildScriptBuilderEx().withJavaPlugin().generate();
    createProjectSubFile("src/main/java/Main.java", "");
    importProject(buildScript);
    Application application = ApplicationManager.getApplication();
    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(myProject);
    try {
      Module module = modelsProvider.findIdeModule("project.main");
      ModifiableRootModel modifiableRootModel = modelsProvider.getModifiableRootModel(module);
      SourceFolder sourceFolder = findSource(modifiableRootModel, "src/main/java");
      sourceFolder.setPackagePrefix("prefix.package.some");
      application.invokeAndWait(() -> application.runWriteAction(() -> modelsProvider.commit()));
    }
    finally {
      application.invokeAndWait(() -> modelsProvider.dispose());
    }
    assertSourcePackagePrefix("project.main", "src/main/java", "prefix.package.some");
    importProject(buildScript);
    assertSourcePackagePrefix("project.main", "src/main/java", "prefix.package.some");
  }

  @Test
  public void testRemovingSourceFolderManagerMemLeaking() throws IOException {
    SourceFolderManagerImpl sourceFolderManager = (SourceFolderManagerImpl)SourceFolderManager.getInstance(myProject);
    String javaSourcePath = FileUtil.toCanonicalPath(myProjectRoot.getPath() + "/java");
    String javaSourceUrl = VfsUtilCore.pathToUrl(javaSourcePath);
    {
      importProject(
        new GradleBuildScriptBuilderEx()
          .withJavaPlugin()
          .addPostfix("sourceSets {")
          .addPostfix("  main.java.srcDirs += 'java'")
          .addPostfix("}")
          .generate());
      Set<String> sourceFolders = sourceFolderManager.getSourceFolders("project.main");
      assertTrue(sourceFolders.contains(javaSourceUrl));
    }
    {
      importProject(
        new GradleBuildScriptBuilderEx()
          .withJavaPlugin()
          .generate());
      Set<String> sourceFolders = sourceFolderManager.getSourceFolders("project.main");
      assertFalse(sourceFolders.contains(javaSourceUrl));
    }
  }

  @Test
  public void testSourceFolderIsDisposedAfterProjectDisposing() throws IOException {
    importProject(new GradleBuildScriptBuilder().generate());
    Application application = ApplicationManager.getApplication();
    Ref<Project> projectRef = new Ref<>();
    application.invokeAndWait(() -> projectRef.set(ProjectUtil.openOrImport(myProjectRoot.getPath(), null, false)));
    Project project = projectRef.get();
    SourceFolderManagerImpl sourceFolderManager = (SourceFolderManagerImpl)SourceFolderManager.getInstance(project);
    try {
      assertFalse(project.isDisposed());
      assertFalse(sourceFolderManager.isDisposed());
    }
    finally {
      application.invokeAndWait(() -> ProjectUtil.closeAndDispose(project));
    }
    assertTrue(project.isDisposed());
    assertTrue(sourceFolderManager.isDisposed());
  }

  @Test
  public void testPostponedImportPackagePrefix() throws IOException {
    createProjectSubFile("src/main/java/Main.java", "");
    importProject(
      new GradleBuildScriptBuilderEx()
        .withGradleIdeaExtPlugin(IDEA_EXT_PLUGIN_VERSION)
        .withJavaPlugin()
        .withKotlinPlugin("1.3.0")
        .addPostfix("idea {")
        .addPostfix("  module {")
        .addPostfix("    settings {")
        .addPostfix("      packagePrefix['src/main/java'] = 'prefix.package.some'")
        .addPostfix("      packagePrefix['src/main/kotlin'] = 'prefix.package.other'")
        .addPostfix("      packagePrefix['src/test/java'] = 'prefix.package.some.test'")
        .addPostfix("    }")
        .addPostfix("  }")
        .addPostfix("}")
        .generate());
    assertSourcePackagePrefix("project.main", "src/main/java", "prefix.package.some");
    assertSourceNotExists("project.main", "src/main/kotlin");
    assertSourceNotExists("project.test", "src/test/java");
    createProjectSubFile("src/main/kotlin/Main.kt", "");
    assertSourcePackagePrefix("project.main", "src/main/java", "prefix.package.some");
    assertSourcePackagePrefix("project.main", "src/main/kotlin", "prefix.package.other");
    assertSourceNotExists("project.test", "src/test/java");
  }

  @Test
  public void testPartialImportPackagePrefix() throws IOException {
    createProjectSubFile("src/main/java/Main.java", "");
    createProjectSubFile("src/main/kotlin/Main.kt", "");
    importProject(
      new GradleBuildScriptBuilderEx()
        .withGradleIdeaExtPlugin(IDEA_EXT_PLUGIN_VERSION)
        .withJavaPlugin()
        .withKotlinPlugin("1.3.0")
        .addPostfix("idea {")
        .addPostfix("  module {")
        .addPostfix("    settings {")
        .addPostfix("      packagePrefix['src/main/java'] = 'prefix.package.some'")
        .addPostfix("    }")
        .addPostfix("  }")
        .addPostfix("}")
        .generate());
    assertSourcePackagePrefix("project.main", "src/main/java", "prefix.package.some");
    assertSourcePackagePrefix("project.main", "src/main/kotlin", "");
  }

  @Test
  public void testImportPackagePrefixWithRemoteSourceRoot() throws IOException {
    createProjectSubFile("src/test/java/Main.java", "");
    createProjectSubFile("../subproject/src/test/java/Main.java", "");
    importProject(
      new GradleBuildScriptBuilderEx()
        .withGradleIdeaExtPlugin(IDEA_EXT_PLUGIN_VERSION)
        .withJavaPlugin()
        .addPostfix("sourceSets {")
        .addPostfix("  test.java.srcDirs += '../subproject/src/test/java'")
        .addPostfix("}")
        .addPostfix("idea {")
        .addPostfix("  module {")
        .addPostfix("    settings {")
        .addPostfix("      packagePrefix['src/test/java'] = 'prefix.package.some'")
        .addPostfix("      packagePrefix['../subproject/src/test/java'] = 'prefix.package.other'")
        .addPostfix("    }")
        .addPostfix("  }")
        .addPostfix("}")
        .generate());
    printProjectStructure();
    assertSourcePackagePrefix("project.test", "src/test/java", "prefix.package.some");
    assertSourcePackagePrefix("project.test", "../subproject/src/test/java", "prefix.package.other");
  }

  @Test
  public void testImportPackagePrefix() throws IOException {
    createProjectSubFile("src/main/java/Main.java", "");
    importProject(
      new GradleBuildScriptBuilderEx()
        .withGradleIdeaExtPlugin(IDEA_EXT_PLUGIN_VERSION)
        .withJavaPlugin()
        .addPostfix("idea {")
        .addPostfix("  module {")
        .addPostfix("    settings {")
        .addPostfix("      packagePrefix['src/main/java'] = 'prefix.package.some'")
        .addPostfix("    }")
        .addPostfix("  }")
        .addPostfix("}")
        .generate());
    assertSourcePackagePrefix("project.main", "src/main/java", "prefix.package.some");
  }

  @Test
  public void testChangeImportPackagePrefix() throws IOException {
    createProjectSubFile("src/main/java/Main.java", "");
    importProject(
      new GradleBuildScriptBuilderEx()
        .withGradleIdeaExtPlugin(IDEA_EXT_PLUGIN_VERSION)
        .withJavaPlugin()
        .addPostfix("idea {")
        .addPostfix("  module {")
        .addPostfix("    settings {")
        .addPostfix("      packagePrefix['src/main/java'] = 'prefix.package.some'")
        .addPostfix("    }")
        .addPostfix("  }")
        .addPostfix("}")
        .generate());
    assertSourcePackagePrefix("project.main", "src/main/java", "prefix.package.some");
    importProject(
      new GradleBuildScriptBuilderEx()
        .withGradleIdeaExtPlugin(IDEA_EXT_PLUGIN_VERSION)
        .withJavaPlugin()
        .addPostfix("idea {")
        .addPostfix("  module {")
        .addPostfix("    settings {")
        .addPostfix("      packagePrefix['src/main/java'] = 'prefix.package.other'")
        .addPostfix("    }")
        .addPostfix("  }")
        .addPostfix("}")
        .generate());
    assertSourcePackagePrefix("project.main", "src/main/java", "prefix.package.other");
  }

  /**
   * This method needed for printing debug information about project
   */
  @SuppressWarnings("unused")
  protected void printProjectStructure() {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (Module module : moduleManager.getModules()) {
      System.out.println(module);
      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      for (ContentEntry contentEntry : moduleRootManager.getContentEntries()) {
        System.out.println("content root = " + contentEntry.getUrl());
        for (SourceFolder sourceFolder : contentEntry.getSourceFolders()) {
          System.out.println("source root = " + sourceFolder);
          String packagePrefix = sourceFolder.getPackagePrefix();
          if (packagePrefix.isEmpty()) continue;
          System.out.println("package prefix = " + packagePrefix);
        }
      }
    }
  }

  protected boolean extPluginVersionIsAtLeast(@NotNull final String version) {
    return Version.parseVersion(IDEA_EXT_PLUGIN_VERSION).compareTo(Version.parseVersion(version)) >= 0;
  }

  @NotNull
  @Override
  protected String injectRepo(String config) {
    return config; // Do not inject anything
  }

  @NotNull
  protected String withGradleIdeaExtPlugin(@NonNls @Language("Groovy") String script) {
    return
      "plugins {\n" +
      "  id \"org.jetbrains.gradle.plugin.idea-ext\" version \"" + IDEA_EXT_PLUGIN_VERSION + "\"\n" +
      "}\n" +
      script;
  }

  protected void assertSourceNotExists(@NotNull String moduleName, @NotNull String sourcePath) {
    SourceFolder sourceFolder = findSource(moduleName, sourcePath);
    assertNull("Source folder " + sourcePath + " found in module " + moduleName + "but shouldn't", sourceFolder);
  }

  protected void assertSourcePackagePrefix(@NotNull String moduleName, @NotNull String sourcePath, @NotNull String packagePrefix) {
    SourceFolder sourceFolder = findSource(moduleName, sourcePath);
    assertNotNull("Source folder " + sourcePath + " not found in module " + moduleName, sourceFolder);
    assertEquals(packagePrefix, sourceFolder.getPackagePrefix());
  }

  @Nullable
  protected SourceFolder findSource(@NotNull String moduleName, @NotNull String sourcePath) {
    return findSource(getRootManager(moduleName), sourcePath);
  }

  @Nullable
  protected SourceFolder findSource(@NotNull ModuleRootModel moduleRootManager, @NotNull String sourcePath) {
    ContentEntry[] contentRoots = moduleRootManager.getContentEntries();
    Module module = moduleRootManager.getModule();
    String rootUrl = getAbsolutePath(ExternalSystemApiUtil.getExternalProjectPath(module));
    for (ContentEntry contentRoot : contentRoots) {
      for (SourceFolder f : contentRoot.getSourceFolders()) {
        String folderPath = getAbsolutePath(f.getUrl());
        String rootPath = getAbsolutePath(rootUrl + "/" + sourcePath);
        if (folderPath.equals(rootPath)) return f;
      }
    }
    return null;
  }
}


class TestRunConfigurationImporter implements RunConfigurationImporter {

  private final String myTypeName;
  private final Map<String, Map<String, Object>> myConfigs = new HashMap<>();

  TestRunConfigurationImporter(@NotNull String typeName) {
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
    return UnknownConfigurationType.getInstance();
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
