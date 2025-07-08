// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.java.workspace.entities.JavaModuleSettingsKt;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.TestModuleProperties;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.jps.entities.ModuleId;
import com.intellij.pom.java.AcceptedLanguageLevelsSettings;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiJavaModule;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil;
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Vladislav.Soroka
 */
@SuppressWarnings("GrUnresolvedAccess") // ignore unresolved code for injected Groovy Gradle DSL
public class GradleMiscImportingTest extends GradleJavaImportingTestCase {

  /**
   * It's sufficient to run the test against one gradle version
   */
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  @Parameterized.Parameters(name = "with Gradle-{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{{BASE_GRADLE_VERSION}});
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    AcceptedLanguageLevelsSettings.allowLevel(
      getTestRootDisposable(),
      LanguageLevel.getEntries().get(LanguageLevel.HIGHEST.ordinal() + 1)
    );
  }

  @Test
  public void testTestModuleProperties() throws Exception {
    importProject(
      "apply plugin: 'java'"
    );

    assertModules("project", "project.main", "project.test");

    final Module testModule = getModule("project.test");
    TestModuleProperties testModuleProperties = TestModuleProperties.getInstance(testModule);
    assertEquals("project.main", testModuleProperties.getProductionModuleName());

    final Module productionModule = getModule("project.main");
    assertSame(productionModule, testModuleProperties.getProductionModule());
  }

  @Test
  public void testTestModulePropertiesForModuleWithHyphenInName() throws Exception {
    createSettingsFile("rootProject.name='my-project'");
    importProject(
      "apply plugin: 'java'"
    );

    assertModules("my-project", "my-project.main", "my-project.test");

    final Module testModule = getModule("my-project.test");
    TestModuleProperties testModuleProperties = TestModuleProperties.getInstance(testModule);
    assertEquals("my-project.main", testModuleProperties.getProductionModuleName());
  }

  @Test
  public void testInheritProjectJdkForModules() throws Exception {
    importProject(
      "apply plugin: 'java'"
    );

    assertModules("project", "project.main", "project.test");
    assertTrue(ModuleRootManager.getInstance(getModule("project")).isSdkInherited());
    assertTrue(ModuleRootManager.getInstance(getModule("project.main")).isSdkInherited());
    assertTrue(ModuleRootManager.getInstance(getModule("project.test")).isSdkInherited());
  }

  @Test
  public void testLanguageLevel() throws Exception {
    importProject(
      """
        apply plugin: 'java'
        java.sourceCompatibility = 1.5
        compileTestJava {
          sourceCompatibility = 1.8
        }
        """
    );

    assertModules("project", "project.main", "project.test");
    assertEquals(LanguageLevel.JDK_1_5, getLanguageLevelForModule("project"));
    assertEquals(LanguageLevel.JDK_1_5, getLanguageLevelForModule("project.main"));
    assertEquals(LanguageLevel.JDK_1_8, getLanguageLevelForModule("project.test"));
  }

  @Test
  public void testPreviewLanguageLevel() throws Exception {
    int feature = LanguageLevel.HIGHEST.feature();
    importProject(
      "apply plugin: 'java'\n" +
      "java.sourceCompatibility = " + feature+ "\n" +
      "apply plugin: 'java'\n" +
      "compileTestJava {\n" +
      "  sourceCompatibility = " + feature +"\n" +
      "  options.compilerArgs << '--enable-preview'" +
      "}\n"
    );

    assertModules("project", "project.main", "project.test");
    assertEquals(LanguageLevel.HIGHEST, getLanguageLevelForModule("project"));
    assertEquals(LanguageLevel.HIGHEST, getLanguageLevelForModule("project.main"));
    LanguageLevel highestPreview = LanguageLevel.getEntries().get(LanguageLevel.HIGHEST.ordinal() + 1);
    assertEquals(highestPreview, getLanguageLevelForModule("project.test"));
  }

  @Test
  public void testTargetLevel() throws Exception {
    importProject(
      """
        apply plugin: 'java'
        java.targetCompatibility = 1.8
        compileJava {
          targetCompatibility = 1.5
        }
        """
    );

    assertModules("project", "project.main", "project.test");
    assertEquals("1.5", getBytecodeTargetLevelForModule("project.main"));
    assertEquals("1.8", getBytecodeTargetLevelForModule("project.test"));

  }

