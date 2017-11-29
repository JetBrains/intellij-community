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
  public void testCompositeBuildWithProjectNameDuplicates() throws Exception {
    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(myProject);
    modelsProvider.newModule(getProjectPath() + "/api.iml", StdModuleTypes.JAVA.getId());
    modelsProvider.newModule(getProjectPath() + "/my-app-api.iml", StdModuleTypes.JAVA.getId());
    modelsProvider.newModule(getProjectPath() + "/my-app-api_main.iml", StdModuleTypes.JAVA.getId());
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

    if (isGradle40orNewer()) {
      assertModules("adhoc", "api",
                    "my-app", "my-app_main", "my-app_test",
                    "my-app-api", "my-app-api~1", "my-app-api_main", "my-app-api_main~1", "my-app-api_test",
                    "my-utils",
                    "string-utils", "string-utils_main", "string-utils_test",
                    "number-utils", "number-utils_main", "number-utils_test",
                    "my-utils-api", "my-utils-api_main", "my-utils-api_test");
    }
    else {
      assertModules("adhoc", "api", "api_main", "api_test",
                    "my-app", "my-app_main", "my-app_test",
                    "my-app-api", "my-app-api~1", "my-app-api_main",
                    "my-utils",
                    "string-utils", "string-utils_main", "string-utils_test",
                    "number-utils", "number-utils_main", "number-utils_test",
                    "my-utils-api", "my-utils-api_main", "my-utils-api_test");
    }

    String[] emptyModules =
      new String[]{"api", "adhoc", "my-app", "my-app-api", "my-app-api_main", "my-app-api~1", "my-utils", "my-utils-api", "string-utils",
        "number-utils"};
    for (String rootModule : emptyModules) {
      assertModuleLibDeps(rootModule);
      assertModuleModuleDeps(rootModule);
    }
    assertModuleModuleDeps("my-app_main", "number-utils_main", "string-utils_main");
    assertModuleModuleDepScope("my-app_main", "number-utils_main", COMPILE);
    assertModuleModuleDepScope("my-app_main", "string-utils_main", COMPILE);
    assertModuleLibDepScope("my-app_main", "Gradle: org.apache.commons:commons-lang3:3.4", COMPILE);

    // my-app api project
    if (isGradle40orNewer()) {
      assertModuleModuleDeps("my-app-api_main~1");
      assertModuleLibDeps("my-app-api_main~1", "Gradle: commons-lang:commons-lang:2.6");
      assertModuleLibDepScope("my-app-api_main~1", "Gradle: commons-lang:commons-lang:2.6", COMPILE);
    } else {
      assertModuleModuleDeps("api_main");
      assertModuleLibDeps("api_main", "Gradle: commons-lang:commons-lang:2.6");
      assertModuleLibDepScope("api_main", "Gradle: commons-lang:commons-lang:2.6", COMPILE);
    }

    assertModuleModuleDeps("my-utils-api_main");
    //assertModuleLibDeps("my-utils-api_main", "Gradle: junit:junit:4.11");
    assertModuleLibDepScope("my-utils-api_main", "Gradle: junit:junit:4.11", COMPILE);
    //assertModuleLibDepScope("my-utils-api_main", "Gradle: org.hamcrest:hamcrest-core:1.3", COMPILE);
  }
}
