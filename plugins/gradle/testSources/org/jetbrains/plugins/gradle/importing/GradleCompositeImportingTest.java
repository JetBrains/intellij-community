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
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.StdModuleTypes;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;
import org.junit.Test;

import static com.intellij.openapi.roots.DependencyScope.COMPILE;

/**
 * @author Vladislav.Soroka
 * @since 2/20/2017
 */
@SuppressWarnings("JUnit4AnnotatedMethodInJUnit3TestCase")
public class GradleCompositeImportingTest extends GradleImportingTestCase {
  @Test
  @TargetVersions("3.3+")
  public void testBasicCompositeBuild() throws Exception {
    createSettingsFile("rootProject.name='adhoc'\n" +
                       "\n" +
                       "includeBuild '../my-app'\n" +
                       "includeBuild '../my-utils'");

    createProjectSubFile("../my-app/settings.gradle", "rootProject.name = 'my-app'\n");
    createProjectSubFile("../my-app/build.gradle",
                         "apply plugin: 'java'\n" +
                         "group 'org.sample'\n" +
                         "version '1.0'\n" +
                         "\n" +
                         "dependencies {\n" +
                         "  compile 'org.sample:number-utils:1.0'\n" +
                         "  compile 'org.sample:string-utils:1.0'\n" +
                         "}\n");

    createProjectSubFile("../my-utils/settings.gradle",
                         "rootProject.name = 'my-utils'\n" +
                         "include 'number-utils', 'string-utils' ");
    createProjectSubFile("../my-utils/build.gradle", injectRepo(
      "subprojects {\n" +
      "  apply plugin: 'java'\n" +
      "\n" +
      "  group 'org.sample'\n" +
      "  version '1.0'\n" +
      "}\n" +
      "\n" +
      "project(':string-utils') {\n" +
      "  dependencies {\n" +
      "    compile 'org.apache.commons:commons-lang3:3.4'\n" +
      "  }\n" +
      "} "));

    importProject();

    assertModules("adhoc",
                  "my-app", "my-app_main", "my-app_test",
                  "my-utils",
                  "string-utils", "string-utils_test", "string-utils_main",
                  "number-utils", "number-utils_main", "number-utils_test");

    String[] rootModules = new String[]{"adhoc", "my-app", "my-utils", "string-utils", "number-utils"};
    for (String rootModule : rootModules) {
      assertModuleLibDeps(rootModule);
      assertModuleModuleDeps(rootModule);
    }
    assertModuleModuleDeps("my-app_main", "number-utils_main", "string-utils_main");
    assertModuleModuleDepScope("my-app_main", "number-utils_main", COMPILE);
    assertModuleModuleDepScope("my-app_main", "string-utils_main", COMPILE);
    assertModuleLibDepScope("my-app_main", "Gradle: org.apache.commons:commons-lang3:3.4", COMPILE);
  }

  @Test
  @TargetVersions("3.3+")
  public void testCompositeBuildWithNestedModules() throws Exception {
    createSettingsFile("rootProject.name = 'app'\n" +
                       "includeBuild 'lib'");

    createProjectSubFile("lib/settings.gradle", "rootProject.name = 'lib'\n" +
                                                "include 'runtime'\n" +
                                                "include 'runtime:runtime-mod'");
    createProjectSubFile("lib/runtime/runtime-mod/build.gradle",
                         "apply plugin: 'java'\n" +
                         "group = 'my.group'");

    importProject("apply plugin: 'java'\n" +
                  "dependencies {\n" +
                  "  compile 'my.group:runtime-mod'\n" +
                  "}");

    assertModules("app", "app_main", "app_test",
                  "lib",
                  "runtime",
                  "runtime-mod", "runtime-mod_main", "runtime-mod_test");

    assertModuleModuleDepScope("app_main", "runtime-mod_main", COMPILE);
  }


  @Test
  @TargetVersions("3.3+")
  public void testCompositeBuildWithNestedModulesSingleModulePerProject() throws Exception {
    createSettingsFile("rootProject.name = 'app'\n" +
                       "includeBuild 'lib'");

    createProjectSubFile("lib/settings.gradle", "rootProject.name = 'lib'\n" +
                                                "include 'runtime'\n" +
                                                "include 'runtime:runtime-mod'");
    createProjectSubFile("lib/runtime/runtime-mod/build.gradle",
                         "apply plugin: 'java'\n" +
                         "group = 'my.group'");

    importProjectUsingSingeModulePerGradleProject("apply plugin: 'java'\n" +
                                                  "dependencies {\n" +
                                                  "  compile 'my.group:runtime-mod'\n" +
                                                  "}");

    assertModules("app",
                  "lib",
                  "runtime",
                  "runtime-mod");

    assertMergedModuleCompileModuleDepScope("app", "runtime-mod");
  }


  @Test
  @TargetVersions("4.0+")
  public void testCompositeBuildWithGradleProjectDuplicates() throws Exception {
    createSettingsFile("rootProject.name = 'app'\n" +
                       "include 'runtime'\n" +
                       "includeBuild 'lib1'\n" +
                       "includeBuild 'lib2'");

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


    importProjectUsingSingeModulePerGradleProject("apply plugin: 'java'\n" +
                                                  "dependencies {\n" +
                                                  "  compile project(':runtime')\n" +
                                                  "  compile 'my.group.lib_1:runtime'\n" +
                                                  "  compile 'my.group.lib_2:runtime'\n" +
                                                  "}");

    assertModules("app", "app-runtime",
                  "lib1", "lib1-runtime",
                  "lib2", "lib2-runtime");

    assertMergedModuleCompileModuleDepScope("app", "app-runtime");
    assertMergedModuleCompileModuleDepScope("app", "lib1-runtime");
    assertMergedModuleCompileModuleDepScope("app", "lib2-runtime");
  }


