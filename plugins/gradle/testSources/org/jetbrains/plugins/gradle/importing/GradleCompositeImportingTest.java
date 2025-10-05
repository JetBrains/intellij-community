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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTrackerSettings;
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.data.BuildParticipant;
import org.jetbrains.plugins.gradle.settings.CompositeDefinitionSource;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;
import org.jetbrains.plugins.gradle.util.GradleModuleDataKt;
import org.jetbrains.plugins.gradle.util.GradleUtil;
import org.junit.Ignore;
import org.junit.Test;

import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.util.io.NioFiles.copyRecursively;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Vladislav.Soroka
 */
public class GradleCompositeImportingTest extends GradleImportingTestCase {

  @Test
  public void testBasicCompositeBuild() throws Exception {
    //enableGradleDebugWithSuspend();
    createSettingsFile(settingsScript(it -> it
      .setProjectName("adhoc")
      .includeBuild("../my-app")
      .includeBuild("../my-utils")
    ));

    createProjectSubFile("../my-app/settings.gradle", settingsScript(it -> it
      .setProjectName("my-app-name")
    ));
    createProjectSubFile("../my-app/build.gradle", script(it -> it
      .addGroup("org.sample")
      .addVersion("1.0")
      .withJavaPlugin()
      .addImplementationDependency("org.sample:number-utils:1.0")
      .addImplementationDependency("org.sample:string-utils:1.0")
    ));

    createProjectSubFile("../my-utils/settings.gradle", settingsScript(it -> it
      .setProjectName("my-utils")
      .include("number-utils", "string-utils")
    ));
    createProjectSubFile("../my-utils/number-utils/build.gradle", script(it -> it
      .addGroup("org.sample")
      .addVersion("1.0")
      .withJavaPlugin()
    ));
    createProjectSubFile("../my-utils/string-utils/build.gradle", script(it -> it
      .addGroup("org.sample")
      .addVersion("1.0")
      .withJavaPlugin()
      .withMavenCentral()
      .withJavaLibraryPlugin()
      .addApiDependency("org.apache.commons:commons-lang3:3.4")
    ));

    importProject();

    assertModules("adhoc",
                  "my-app-name", "my-app-name.main", "my-app-name.test",
                  "my-utils",
                  "my-utils.string-utils", "my-utils.string-utils.test", "my-utils.string-utils.main",
                  "my-utils.number-utils", "my-utils.number-utils.main", "my-utils.number-utils.test");

    String[] rootModules = new String[]{"adhoc", "my-app-name", "my-utils", "my-utils.string-utils", "my-utils.number-utils"};
    for (String rootModule : rootModules) {
      assertModuleLibDeps(rootModule);
      assertModuleModuleDeps(rootModule);
    }
    assertModuleModuleDeps("my-app-name.main", "my-utils.number-utils.main", "my-utils.string-utils.main");
    assertModuleModuleDepScope("my-app-name.main", "my-utils.number-utils.main", COMPILE);
    assertModuleModuleDepScope("my-app-name.main", "my-utils.string-utils.main", COMPILE);
    assertModuleLibDepScope("my-app-name.main", "Gradle: org.apache.commons:commons-lang3:3.4", COMPILE);

    assertTasksProjectPath("adhoc", getProjectPath());
    if (isGradleAtLeast("6.8")) {
      /* Has to be :my-app: as this is the name of the included build (rootProject.name) is not used for path construction */
      assertTasksProjectPath("my-app-name", getProjectPath(), ":my-app:");
      assertTasksProjectPath("my-utils", getProjectPath(), ":my-utils:");
    } else {
      assertTasksProjectPath("my-app-name", path("../my-app"));
      assertTasksProjectPath("my-utils", path("../my-utils"));
    }
  }