  @Test
  public void testCompilerArguments() {
    createProjectConfig(script(it -> it
      .withJavaPlugin()
      .configureTask("compileTestJava", "JavaCompile", task -> {
        task.code("options.compilerArgs << '-param1' << '-param2'");
      })
    ));
    importProject();
    assertModules("project", "project.main", "project.test");
    assertProjectCompilerArgumentsVersion();
    assertModuleCompilerArgumentsVersion("project");
    assertModuleCompilerArgumentsVersion("project.main");
    assertModuleCompilerArgumentsVersion("project.test", "-param1", "-param2");

    createProjectConfig(script(it -> it
      .withJavaPlugin()
      .configureTask("compileJava", "JavaCompile", task -> {
        task.code("options.compilerArgs << '-param'");
      })
      .configureTask("compileTestJava", "JavaCompile", task -> {
        task.code("options.compilerArgs << '-param'");
      })
    ));
    importProject();
    assertModules("project", "project.main", "project.test");
    assertProjectCompilerArgumentsVersion("-param");
    assertModuleCompilerArgumentsVersion("project", "-param");
    assertModuleCompilerArgumentsVersion("project.main", "-param");
    assertModuleCompilerArgumentsVersion("project.test", "-param");
  }

  @Test
  public void testCompilerArgumentsProvider() {
    createProjectConfig(script(it -> it
      .withJavaPlugin()
      .addPrefix("""
                   class GStringArgumentProvider implements CommandLineArgumentProvider {
                       @Input
                       String value
                   
                       @Override
                       Iterable<String> asArguments() {
                           { return ["-DgString=${value}"] }
                       }
                   }
                   
                   class JavaStringArgumentProvider implements CommandLineArgumentProvider {
                       @Input
                       String value
                   
                       @Override
                       Iterable<String> asArguments() {
                           { return ["-Dstring=" + value] }
                       }
                   }
                   """)
      .configureTask("compileJava", "JavaCompile", task -> {
        task.code("options.compilerArgumentProviders.add(new GStringArgumentProvider(value: \"Str1\"))");
        task.code("options.compilerArgumentProviders.add(new JavaStringArgumentProvider(value: \"Str2\"))");
      })
    ));
    importProject();

    assertModuleCompilerArgumentsVersion("project.main", "-DgString=Str1", "-Dstring=Str2");
  }

  @Test
  public void testJdkName() throws Exception {
    Sdk myJdk = IdeaTestUtil.getMockJdk17("MyJDK");
    edt(() -> ApplicationManager.getApplication().runWriteAction(() -> ProjectJdkTable.getInstance().addJdk(myJdk, myProject)));
    importProject(
      """
        apply plugin: 'java'
        apply plugin: 'idea'
        idea {
          module {
            jdkName = 'MyJDK'
          }
        }
        """
    );

    assertModules("project", "project.main", "project.test");
    assertSame(getSdkForModule("project.main"), myJdk);
    assertSame(getSdkForModule("project.test"), myJdk);
  }

  @Test
  public void testUnloadedModuleImport() throws Exception {
    importProject(
      "apply plugin: 'java'"
    );
    assertModules("project", "project.main", "project.test");

    edt(() -> ModuleManager.getInstance(myProject).setUnloadedModulesSync(List.of("project", "project.main")));
    assertModules("project.test");

    importProject();
    assertModules("project.test");
  }

