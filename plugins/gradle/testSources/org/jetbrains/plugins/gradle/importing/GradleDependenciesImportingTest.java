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

import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import org.gradle.util.GradleVersion;
import org.junit.Test;

import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 6/30/2014
 */
@SuppressWarnings("JUnit4AnnotatedMethodInJUnit3TestCase")
public class GradleDependenciesImportingTest extends GradleImportingTestCase {

  @Test
  public void testDependencyScopeMerge() throws Exception {
    createSettingsFile("include 'api', 'impl' ");

    importProject(
      "allprojects {\n" +
      "  apply plugin: 'java'\n" +
      "\n" +
      "  sourceCompatibility = 1.5\n" +
      "  version = '1.0'\n" +
      "}\n" +
      "\n" +
      "dependencies {\n" +
      "  compile project(':api')\n" +
      "  testCompile project(':impl'), 'junit:junit:4.11'\n" +
      "  runtime project(':impl')\n" +
      "}"
    );

    assertModules("project", "project_main", "project_test", "api", "api_main", "api_test", "impl", "impl_main", "impl_test");
    assertModuleModuleDepScope("project_test", "project_main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("api_test", "api_main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("impl_test", "impl_main", DependencyScope.COMPILE);

    assertModuleModuleDepScope("project_main", "api_main", DependencyScope.COMPILE);

    assertModuleModuleDepScope("project_main", "impl_main", DependencyScope.RUNTIME);
    assertModuleModuleDepScope("project_test", "impl_main", DependencyScope.COMPILE);

    assertModuleLibDepScope("project_test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleLibDepScope("project_test", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);
  }

  @Test
  public void testCustomSourceSetsDependencies() throws Exception {
    createSettingsFile("include 'api', 'impl' ");

    importProject(
      "allprojects {\n" +
      "  apply plugin: 'java'\n" +
      "\n" +
      "  sourceCompatibility = 1.5\n" +
      "  version = '1.0'\n" +
      "}\n" +
      "\n" +
      "project(\"impl\") {\n" +
      "  sourceSets {\n" +
      "    myCustomSourceSet\n" +
      "    myAnotherSourceSet\n" +
      "  }\n" +
      "  \n" +
      "  dependencies {\n" +
      "    myCustomSourceSetCompile sourceSets.main.output\n" +
      "    myCustomSourceSetCompile project(\":api\")\n" +
      "    myCustomSourceSetRuntime 'junit:junit:4.11'\n" +
      "  }\n" +
      "}\n"
    );

    assertModules("project", "project_main", "project_test", "api", "api_main", "api_test", "impl", "impl_main", "impl_test",
                  "impl_myCustomSourceSet", "impl_myAnotherSourceSet");

    assertModuleModuleDepScope("project_test", "project_main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("api_test", "api_main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("impl_test", "impl_main", DependencyScope.COMPILE);

    assertModuleModuleDepScope("impl_myCustomSourceSet", "impl_main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("impl_myCustomSourceSet", "api_main", DependencyScope.COMPILE);
    assertModuleLibDepScope("impl_myCustomSourceSet", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.RUNTIME);
    assertModuleLibDepScope("impl_myCustomSourceSet", "Gradle: junit:junit:4.11", DependencyScope.RUNTIME);
  }

  @Test
  public void testDependencyWithDifferentClassifiers() throws Exception {
    final VirtualFile depJar = createProjectJarSubFile("lib/dep/dep/1.0/dep-1.0.jar");
    final VirtualFile depTestsJar = createProjectJarSubFile("lib/dep/dep/1.0/dep-1.0-tests.jar");
    final VirtualFile depNonJar = createProjectSubFile("lib/dep/dep/1.0/dep-1.0.someExt");

    importProject(
      "allprojects {\n" +
      "  apply plugin: 'java'\n" +
      "  sourceCompatibility = 1.5\n" +
      "  version = '1.0'\n" +
      "\n" +
      "  repositories {\n" +
      "    maven{ url file('lib') }\n" +
      "  }\n" +
      "}\n" +
      "\n" +
      "dependencies {\n" +
      "  compile 'dep:dep:1.0'\n" +
      "  testCompile 'dep:dep:1.0:tests'\n" +
      "  runtime 'dep:dep:1.0@someExt'\n" +
      "}"
    );

    assertModules("project", "project_main", "project_test");

    assertModuleModuleDepScope("project_test", "project_main", DependencyScope.COMPILE);

    final String depName = "Gradle: dep:dep:1.0";
    assertModuleLibDep("project_main", depName, depJar.getUrl());
    assertModuleLibDepScope("project_main", depName, DependencyScope.COMPILE);
    assertModuleLibDep("project_test", depName, depJar.getUrl());
    assertModuleLibDepScope("project_test", depName, DependencyScope.COMPILE);

    final boolean isArtifactResolutionQuerySupported = GradleVersion.version(gradleVersion).compareTo(GradleVersion.version("2.0")) >= 0;
    final String depTestsName = isArtifactResolutionQuerySupported ? "Gradle: dep:dep:tests:1.0" : PathUtil.toPresentableUrl(depTestsJar.getUrl());
    assertModuleLibDep("project_test", depTestsName, depTestsJar.getUrl());
    assertModuleLibDepScope("project_test", depTestsName, DependencyScope.COMPILE);

    final String depNonJarName = isArtifactResolutionQuerySupported ? "Gradle: dep:dep:someExt:1.0" : PathUtil.toPresentableUrl(depNonJar.getUrl());
    assertModuleLibDep("project_main", depNonJarName, depNonJar.getUrl());
    assertModuleLibDepScope("project_main", depNonJarName, DependencyScope.RUNTIME);
    assertModuleLibDep("project_test", depNonJarName, depNonJar.getUrl());
    assertModuleLibDepScope("project_test", depNonJarName, DependencyScope.RUNTIME);
  }

  @Test
  public void testProjectWithUnresolvedDependency() throws Exception {
    final VirtualFile depJar = createProjectJarSubFile("lib/dep/dep/1.0/dep-1.0.jar");
    importProject(
      "apply plugin: 'java'\n" +
      "\n" +
      "repositories {\n" +
      "  maven { url file('lib') }\n" +
      "}\n" +
      "dependencies {\n" +
      "  compile 'dep:dep:1.0'\n" +
      "  compile 'some:unresolvable-lib:0.1'\n" +
      "}\n"
    );

    assertModules("project", "project_main", "project_test");

    final String depName = "Gradle: dep:dep:1.0";
    assertModuleLibDep("project_main", depName, depJar.getUrl());
    assertModuleLibDepScope("project_main", depName, DependencyScope.COMPILE);
    assertModuleLibDepScope("project_main", "Gradle: some:unresolvable-lib:0.1", DependencyScope.COMPILE);

    final List<LibraryOrderEntry> unresolvableDep = getModuleLibDeps("project_main", "Gradle: some:unresolvable-lib:0.1");
    assertEquals(1, unresolvableDep.size());
    final LibraryOrderEntry unresolvableEntry = unresolvableDep.iterator().next();
    assertFalse(unresolvableEntry.isModuleLevel());
    assertEquals(DependencyScope.COMPILE, unresolvableEntry.getScope());
    final String[] unresolvableEntryUrls = unresolvableEntry.getUrls(OrderRootType.CLASSES);
    assertEquals(1, unresolvableEntryUrls.length);
    assertTrue(unresolvableEntryUrls[0].contains("Could not find some:unresolvable-lib:0.1"));

    assertModuleLibDep("project_test", depName, depJar.getUrl());
    assertModuleLibDepScope("project_test", depName, DependencyScope.COMPILE);
  }

  @Test
  public void testSourceSetOutputDirsAsRuntimeDependencies() throws Exception {
    importProject(
      "apply plugin: 'java'\n" +
      "sourceSets.main.output.dir file(\"$buildDir/generated-resources/main\")"
    );

    assertModules("project", "project_main", "project_test");
    final String path = pathFromBasedir("build/generated-resources/main");
    final String depName = PathUtil.toPresentableUrl(path);
    assertModuleLibDep("project_main", depName, "file://" + path);
    assertModuleLibDepScope("project_main", depName, DependencyScope.RUNTIME);
  }

  @Test
  public void testProjectArtifactDependencyInTestAndArchivesConfigurations() throws Exception {
    createSettingsFile("include 'api', 'impl' ");

    importProject(
      "allprojects {\n" +
      "  apply plugin: 'java'\n" +
      "}\n" +
      "\n" +
      "project(\"api\") {\n" +
      "  configurations {\n" +
      "    tests\n" +
      "  }\n" +
      "  task testJar(type: Jar, dependsOn: testClasses, description: \"archive the testClasses\") {\n" +
      "    baseName = \"${project.archivesBaseName}-tests\"\n" +
      "    classifier = \"tests\"\n" +
      "    from sourceSets.test.output\n" +
      "  }\n" +
      "  artifacts {\n" +
      "    tests testJar\n" +
      "    archives testJar\n" +
      "  }\n" +
      "}\n" +
      "project(\"impl\") {\n" +
      "  dependencies {\n" +
      "    testCompile  project(path: ':api', configuration: 'tests')\n" +
      "  }\n" +
      "}\n"
    );

    assertModules("project", "project_main", "project_test", "api", "api_main", "api_test", "impl", "impl_main", "impl_test");

    assertModuleModuleDepScope("project_test", "project_main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("api_test", "api_main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("impl_test", "impl_main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("impl_test", "api_test", DependencyScope.COMPILE);
  }

  @Test
  public void testCompileAndRuntimeConfigurationsTransitiveDependencyMerge() throws Exception {
    createSettingsFile("include 'project1'\n" +
                       "include 'project2'\n" +
                       "include 'project-tests'");

    importProject(
      "subprojects {\n" +
      "  apply plugin: \"java\"\n" +
      "}\n" +
      "\n" +
      "project(\":project1\") {\n" +
      "  dependencies {\n" +
      "      compile 'org.apache.geronimo.specs:geronimo-jms_1.1_spec:1.0'\n" +
      "  }\n" +
      "}\n" +
      "\n" +
      "project(\":project2\") {\n" +
      "  dependencies {\n" +
      "      runtime 'org.apache.geronimo.specs:geronimo-jms_1.1_spec:1.1.1'\n" +
      "  }\n" +
      "}\n" +
      "\n" +
      "project(\":project-tests\") {\n" +
      "  dependencies {\n" +
      "      compile project(':project1')\n" +
      "      runtime project(':project2')\n" +
      "      compile 'junit:junit:4.11'\n" +
      "  }\n" +
      "}\n"
    );

    assertModules("project", "project1", "project1_main", "project1_test", "project2", "project2_main", "project2_test", "project-tests", "project-tests_main", "project-tests_test");

    assertModuleModuleDepScope("project-tests_main", "project1_main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project-tests_main", "project2_main", DependencyScope.RUNTIME);
    assertModuleLibDepScope("project-tests_main", "Gradle: org.apache.geronimo.specs:geronimo-jms_1.1_spec:1.0", DependencyScope.COMPILE);
    assertModuleLibDepScope("project-tests_main", "Gradle: org.apache.geronimo.specs:geronimo-jms_1.1_spec:1.1.1", DependencyScope.RUNTIME);
  }
}