  @Test
  @TargetVersions("6.0+")
  public void testIncludedBuildWithBuildSrc() throws Exception {
    createSettingsFile("""
                         rootProject.name='adhoc'

                         includeBuild 'my-app'
                         """);

    createProjectSubFile("my-app/settings.gradle", "rootProject.name = 'my-app'\n");
    createProjectSubFile("my-app/build.gradle",
                         createBuildScriptBuilder()
                           .generate());

    createProjectSubFile("buildSrc/build.gradle",
                         createBuildScriptBuilder()
                           .generate());

    importProject();

    DataNode<ModuleData> myAppData = GradleUtil.findGradleModuleData(getModule("my-app"));

    assertFalse(GradleModuleDataKt.isBuildSrcModule(myAppData.getData()));
    assertTrue(GradleModuleDataKt.isIncludedBuild(myAppData.getData()));

    DataNode<ModuleData> buildSrcData = GradleUtil.findGradleModuleData(getModule("adhoc.buildSrc"));
    assertTrue(GradleModuleDataKt.isBuildSrcModule(buildSrcData.getData()));
  }

  @Test
  public void testCompositeBuildWithNestedModules() throws Exception {
    createSettingsFile("rootProject.name = 'app'\n" +
                       "includeBuild 'lib'");

    createProjectSubFile("lib/settings.gradle", """
      rootProject.name = 'lib'
      include 'runtime'
      include 'runtime:runtime-mod'""");
    createProjectSubFile("lib/runtime/runtime-mod/build.gradle",
                         "apply plugin: 'java'\n" +
                         "group = 'my.group'");

    importProject(createBuildScriptBuilder()
                    .withJavaPlugin()
                    .addImplementationDependency("my.group:runtime-mod")
                    .generate());

    assertModules("app", "app.main", "app.test",
                  "lib",
                  "lib.runtime",
                  "lib.runtime.runtime-mod", "lib.runtime.runtime-mod.main", "lib.runtime.runtime-mod.test");

    assertModuleModuleDepScope("app.main", "lib.runtime.runtime-mod.main", COMPILE);

    assertTasksProjectPath("app", getProjectPath());
    if (isGradleAtLeast("6.8")) {
      assertTasksProjectPath("lib", getProjectPath(), ":lib:");
    }
    else {
      assertTasksProjectPath("lib", path("lib"));
    }
  }


  @Test
  public void testCompositeBuildWithNestedModulesSingleModulePerProject() throws Exception {
    createSettingsFile("rootProject.name = 'app'\n" +
                       "includeBuild 'lib'");

    createProjectSubFile("lib/settings.gradle", """
      rootProject.name = 'lib'
      include 'runtime'
      include 'runtime:runtime-mod'""");
    createProjectSubFile("lib/runtime/runtime-mod/build.gradle",
                         "apply plugin: 'java'\n" +
                         "group = 'my.group'");

    importProjectUsingSingeModulePerGradleProject(createBuildScriptBuilder()
                                                    .withJavaPlugin()
                                                    .addImplementationDependency("my.group:runtime-mod")
                                                    .generate());

    assertModules("app",
                  "lib",
                  "lib.runtime",
                  "lib.runtime.runtime-mod");

    assertMergedModuleCompileModuleDepScope("app", "lib.runtime.runtime-mod");
  }


  @Test
  public void testCompositeBuildWithGradleProjectDuplicates() throws Exception {
    createSettingsFile("""
                         rootProject.name = 'app'
                         include 'runtime'
                         includeBuild 'lib1'
                         includeBuild 'lib2'""");

    createProjectSubFile("runtime/build.gradle",
                         "apply plugin: 'java'");


    createProjectSubFile("lib1/settings.gradle", "rootProject.name = 'lib1'\n" +
                                                 "include 'runtime'");
    createProjectSubFile("lib1/runtime/build.gradle",
                         "apply plugin: 'java'\n" +
                         "group = 'my.group.lib_1'");


    createProjectSubFile("lib2/settings.gradle", "rootProject.name = 'lib2'\n" +
                                                 "include 'runtime'");
    createProjectSubFile("lib2/runtime/build.gradle",
                         "apply plugin: 'java'\n" +
                         "group = 'my.group.lib_2'");

    importProjectUsingSingeModulePerGradleProject(script(it -> {
      it.withJavaPlugin()
        .addImplementationDependency(it.project(":runtime"))
        .addImplementationDependency("my.group.lib_1:runtime")
        .addImplementationDependency("my.group.lib_2:runtime");
    }));

    assertModules("app", "app.runtime",
                  "lib1", "lib1.runtime",
                  "lib2", "lib2.runtime");

    assertMergedModuleCompileModuleDepScope("app", "app.runtime");
    assertMergedModuleCompileModuleDepScope("app", "lib1.runtime");
    assertMergedModuleCompileModuleDepScope("app", "lib2.runtime");
  }