  @Test
  @TargetVersions("3.3+")
  public void testCompositeBuildWithGradleProjectDuplicatesModulePerSourceSet() throws Exception {
    createSettingsFile("rootProject.name = 'app'\n" +
                       "include 'runtime'\n" +
                       "includeBuild 'lib1'\n" +
                       "includeBuild 'lib2'");

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


    importProject("apply plugin: 'java'\n" +
                  "dependencies {\n" +
                  "  compile project(':runtime')\n" +
                  "  compile 'my.group.lib_1:runtime'\n" +
                  "  compile 'my.group.lib_2:runtime'\n" +
                  "}");

    if (isGradle40orNewer()) {
      assertModules("app", "app_main", "app_test",
                    "app-runtime", "app-runtime_main", "app-runtime_test",
                    "lib1", "lib1-runtime", "lib1-runtime_main", "lib1-runtime_test",
                    "lib2", "lib2-runtime", "lib2-runtime_main", "lib2-runtime_test");
    }
    else {
      assertModules("app", "app_main", "app_test",
                    "runtime", "runtime_main", "runtime_test",
                    "lib1", "my.group.lib_1-runtime", "my.group.lib_1-runtime_main", "my.group.lib_1-runtime_test",
                    "lib2", "my.group.lib_2-runtime", "my.group.lib_2-runtime_main", "my.group.lib_2-runtime_test");
    }

    if (isGradle40orNewer()) {
      assertModuleModuleDepScope("app_main", "app-runtime_main", COMPILE);
      assertModuleModuleDepScope("app_main", "lib1-runtime_main", COMPILE);
      assertModuleModuleDepScope("app_main", "lib2-runtime_main", COMPILE);
    }
    else {
      assertModuleModuleDepScope("app_main", "runtime_main", COMPILE);
      assertModuleModuleDepScope("app_main", "my.group.lib_1-runtime_main", COMPILE);
      assertModuleModuleDepScope("app_main", "my.group.lib_2-runtime_main", COMPILE);
    }
  }


  @Test
  @TargetVersions("3.3+")
  public void testCompositeBuildWithProjectNameDuplicates() throws Exception {
    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(myProject);
    modelsProvider.newModule(getProjectPath() + "/api.iml", StdModuleTypes.JAVA.getId());
    modelsProvider.newModule(getProjectPath() + "/api_main.iml", StdModuleTypes.JAVA.getId());
    modelsProvider.newModule(getProjectPath() + "/my-app-api.iml", StdModuleTypes.JAVA.getId());
    modelsProvider.newModule(getProjectPath() + "/my-app-api_main.iml", StdModuleTypes.JAVA.getId());
    modelsProvider.newModule(getProjectPath() + "/my-utils-api.iml", StdModuleTypes.JAVA.getId());
    modelsProvider.newModule(getProjectPath() + "/my-utils-api_main.iml", StdModuleTypes.JAVA.getId());
    edt(() -> ApplicationManager.getApplication().runWriteAction(modelsProvider::commit));

    createSettingsFile("rootProject.name='adhoc'\n" +
                       "\n" +
                       "includeBuild '../my-app'\n" +
                       "includeBuild '../my-utils'");

    createProjectSubFile("../my-app/settings.gradle", "rootProject.name = 'my-app'\n" +
                                                      "include 'api'\n");
    createProjectSubFile("../my-app/build.gradle",
                         "apply plugin: 'java'\n" +
                         "group 'org.sample'\n" +
                         "version '1.0'\n" +
                         "\n" +
                         "dependencies {\n" +
                         "  compile 'org.sample:number-utils:1.0'\n" +
                         "  compile 'org.sample:string-utils:1.0'\n" +
                         "}\n" +
                         "project(':api') {\n" +
                         "  apply plugin: 'java'\n" +
                         "  dependencies {\n" +
                         "    compile 'commons-lang:commons-lang:2.6'\n" +
                         "  }\n" +
                         "}\n");

    createProjectSubFile("../my-utils/settings.gradle",
                         "rootProject.name = 'my-utils'\n" +
                         "include 'number-utils', 'string-utils', 'api'");
    createProjectSubFile("../my-utils/build.gradle", injectRepo(
      "subprojects {\n" +
      "  apply plugin: 'java'\n" +
      "\n" +
      "  group 'org.sample'\n" +
      "  version '1.0'\n" +
      "}\n" +
      "\n" +
      "project(':string-utils') {\n" +
      "  dependencies {\n" +
      "    compile 'org.apache.commons:commons-lang3:3.4'\n" +
      "  }\n" +
      "}\n" +
      "project(':api') {\n" +
      "  dependencies {\n" +
      "    compile 'junit:junit:4.11'\n" +
      "  }\n" +
      "}"));

    importProject();

    String myAppApiModuleName = myTestDir.getName() + "-my-app-api";
    String myAppApiMainModuleName = myTestDir.getName() + "-my-app-api_main";
    String myUtilsApiMainModuleName = isGradle40orNewer() ? "org.sample-my-utils-api_main" : "org.sample-api_main";
    if (isGradle40orNewer()) {
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
    }
    else {
      assertModules(
        // non-gradle modules
        "api", "api_main", "my-app-api", "my-app-api_main", "my-utils-api", "my-utils-api_main",
        // generated modules by gradle import
        "adhoc",
        "my-app", "my-app_main", "my-app_test",
        myAppApiModuleName, myAppApiMainModuleName, "org.sample-api_test",
        "my-utils",
        "org.sample-api", myUtilsApiMainModuleName, "api_test",
        "string-utils", "string-utils_main", "string-utils_test",
        "number-utils", "number-utils_main", "number-utils_test"
      );
    }

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
}