  @Test
  public void testESLinkedProjectIds() throws Exception {
    // Project configuration without an existing directory is not allowed
    createProjectSubDir("app");
    createProjectSubDir("util");

    createProjectSubDir("included-build/util");
    createProjectSubDir("included-build/buildSrc/util");

    createProjectSubDir("buildSrc/buildSrcSubProject");
    createProjectSubDir("buildSrc/util");

    // main build
    createSettingsFile("""
                         rootProject.name = 'multiproject'
                         include ':app'
                         include ':util'
                         includeBuild 'included-build'""");
    createProjectSubFile("build.gradle", "allprojects { apply plugin: 'java' }");

    // main buildSrc
    createProjectSubFile("buildSrc/settings.gradle", "include ':buildSrcSubProject'\n" +
                                                     "include ':util'");
    createProjectSubFile("buildSrc/build.gradle", "allprojects { apply plugin: 'java' }");

    // included build with buildSrc
    createProjectSubFile("included-build/settings.gradle", "rootProject.name = 'inc-build'\n" +
                                                           "include ':util'");
    createProjectSubFile("included-build/buildSrc/settings.gradle", "include ':util'");

    importProject();
    assertModules(
      "multiproject",
      "multiproject.main",
      "multiproject.test",

      "multiproject.buildSrc",
      "multiproject.buildSrc.main",
      "multiproject.buildSrc.test",

      "multiproject.buildSrc.buildSrcSubProject",
      "multiproject.buildSrc.buildSrcSubProject.main",
      "multiproject.buildSrc.buildSrcSubProject.test",

      "multiproject.buildSrc.util",
      "multiproject.buildSrc.util.main",
      "multiproject.buildSrc.util.test",

      "multiproject.app",
      "multiproject.app.main",
      "multiproject.app.test",

      "multiproject.util",
      "multiproject.util.main",
      "multiproject.util.test",

      "inc-build",
      "inc-build.util",

      "inc-build.buildSrc",
      "inc-build.buildSrc.util",
      "inc-build.buildSrc.main",
      "inc-build.buildSrc.test"
    );

    assertExternalProjectId("multiproject", "multiproject");
    assertExternalProjectId("multiproject.main", "multiproject:main");
    assertExternalProjectId("multiproject.test", "multiproject:test");

    assertExternalProjectId("multiproject.buildSrc", ":buildSrc");
    assertExternalProjectId("multiproject.buildSrc.main", ":buildSrc:main");
    assertExternalProjectId("multiproject.buildSrc.test", ":buildSrc:test");

    assertExternalProjectId("multiproject.buildSrc.buildSrcSubProject", ":buildSrc:buildSrcSubProject");
    assertExternalProjectId("multiproject.buildSrc.buildSrcSubProject.main", ":buildSrc:buildSrcSubProject:main");
    assertExternalProjectId("multiproject.buildSrc.buildSrcSubProject.test", ":buildSrc:buildSrcSubProject:test");

    assertExternalProjectId("multiproject.buildSrc.util", ":buildSrc:util");
    assertExternalProjectId("multiproject.buildSrc.util.main", ":buildSrc:util:main");
    assertExternalProjectId("multiproject.buildSrc.util.test", ":buildSrc:util:test");

    assertExternalProjectId("multiproject.app", ":app");
    assertExternalProjectId("multiproject.app.main", ":app:main");
    assertExternalProjectId("multiproject.app.test", ":app:test");

    assertExternalProjectId("multiproject.util", ":util");
    assertExternalProjectId("multiproject.util.main", ":util:main");
    assertExternalProjectId("multiproject.util.test", ":util:test");

    assertExternalProjectId("inc-build", ":included-build");
    assertExternalProjectId("inc-build.util", ":included-build:util");

    assertExternalProjectId("inc-build.buildSrc", ":included-build:buildSrc");
    assertExternalProjectId("inc-build.buildSrc.util", ":included-build:buildSrc:util");
    assertExternalProjectId("inc-build.buildSrc.main", ":included-build:buildSrc:main");
    assertExternalProjectId("inc-build.buildSrc.test", ":included-build:buildSrc:test");

    Map<String, ExternalProject> projectMap = getExternalProjectsMap();
    assertExternalProjectIds(projectMap, "multiproject", "multiproject:main", "multiproject:test");
    assertExternalProjectIds(projectMap, ":app", ":app:main", ":app:test");
    assertExternalProjectIds(projectMap, ":util", ":util:main", ":util:test");
    assertExternalProjectIds(projectMap, ":included-build", ArrayUtilRt.EMPTY_STRING_ARRAY);
    assertExternalProjectIds(projectMap, ":included-build:util", ArrayUtilRt.EMPTY_STRING_ARRAY);

    // Note, currently ExternalProject models are not exposed for "buildSrc" projects
  }

  @Test
  public void testSourceSetModuleNamesForDeduplicatedMainModule() throws Exception {
    IdeModifiableModelsProvider modelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(myProject);
    modelsProvider.newModule(getProjectPath() + "/app.iml", JavaModuleType.getModuleType().getId());
    modelsProvider.newModule(getProjectPath() + "/my_group.app.main.iml", JavaModuleType.getModuleType().getId());
    edt(() -> ApplicationManager.getApplication().runWriteAction(modelsProvider::commit));

    createSettingsFile("rootProject.name = 'app'");
    importProject("apply plugin: 'java'\n" +
                  "group = 'my_group'");

    assertModules("app", "my_group.app.main",
                  "my_group.app", "my_group.app.main~1", "my_group.app.test");

    assertNull(ExternalSystemApiUtil.getExternalProjectPath(getModule("app")));
    assertNull(ExternalSystemApiUtil.getExternalProjectPath(getModule("my_group.app.main")));
    assertEquals(getProjectPath(), ExternalSystemApiUtil.getExternalProjectPath(getModule("my_group.app")));
    assertEquals(getProjectPath(), ExternalSystemApiUtil.getExternalProjectPath(getModule("my_group.app.main~1")));
    assertEquals(getProjectPath(), ExternalSystemApiUtil.getExternalProjectPath(getModule("my_group.app.test")));
  }