  @Test
  public void testCompositeBuildWithGradleProjectDuplicatesModulePerSourceSet() throws Exception {
    createSettingsFile("""
                         rootProject.name = 'app'
                         include 'runtime'
                         includeBuild 'lib1'
                         includeBuild 'lib2'""");

    createProjectSubFile("runtime/build.gradle",
                         "apply plugin: 'java'");


    createProjectSubFile("lib1/settings.gradle", "rootProject.name = 'lib1'\n" +
                                                 "include 'runtime'");
    createProjectSubFile("lib1/runtime/build.gradle",
                         "apply plugin: 'java'\n" +
                         "group = 'my.group.lib_1'");


    createProjectSubFile("lib2/settings.gradle", "rootProject.name = 'lib2'\n" +
                                                 "include 'runtime'");
    createProjectSubFile("lib2/runtime/build.gradle",
                         "apply plugin: 'java'\n" +
                         "group = 'my.group.lib_2'");

    // check for non-qualified module names
    getCurrentExternalProjectSettings().setUseQualifiedModuleNames(false);
    importProject(script(it -> {
      it.withJavaPlugin()
        .addImplementationDependency(it.project(":runtime"))
        .addImplementationDependency("my.group.lib_1:runtime")
        .addImplementationDependency("my.group.lib_2:runtime");
    }));

    assertModules("app", "app_main", "app_test",
                  "app-runtime", "app-runtime_main", "app-runtime_test",
                  "lib1", "lib1-runtime", "lib1-runtime_main", "lib1-runtime_test",
                  "lib2", "lib2-runtime", "lib2-runtime_main", "lib2-runtime_test");

    assertModuleModuleDepScope("app_main", "app-runtime_main", COMPILE);
    assertModuleModuleDepScope("app_main", "lib1-runtime_main", COMPILE);
    assertModuleModuleDepScope("app_main", "lib2-runtime_main", COMPILE);
  }


