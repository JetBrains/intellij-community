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

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

/**
 * @author Vladislav.Soroka
 * @since 6/30/2014
 */
@SuppressWarnings("JUnit4AnnotatedMethodInJUnit3TestCase")
public class GradleFoldersImportingTest extends GradleImportingTestCase {

  @Test
  public void testBaseJavaProject() throws Exception {

    importProject(
      "apply plugin: 'java'"
    );

    assertModules("project");
    assertContentRoots("project", getProjectPath());

    assertDefaultGradleJavaProjectFolders("project");

    assertModuleOutput("project",
                       getProjectPath() + "/build/classes/main",
                       getProjectPath() + "/build/classes/test");
  }

  @Test
  public void testProjectWithInheritedOutputDirs() throws Exception {

    importProject(
      "apply plugin: 'java'\n" +
      "apply plugin: 'idea'\n" +
      "idea {\n" +
      "  module {\n" +
      "    inheritOutputDirs = true\n" +
      "  }\n" +
      "}"
    );

    assertModules("project");
    assertContentRoots("project", getProjectPath());

    assertDefaultGradleJavaProjectFolders("project");

    assertModuleInheritedOutput("project");
  }

  protected void assertDefaultGradleJavaProjectFolders(@NotNull String moduleName) {
    assertSources(moduleName, "src/main/java");
    assertResources(moduleName, "src/main/resources");
    assertTestSources(moduleName, "src/test/java");
    assertTestResources(moduleName, "src/test/resources");
    assertExcludes(moduleName, ".gradle", "build");
  }
}