  @Test
  public void testImportingTasksWithSpaces() throws IOException {
    importProject("project.tasks.create('descriptive task name') {}");
    ExternalProjectInfo projectData =
      ProjectDataManager.getInstance().getExternalProjectData(myProject, GradleConstants.SYSTEM_ID, getProjectPath());
    DataNode<ModuleData> moduleNode = ExternalSystemApiUtil.find(projectData.getExternalProjectStructure(), ProjectKeys.MODULE);
    Collection<DataNode<TaskData>> tasksNodes = ExternalSystemApiUtil.findAll(moduleNode, ProjectKeys.TASK);
    List<String> taskNames = ContainerUtil.map(tasksNodes, node -> node.getData().getName());
    assertThat(taskNames).containsOnlyOnce("\"descriptive task name\"");
  }

  @Test
  public void testImportProjectWithExistingFakeModule() throws IOException {
    // After first opening of the project, IJ creates a fake module at the project root
    edt(() -> {
      ApplicationManager.getApplication().runWriteAction(() -> {
        Module module = ModuleManager.getInstance(myProject).newModule(
          getProjectPath() + "/" + "project" + ModuleFileType.DOT_DEFAULT_EXTENSION, JavaModuleType.getModuleType().getId());
        ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
        modifiableModel.addContentEntry(myProjectRoot);
        modifiableModel.inheritSdk();
        modifiableModel.commit();
      });
    });

    Module module = ModuleManager.getInstance(myProject).findModuleByName("project");
    assertFalse(ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module));

    importProject("");

    Module moduleAfter = ModuleManager.getInstance(myProject).findModuleByName("project");
    assertTrue(ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, moduleAfter));
  }


  @Test
  public void testProjectLibraryCoordinatesAreSet() throws Exception {
    importProject(createBuildScriptBuilder()
                    .withJavaPlugin()
                    .withMavenCentral()
                    .addImplementationDependency("junit:junit:4.0")
                    .generate());

    assertProjectLibraryCoordinates("Gradle: junit:junit:4.0",
                                    "junit", "junit", "4.0");
  }

  @Test
  public void testJarManifestAutomaticModuleName() throws Exception {
    importProject(
      """
        apply plugin: 'java'
        tasks.named('jar') {
          manifest {
            attributes('Automatic-Module-Name': 'my.module.name')
          }
        }"""
    );

    var moduleEntity = WorkspaceModel.getInstance(myProject).getCurrentSnapshot().resolve(new ModuleId("project.main"));
    var javaSettings = JavaModuleSettingsKt.getJavaSettings(moduleEntity);
    var automaticModuleName = javaSettings.getManifestAttributes().get(PsiJavaModule.AUTO_MODULE_NAME);
    assertEquals("my.module.name", automaticModuleName);
  }

  private static void assertExternalProjectIds(Map<String, ExternalProject> projectMap, String projectId, String... sourceSetModulesIds) {
    ExternalProject externalProject = projectMap.get(projectId);
    assertEquals(projectId, externalProject.getId());
    List<String> actualSourceSetModulesIds = ContainerUtil.map(
      externalProject.getSourceSets().values(), sourceSet -> GradleProjectResolverUtil.getModuleId(externalProject, sourceSet));
    assertSameElements(actualSourceSetModulesIds, sourceSetModulesIds);
  }

  @NotNull
  private Map<String, ExternalProject> getExternalProjectsMap() {
    ExternalProject rootExternalProject = ExternalProjectDataCache.getInstance(myProject).getRootExternalProject(getProjectPath());
    final Map<String, ExternalProject> externalProjectMap = new HashMap<>();
    if (rootExternalProject == null) return externalProjectMap;
    ArrayDeque<ExternalProject> queue = new ArrayDeque<>();
    queue.add(rootExternalProject);
    ExternalProject externalProject;
    while ((externalProject = queue.pollFirst()) != null) {
      queue.addAll(externalProject.getChildProjects().values());
      externalProjectMap.put(externalProject.getId(), externalProject);
    }
    return externalProjectMap;
  }

  private void assertExternalProjectId(String moduleName, String expectedId) {
    assertEquals(expectedId, ExternalSystemApiUtil.getExternalProjectId(getModule(moduleName)));
  }
}