  @Test
  public void testCompositeBuildWithProjectNameDuplicates() throws Exception {
    IdeModifiableModelsProvider modelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(getMyProject());
    modelsProvider.newModule(getProjectPath() + "/api.iml", JavaModuleType.getModuleType().getId());
    modelsProvider.newModule(getProjectPath() + "/api_main.iml", JavaModuleType.getModuleType().getId());
    modelsProvider.newModule(getProjectPath() + "/my-app-api.iml", JavaModuleType.getModuleType().getId());
    modelsProvider.newModule(getProjectPath() + "/my-app-api_main.iml", JavaModuleType.getModuleType().getId());
    modelsProvider.newModule(getProjectPath() + "/my-utils-api.iml", JavaModuleType.getModuleType().getId());
    modelsProvider.newModule(getProjectPath() + "/my-utils-api_main.iml", JavaModuleType.getModuleType().getId());
    edt(() -> ApplicationManager.getApplication().runWriteAction(modelsProvider::commit));

    createSettingsFile("""
                         rootProject.name='adhoc'

                         includeBuild '../my-app'
                         includeBuild '../my-utils'""");

    // Project configuration without an existing directory is not allowed
    createProjectSubDir("../my-app/api");
    createProjectSubDir("../my-utils/string-utils");
    createProjectSubDir("../my-utils/number-utils");
    createProjectSubDir("../my-utils/api");

    createProjectSubFile("../my-app/settings.gradle", """
      rootProject.name = 'my-app'
      include 'api'
      """);
    createProjectSubFile("../my-app/build.gradle",
                         createBuildScriptBuilder()
                           .withJavaPlugin()
                           .addGroup("org.sample")
                           .addVersion("1.0")
                           .addImplementationDependency("org.sample:number-utils:1.0")
                           .addImplementationDependency("org.sample:string-utils:1.0")
                           .project(":api", it -> {
                             it
                               .withJavaPlugin()
                               .addImplementationDependency("commons-lang:commons-lang:2.6");
                           })
                           .generate());

    createProjectSubFile("../my-utils/settings.gradle",
                         "rootProject.name = 'my-utils'\n" +
                         "include 'number-utils', 'string-utils', 'api'");
    createProjectSubFile("../my-utils/build.gradle",
                         createBuildScriptBuilder()
                           .subprojects(it -> {
                             it.addGroup("org.sample")
                               .addVersion("1.0")
                               .withJavaLibraryPlugin();
                           })
                           .project(":string-utils", it -> {
                             it.withMavenCentral();
                             it.addApiDependency("org.apache.commons:commons-lang3:3.4");
                           })
                           .project(":api", it -> { it.addApiDependency("junit:junit:4.11"); })
                           .generate());

    // check for non-qualified module names
    getCurrentExternalProjectSettings().setUseQualifiedModuleNames(false);
    importProject();

    String myAppApiModuleName = getMyTestDir().getFileName() + "-my-app-api";
    String myAppApiMainModuleName = getMyTestDir().getFileName() + "-my-app-api_main";
    String myUtilsApiMainModuleName = "org.sample-my-utils-api_main";
    assertModules(
      // non-gradle modules
      "api", "api_main", "my-app-api", "my-app-api_main", "my-utils-api", "my-utils-api_main",
      // generated modules by gradle import
      "adhoc",
      "my-app", "my-app_main", "my-app_test",
      myAppApiModuleName, myAppApiMainModuleName, "my-app-api_test",
      "my-utils",
      "org.sample-my-utils-api", myUtilsApiMainModuleName, "my-utils-api_test",
      "string-utils", "string-utils_main", "string-utils_test",
      "number-utils", "number-utils_main", "number-utils_test"
    );

    String[] emptyModules =
      new String[]{
        // non-gradle modules
        "api", "api_main", "my-app-api", "my-app-api_main", "my-utils-api", "my-utils-api_main",
        // generated modules by gradle import
        "adhoc", "my-app", myAppApiModuleName, "my-utils", "string-utils", "number-utils"};
    for (String rootModule : emptyModules) {
      assertModuleLibDeps(rootModule);
      assertModuleModuleDeps(rootModule);
    }
    assertModuleModuleDeps("my-app_main", "number-utils_main", "string-utils_main");
    assertModuleModuleDepScope("my-app_main", "number-utils_main", COMPILE);
    assertModuleModuleDepScope("my-app_main", "string-utils_main", COMPILE);
    assertModuleLibDepScope("my-app_main", "Gradle: org.apache.commons:commons-lang3:3.4", COMPILE);

    // my-app api project
    assertModuleModuleDeps(myAppApiMainModuleName);
    assertModuleLibDeps(myAppApiMainModuleName, "Gradle: commons-lang:commons-lang:2.6");
    assertModuleLibDepScope(myAppApiMainModuleName, "Gradle: commons-lang:commons-lang:2.6", COMPILE);

    assertModuleModuleDeps(myUtilsApiMainModuleName);
    //assertModuleLibDeps("my-utils-api_main", "Gradle: junit:junit:4.11");
    assertModuleLibDepScope(myUtilsApiMainModuleName, "Gradle: junit:junit:4.11", COMPILE);
    //assertModuleLibDepScope("my-utils-api_main", "Gradle: org.hamcrest:hamcrest-core:1.3", COMPILE);
  }

  @Test
  public void testApiDependenciesAreImported() throws Exception {
    createSettingsFile("rootProject.name = \"project-b\"\n" +
                       "includeBuild 'project-a'");

    createProjectSubFile("project-a/settings.gradle",
                                                      "rootProject.name = \"project-a\"\n" +
                                                      "include 'core', 'ext'");

    createProjectSubFile("project-a/core/build.gradle",
                         createBuildScriptBuilder()
                           .withMavenCentral()
                           .withKotlinJvmPlugin()
                           .withJavaLibraryPlugin()
                           .generate());

    createProjectSubFile("project-a/ext/build.gradle",
                         createBuildScriptBuilder()
                           .withMavenCentral()
                           .withKotlinJvmPlugin()
                           .withJavaLibraryPlugin()
                           .addGroup("myGroup.projectA")
                           .addVersion("1.0-SNAPSHOT")
                           .addDependency("api project(':core')")
                           .generate());

    createProjectSubFile("project-a/build.gradle", "");

    importProject(createBuildScriptBuilder()
                    .addPostfix("apply plugin: 'java-library'",
                                "group = 'myGroup'",
                                "version = '1.0-SNAPSHOT'",
                                "dependencies {",
                                "    api group: 'myGroup.projectA', name: 'ext', version: '1.0-SNAPSHOT'",
                                "}"
                    )
                    .generate());

    assertModules("project-a",
                  "project-a.core", "project-a.core.main", "project-a.core.test",
                  "project-a.ext", "project-a.ext.main", "project-a.ext.test",
                  "project-b", "project-b.main", "project-b.test");

    assertModuleModuleDeps("project-b.main", "project-a.ext.main", "project-a.core.main");
  }


  @Test
  // todo should this be fixed for Gradle versions [3.1, 4.9)?
  @TargetVersions("4.9+")
  public void testTransitiveSourceSetDependenciesAreImported() throws Exception {
    createSettingsFile("rootProject.name = \"project-b\"\n" +
                       "includeBuild 'project-a'");

    createProjectSubFile("project-a/settings.gradle", "rootProject.name = \"project-a\"");

    createProjectSubFile("project-a/build.gradle", script(it -> {
      it.withIdeaPlugin()
        .withJavaPlugin()
        .addGroup("myGroup")
        .addVersion("1.0-SNAPSHOT")
        .addPrefix(
          "sourceSets {",
          "    util {",
          "        java.srcDir 'src/util/java'",
          "        resources.srcDir 'src/util/resources'",
          "    }",
          "}",
          "configurations {",
          "  implementation {",
          "    extendsFrom utilImplementation",
          "  }",
          "}",
          "jar {",
          "  from sourceSets.util.output",
          "}",
          "compileJava {",
          "    dependsOn(compileUtilJava)",
          "}")
        .addImplementationDependency(it.code("sourceSets.util.output"));
    }));
    createProjectSubFile("project-a/src/main/java/my/pack/Clazz.java", "package my.pack; public class Clazz{};");
    createProjectSubFile("project-a/src/main/util/my/pack/Util.java", "package my.pack; public class Util{};");

    createProjectSubFile("src/main/java/my/pack/ClazzB.java", "package my.pack; public class CLazzB{};");
    importProject(script(it -> {
      it.withIdeaPlugin()
        .withJavaPlugin()
        .addGroup("myGroup")
        .addVersion("1.0-SNAPSHOT")
        .addImplementationDependency(it.code("group: 'myGroup', name: 'project-a', version: '1.0-SNAPSHOT'"));
    }));

    assertModules("project-a",
                  "project-a.main", "project-a.test", "project-a.util",
                  "project-b", "project-b.main", "project-b.test");

    assertModuleModuleDeps("project-b.main",  "project-a.main", "project-a.util");
  }

  @Test
  public void testProjectWithCompositePluginDependencyImported() throws Exception {
    createSettingsFile("includeBuild('plugin'); includeBuild('consumer')");
    createProjectSubFile("plugin/settings.gradle", "rootProject.name = 'test-plugin'");
    createProjectSubFile("plugin/build.gradle", script(it -> {
      it.withJavaPlugin()
        .addGroup("myGroup")
        .addVersion("1.0");
    }));

    // consumer need to be complicated to display the issue
    createProjectSubFile("consumer/settings.gradle",
                         """
                           pluginManagement {
                             resolutionStrategy {
                               eachPlugin {
                                 println "resolving ${requested.id.id} dependency"
                                 if(requested.id.id == "test-plugin") {
                                   useModule("myGroup:test-plugin:1.0")
                                 }
                               }
                             }
                           }
                           include 'library'""");
    createProjectSubFile("consumer/build.gradle", createBuildScriptBuilder()
      .addPostfix(
        "plugins {",
        " id 'test-plugin' apply false",
        "}",
        "subprojects {",
        "  apply plugin: 'java'",
        "}"
      )
      .generate());
    // sourceSets here will fail to evaluate if parent project was not evaluated successfully
    // because of missing test-plugin, caused by bad included build evaluation order.
    createProjectSubFile("consumer/library/build.gradle", createBuildScriptBuilder()
      .addPostfix(
        "sourceSets {",
        "  integrationTest ",
        "}"
      )
      .generate());

    importProject("");

    assertModules("project",
                  "test-plugin", "test-plugin.main", "test-plugin.test",
                  "consumer", "consumer.library", "consumer.library.main", "consumer.library.test", "consumer.library.integrationTest");
  }



  @Test
  public void testSubstituteDependencyWithRootProject() throws Exception {
    if (isGradleAtLeast("6.6")) {
      createSettingsFile("""
                         rootProject.name = "root-project"
                         include 'sub-project'
                         includeBuild('included-project') { dependencySubstitution { substitute module('my.grp:myId') using project(':') } }""");
    } else {
      createSettingsFile("""
                         rootProject.name = "root-project"
                         include 'sub-project'
                         includeBuild('included-project') { dependencySubstitution { substitute module('my.grp:myId') with project(':') } }""");
    }


    createProjectSubFile("sub-project/build.gradle",
                         createBuildScriptBuilder()
                           .withJavaPlugin()
                           .addDependency("implementation 'my.grp:myId:1.0'")
                           .generate());

    createProjectSubFile("included-project/settings.gradle", "rootProject.name = 'myId'");
    createProjectSubFile("included-project/build.gradle",
                         createBuildScriptBuilder()
                           .withJavaPlugin()
                           .addGroup("my.grp")
                           .addVersion("1.0")
                           .generate());

    importProject("");

    assertModules("root-project",
                  "root-project.sub-project", "root-project.sub-project.main", "root-project.sub-project.test",
                  "myId", "myId.main", "myId.test");

    assertModuleModuleDeps("root-project.sub-project.main", "myId.main");
  }

  @Test
  public void testScopeUpdateForSubstituteDependency() throws Exception {
    createSettingsFile("""
                         rootProject.name = 'pA'
                         include 'pA-1', 'pA-2'
                         includeBuild('pB')
                         includeBuild('pC')""");

    createProjectSubFile("pB/settings.gradle");
    createProjectSubFile("pC/settings.gradle");

    createProjectSubFile("pA-1/build.gradle",
                         createBuildScriptBuilder()
                           .applyPlugin("java-library")
                           .addDependency("implementation 'group:pC'")
                           .generate());

    createProjectSubFile("pA-2/build.gradle",
                         createBuildScriptBuilder()
                           .applyPlugin("java-library")
                           .addDependency("implementation project(':pA-1')")
                           .addDependency("implementation 'group:pB'")
                           .generate());

    createProjectSubFile("pB/build.gradle",
                         createBuildScriptBuilder()
                           .addPostfix("group = 'group'")
                           .applyPlugin("java-library")
                           .addDependency("api 'group:pC'")
                           .generate());

    createProjectSubFile("pC/build.gradle",
                         createBuildScriptBuilder()
                           .addPostfix("group = 'group'")
                           .applyPlugin("java-library")
                           .generate());

    //enableGradleDebugWithSuspend();
    importProject("");

    assertModules("pA",
                  "pA.pA-1", "pA.pA-1.main", "pA.pA-1.test",
                  "pA.pA-2", "pA.pA-2.main", "pA.pA-2.test",
                  "pB", "pB.main", "pB.test",
                  "pC", "pC.main", "pC.test");

    assertModuleModuleDepScope("pA.pA-2.main", "pC.main", COMPILE);
  }

  @Test
  @Ignore
  public void testIdeCompositeBuild() throws Exception {
    createSettingsFile("rootProject.name='rootProject'\n");
    // generate Gradle wrapper files for the test
    importProject();

    // create files for the first "included" build1
    createProjectSubFile("build1/settings.gradle", """
      rootProject.name = 'project1'
      include 'utils'
      """);
    createProjectSubFile("build1/build.gradle",
                         createBuildScriptBuilder()
                           .addGroup("org.build1")
                           .addVersion("1.0")
                           .withJavaPlugin()
                           .addImplementationDependency("org.build2:project2:1.0")
                           .addImplementationDependency("org.build2:utils:1.0")
                           .generate());
    createProjectSubFile("build1/utils/build.gradle",
                         """
                           apply plugin: 'java'
                           group 'org.build1'
                           version '1.0'
                           """);
    // use Gradle wrapper of the test root project
    copyRecursively(getProjectPath("gradle"), getProjectPath("build1"));

    // create files for the second "included" build2
    createProjectSubFile("build2/settings.gradle", """
      rootProject.name = 'project2'
      include 'utils'
      """);
    createProjectSubFile("build2/build.gradle",
                         """
                           apply plugin: 'java'
                           group 'org.build2'
                           version '1.0'
                           """);
    createProjectSubFile("build2/utils/build.gradle",
                         """
                           apply plugin: 'java'
                           group 'org.build2'
                           version '1.0'
                           """);
    // use Gradle wrapper of the test root project
    copyRecursively(getProjectPath("gradle"), getProjectPath("build2"));

    AutoImportProjectTrackerSettings importProjectTrackerSettings = AutoImportProjectTrackerSettings.getInstance(getMyProject());
    ExternalSystemProjectTrackerSettings.AutoReloadType autoReloadType = importProjectTrackerSettings.getAutoReloadType();
    try {
      importProjectTrackerSettings.setAutoReloadType(ExternalSystemProjectTrackerSettings.AutoReloadType.NONE);

      GradleProjectSettings build1Settings = linkProject(path("build1"));
      linkProject(path("build2"));

      addToComposite(getCurrentExternalProjectSettings(), "project1", path("build1"));
      addToComposite(getCurrentExternalProjectSettings(), "project2", path("build2"));
      addToComposite(build1Settings, "project2", path("build2"));

      ExternalSystemUtil.refreshProject(path("build2"), createImportSpec());
      ExternalSystemUtil.refreshProject(path("build1"), createImportSpec());

      importProject(createBuildScriptBuilder()
                      .withJavaPlugin()
                      .addImplementationDependency("org.build1:project1:1.0")
                      .addImplementationDependency("org.build1:utils:1.0")
                      .addImplementationDependency("org.build2:utils:1.0")
                      .generate());

      assertModules(
        "rootProject", "rootProject.main", "rootProject.test",
        "project1", "project1.main", "project1.test",
        "project1.utils", "project1.utils.main", "project1.utils.test",
        "project2", "project2.main", "project2.test",
        "project2.utils", "project2.utils.main", "project2.utils.test"
      );

      assertModuleLibDeps("rootProject.main", ArrayUtilRt.EMPTY_STRING_ARRAY);
      assertModuleModuleDeps("rootProject.main", "project1.main", "project1.utils.main", "project2.utils.main", "project2.main");
    }
    finally {
      importProjectTrackerSettings.setAutoReloadType(autoReloadType);
    }
  }

  @Test
  @TargetVersions("4.10+") // https://docs.gradle.org/4.10/release-notes.html#nested-included-builds
  public void testNestedCompositeBuilds() throws Exception {
    createSettingsFile("rootProject.name = 'root'\n" +
                       "includeBuild('A')");
    createProjectSubFile("A/settings.gradle", "includeBuild('AA')");
    createProjectSubFile("A/AA/settings.gradle", "includeBuild('AAA')");
    createProjectSubFile("A/AA/AAA/settings.gradle");

    importProject("");

    assertModules("root", "A", "AA", "AAA");
  }

  @Test
  @TargetVersions("8.0+")
  public void testNestedCompositeBuildsWithDuplicateNames() throws Exception {
    createSettingsFile("""
     rootProject.name = 'root'
     includeBuild('doppelganger')
     includeBuild('nested')
    """);
    createProjectSubFile("nested/settings.gradle", """
      includeBuild('doppelganger')
    """);
    createProjectSubFile("doppelganger/settings.gradle", "include('module')");
    createProjectSubFile("doppelganger/module/build.gradle", "//empty");
    createProjectSubFile("nested/doppelganger/settings.gradle", "include('module')");
    createProjectSubFile("nested/doppelganger/module/build.gradle", "//empty");

    importProject("");

    assertModules("root", "nested", "doppelganger", "nested.doppelganger", "doppelganger.module", "nested.doppelganger.module");
  }

  @Test
  @TargetVersions("6.8+") // https://docs.gradle.org/6.8-rc-1/release-notes.html#desired-cycles-between-builds-are-now-fully-supported
  public void testNestedCyclicCompositeBuilds() throws Exception {
    createSettingsFile("""
                         rootProject.name = 'root'
                         includeBuild('A')
                         includeBuild('B')
                         includeBuild('C')
                         includeBuild('.')""");
    createProjectSubFile("A/settings.gradle", "includeBuild('AA')");
    createProjectSubFile("A/AA/settings.gradle", "includeBuild('AAA')");
    createProjectSubFile("A/AA/AAA/settings.gradle");

    createProjectSubFile("B/settings.gradle", "includeBuild('../C')\n" +
                                              "includeBuild('../D')");

    createProjectSubFile("C/settings.gradle", "includeBuild('..')\n" +
                                              "includeBuild('../D')");

    createProjectSubFile("D/settings.gradle", "includeBuild('../A')\n" +
                                              "includeBuild('../C')");

    importProject("");

    assertModules("root",
                  "A", "AA", "AAA",
                  "B",
                  "C",
                  "D");
  }

  private static void addToComposite(GradleProjectSettings settings, String buildRootProjectName, String buildPath) {
    GradleProjectSettings.CompositeBuild compositeBuild = settings.getCompositeBuild();
    if (compositeBuild == null) {
      compositeBuild = new GradleProjectSettings.CompositeBuild();
      compositeBuild.setCompositeDefinitionSource(CompositeDefinitionSource.IDE);
      settings.setCompositeBuild(compositeBuild);
    }
    assert compositeBuild.getCompositeDefinitionSource() == CompositeDefinitionSource.IDE;

    compositeBuild.setCompositeDefinitionSource(CompositeDefinitionSource.IDE);
    BuildParticipant buildParticipant = new BuildParticipant();
    buildParticipant.setRootProjectName(buildRootProjectName);
    buildParticipant.setRootPath(buildPath);
    compositeBuild.getCompositeParticipants().add(buildParticipant);
  }

  private GradleProjectSettings linkProject(String path) {
    GradleProjectSettings projectSettings = new GradleProjectSettings();
    projectSettings.setExternalProjectPath(path);
    projectSettings.setDistributionType(DistributionType.DEFAULT_WRAPPED);
    GradleSettings.getInstance(getMyProject()).linkProject(projectSettings);
    return projectSettings;
  }

  private void assertTasksProjectPath(@NotNull String moduleName, @NotNull String expectedTaskProjectPath) {
    assertTasksProjectPath(moduleName, expectedTaskProjectPath, "");
  }

  private void assertTasksProjectPath(@NotNull String moduleName,
                                      @NotNull String expectedTaskProjectPath,
                                      @NotNull String expectedTaskPrefix) {
    for (DataNode<TaskData> node : ExternalSystemApiUtil
      .findAll(GradleUtil.findGradleModuleData(getModule(moduleName)), ProjectKeys.TASK)) {
      TaskData taskData = node.getData();
      String actualTaskProjectPath = taskData.getLinkedExternalProjectPath();
      expectedTaskProjectPath = FileUtil.toCanonicalPath(expectedTaskProjectPath);
      actualTaskProjectPath = FileUtil.toCanonicalPath(actualTaskProjectPath);
      assertEquals(expectedTaskProjectPath, actualTaskProjectPath);
      assertThat(taskData.getName()).startsWith(expectedTaskPrefix);
    }
  }
}
