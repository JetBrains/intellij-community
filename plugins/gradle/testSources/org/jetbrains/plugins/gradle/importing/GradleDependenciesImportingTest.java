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

import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.util.GradleVersion;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.*;
import static com.intellij.openapi.util.text.StringUtil.*;
import static com.intellij.util.containers.ContainerUtil.ar;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.getSourceSetName;

/**
 * @author Vladislav.Soroka
 * @since 6/30/2014
 */
@SuppressWarnings("JUnit4AnnotatedMethodInJUnit3TestCase")
public class GradleDependenciesImportingTest extends GradleImportingTestCase {

  @Override
  protected void importProject(@NonNls @Language("Groovy") String config) throws IOException {
    config += "\nallprojects {\n" +
              "  if(convention.findPlugin(JavaPluginConvention)) {\n" +
              "    sourceSets.each { SourceSet sourceSet ->\n" +
              "      tasks.create(name: 'print'+ sourceSet.name.capitalize() +'CompileDependencies') {\n" +
              "        doLast { println sourceSet.compileClasspath.files.collect {it.name}.join(' ') }\n" +
              "      }\n" +
              "    }\n" +
              "  }\n" +
              "}\n";
    super.importProject(config);
  }

  protected void assertCompileClasspathOrdering(String moduleName) {
    Module module = getModule(moduleName);
    String sourceSetName = getSourceSetName(module);
    assertNotNull("Can not find the sourceSet for the module", sourceSetName);

    ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();
    settings.setExternalProjectPath(getExternalProjectPath(module));
    String id = getExternalProjectId(module);
    String gradlePath = id.startsWith(":") ? trimEnd(id, sourceSetName) : "";
    settings.setTaskNames(Collections.singletonList(gradlePath + ":print" + capitalize(sourceSetName) + "CompileDependencies"));
    settings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.getId());
    settings.setScriptParameters("--quiet");
    ExternalSystemProgressNotificationManager notificationManager =
      ServiceManager.getService(ExternalSystemProgressNotificationManager.class);
    StringBuilder gradleClasspath = new StringBuilder();
    ExternalSystemTaskNotificationListenerAdapter listener = new ExternalSystemTaskNotificationListenerAdapter() {
      @Override
      public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
        gradleClasspath.append(text);
      }
    };
    notificationManager.addNotificationListener(listener);
    try {
      ExternalSystemUtil.runTask(settings, DefaultRunExecutor.EXECUTOR_ID, myProject, GradleConstants.SYSTEM_ID, null,
                                 ProgressExecutionMode.NO_PROGRESS_SYNC);
    }
    finally {
      notificationManager.removeNotificationListener(listener);
    }

    List<String> ideClasspath = ContainerUtil.newArrayList();
    ModuleRootManager.getInstance(module).orderEntries().withoutSdk().withoutModuleSourceEntries().compileOnly().productionOnly().forEach(
      entry -> {
        if (entry instanceof ModuleOrderEntry) {
          Module moduleDep = ((ModuleOrderEntry)entry).getModule();
          String sourceSetDepName = getSourceSetName(moduleDep);
          // for simplicity, only project dependency on 'default' configuration allowed here
          assert sourceSetDepName != "main";

          String gradleProjectDepName = trimStart(trimEnd(getExternalProjectId(moduleDep), ":main"), ":");
          String version = getExternalProjectVersion(moduleDep);
          version = "unspecified".equals(version) ? "" : "-" + version;
          ideClasspath.add(gradleProjectDepName + version + ".jar");
        }
        else {
          ideClasspath.add(entry.getFiles(OrderRootType.CLASSES)[0].getName());
        }
        return true;
      });

    assertEquals(join(ideClasspath, " "), gradleClasspath.toString().trim());
  }

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

    assertCompileClasspathOrdering("project_main");

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project", "api", "impl");

    if(GradleVersion.version(gradleVersion).compareTo(GradleVersion.version("1.12")) < 0) {
      assertModuleModuleDepScope("project", "impl", DependencyScope.RUNTIME);
    } else {
      assertModuleModuleDepScope("project", "impl", DependencyScope.RUNTIME, DependencyScope.TEST);
    }

    assertModuleLibDepScope("project", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.TEST);
    assertModuleLibDepScope("project", "Gradle: junit:junit:4.11", DependencyScope.TEST);
  }

  @Test
  @TargetVersions("2.0+")
  public void testTransitiveNonTransitiveDependencyScopeMerge() throws Exception {
    createSettingsFile("include 'project1'\n" +
                       "include 'project2'\n");

    importProject(
      "project(':project1') {\n" +
      "  apply plugin: 'java'\n" +
      "  dependencies {\n" +
      "    compile 'junit:junit:4.11'\n" +
      "  }\n" +
      "}\n" +
      "\n" +
      "project(':project2') {\n" +
      "  apply plugin: 'java'\n" +
      "  dependencies.ext.strict = { projectPath ->\n" +
      "    dependencies.compile dependencies.project(path: projectPath, transitive: false)\n" +
      "    dependencies.runtime dependencies.project(path: projectPath, transitive: true)\n" +
      "    dependencies.testRuntime dependencies.project(path: projectPath, transitive: true)\n" +
      "  }\n" +
      "\n" +
      "  dependencies {\n" +
      "    strict ':project1'\n" +
      "  }\n" +
      "}\n"
    );

    assertModules("project", "project1", "project1_main", "project1_test", "project2", "project2_main", "project2_test");

    assertModuleModuleDeps("project2_main", "project1_main");
    assertModuleModuleDepScope("project2_main", "project1_main", DependencyScope.COMPILE);
    assertModuleLibDepScope("project2_main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.RUNTIME);
    assertModuleLibDepScope("project2_main", "Gradle: junit:junit:4.11", DependencyScope.RUNTIME);

    if(GradleVersion.version(gradleVersion).compareTo(GradleVersion.version("2.5")) >= 0) {
      boolean gradleOlderThen_3_4 = isGradleOlderThen_3_4();
      importProjectUsingSingeModulePerGradleProject();
      assertModules("project", "project1", "project2");
      assertMergedModuleCompileModuleDepScope("project2", "project1");
      assertModuleLibDepScope("project2", "Gradle: org.hamcrest:hamcrest-core:1.3",
                              gradleOlderThen_3_4 ? ar(DependencyScope.RUNTIME)
                                                  : ar(DependencyScope.RUNTIME, DependencyScope.TEST));
      assertModuleLibDepScope("project2", "Gradle: junit:junit:4.11",
                              gradleOlderThen_3_4 ? ar(DependencyScope.RUNTIME)
                                                  : ar(DependencyScope.RUNTIME, DependencyScope.TEST));
    }
  }

  @Test
  @TargetVersions("2.0+")
  public void testProvidedDependencyScopeMerge() throws Exception {
    createSettingsFile("include 'web'\n" +
                       "include 'user'");

    importProject(
      "subprojects {\n" +
      "  apply plugin: 'java'\n" +
      "  configurations {\n" +
      "    provided\n" +
      "  }\n" +
      "}\n" +
      "\n" +
      "project(':web') {\n" +
      "  dependencies {\n" +
      "    provided 'junit:junit:4.11'\n" +
      "  }\n" +
      "}\n" +
      "project(':user') {\n" +
      "  apply plugin: 'war'\n" +
      "  dependencies {\n" +
      "    compile project(':web')\n" +
      "    providedCompile project(path: ':web', configuration: 'provided')\n" +
      "  }\n" +
      "}"
    );

    assertModules("project", "web", "web_main", "web_test", "user", "user_main", "user_test");

    assertModuleLibDeps("web");
    assertModuleLibDeps("web_main");
    assertModuleLibDeps("web_test");

    assertModuleModuleDeps("user_main", "web_main");
    assertModuleModuleDepScope("user_main", "web_main", DependencyScope.COMPILE);
    assertModuleLibDepScope("user_main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.PROVIDED);
    assertModuleLibDepScope("user_main", "Gradle: junit:junit:4.11", DependencyScope.PROVIDED);

    createProjectSubDirs("web", "user");
    assertCompileClasspathOrdering("user_main");
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

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project");

    assertModuleLibDep("project", depName, depJar.getUrl());
    assertMergedModuleCompileLibDepScope("project", depName);

    assertModuleLibDep("project", "Gradle: dep:dep:1.0:tests", depTestsJar.getUrl());
    assertModuleLibDepScope("project", "Gradle: dep:dep:1.0:tests", DependencyScope.TEST);

    assertModuleLibDep("project", "Gradle: dep:dep:1.0:someExt", depNonJar.getUrl());
    if (isGradleOlderThen_3_4()) {
      assertModuleLibDepScope("project", "Gradle: dep:dep:1.0:someExt", DependencyScope.RUNTIME);
    } else {
      assertModuleLibDepScope("project", "Gradle: dep:dep:1.0:someExt", DependencyScope.RUNTIME, DependencyScope.TEST);
    }
  }


  @Test
  public void testGlobalFileDepsImportedAsProjectLibraries() throws  Exception {
    final VirtualFile depJar = createProjectJarSubFile("lib/dep.jar");
    final VirtualFile dep2Jar = createProjectJarSubFile("lib_other/dep.jar");
    createSettingsFile("include 'p1'\n" +
                       "include 'p2'");

    importProjectUsingSingeModulePerGradleProject("allprojects {\n" +
                  "apply plugin: 'java'\n" +
                  "  dependencies {\n" +
                  "     compile rootProject.files('lib/dep.jar', 'lib_other/dep.jar')\n" +
                  "  }\n" +
                  "}");

    assertModules("project", "p1", "p2");
    Set<Library> libs = new HashSet<>();
    final List<LibraryOrderEntry> moduleLibDeps = getModuleLibDeps("p1", "Gradle: dep");
    moduleLibDeps.addAll(getModuleLibDeps("p1", "Gradle: dep_1"));
    moduleLibDeps.addAll(getModuleLibDeps("p2", "Gradle: dep"));
    moduleLibDeps.addAll(getModuleLibDeps("p2", "Gradle: dep_1"));
    for (LibraryOrderEntry libDep : moduleLibDeps) {
      libs.add(libDep.getLibrary());
      assertFalse("Dependency be project level: " + libDep.toString(), libDep.isModuleLevel());
    }

    assertProjectLibraries("Gradle: dep", "Gradle: dep_1");
    assertEquals("No duplicates of libraries are expected", 2, libs.size());
    assertContain(libs.stream().map(l -> l.getUrls(OrderRootType.CLASSES)[0]).collect(Collectors.toList()),
                  depJar.getUrl(), dep2Jar.getUrl());
  }

  @Test
  public void testLocalFileDepsImportedAsModuleLibraries() throws  Exception {
    final VirtualFile depP1Jar = createProjectJarSubFile("p1/lib/dep.jar");
    final VirtualFile depP2Jar = createProjectJarSubFile("p2/lib/dep.jar");
    createSettingsFile("include 'p1'\n" +
                       "include 'p2'");

    importProjectUsingSingeModulePerGradleProject("allprojects { p ->\n" +
                                                  "apply plugin: 'java'\n" +
                                                  "  dependencies {\n" +
                                                  "     compile p.files('lib/dep.jar')\n" +
                                                  "  }\n" +
                                                  "}");

    assertModules("project", "p1", "p2");

    final List<LibraryOrderEntry> moduleLibDepsP1 = getModuleLibDeps("p1", "Gradle: dep");
    final boolean isGradleNewerThen_2_4 = GradleVersion.version(gradleVersion).getBaseVersion().compareTo(GradleVersion.version("2.4")) > 0;
    for (LibraryOrderEntry libDep : moduleLibDepsP1) {
      assertEquals("Dependency must be " + (isGradleNewerThen_2_4 ? "module" : "project") + " level: " + libDep.toString(), isGradleNewerThen_2_4, libDep.isModuleLevel());
      assertEquals("Wrong library dependency", depP1Jar.getUrl(), libDep.getLibrary().getUrls(OrderRootType.CLASSES)[0]);
    }

    final List<LibraryOrderEntry> moduleLibDepsP2 = getModuleLibDeps("p2", "Gradle: dep");
    for (LibraryOrderEntry libDep : moduleLibDepsP2) {
      assertEquals("Dependency must be " + (isGradleNewerThen_2_4 ? "module" : "project") + " level: " + libDep.toString(), isGradleNewerThen_2_4, libDep.isModuleLevel());
      assertEquals("Wrong library dependency", depP2Jar.getUrl(), libDep.getLibrary().getUrls(OrderRootType.CLASSES)[0]);
    }
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

    List<LibraryOrderEntry> unresolvableDep = getModuleLibDeps("project_main", "Gradle: some:unresolvable-lib:0.1");
    assertEquals(1, unresolvableDep.size());
    LibraryOrderEntry unresolvableEntry = unresolvableDep.iterator().next();
    assertFalse(unresolvableEntry.isModuleLevel());
    assertEquals(DependencyScope.COMPILE, unresolvableEntry.getScope());
    String[] unresolvableEntryUrls = unresolvableEntry.getUrls(OrderRootType.CLASSES);
    assertEquals(1, unresolvableEntryUrls.length);
    assertTrue(unresolvableEntryUrls[0].contains("Could not find some:unresolvable-lib:0.1"));

    assertModuleLibDep("project_test", depName, depJar.getUrl());
    assertModuleLibDepScope("project_test", depName, DependencyScope.COMPILE);

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project");

    assertModuleLibDep("project", depName, depJar.getUrl());
    assertMergedModuleCompileLibDepScope("project", depName);
    assertMergedModuleCompileLibDepScope("project", "Gradle: unresolvable-lib-0.1:1");

    unresolvableDep = getModuleLibDeps("project", "Gradle: unresolvable-lib-0.1:1");
    if (isGradleOlderThen_3_4()) {
      assertEquals(1, unresolvableDep.size());
      unresolvableEntry = unresolvableDep.iterator().next();
      assertTrue(unresolvableEntry.isModuleLevel());
      assertEquals(DependencyScope.COMPILE, unresolvableEntry.getScope());
      unresolvableEntryUrls = unresolvableEntry.getUrls(OrderRootType.CLASSES);
      assertEquals(0, unresolvableEntryUrls.length);
    }
    else {
      assertEquals(3, unresolvableDep.size());
      unresolvableEntry = unresolvableDep.iterator().next();
      assertTrue(unresolvableEntry.isModuleLevel());
      unresolvableEntryUrls = unresolvableEntry.getUrls(OrderRootType.CLASSES);
      assertEquals(0, unresolvableEntryUrls.length);
    }
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
  public void testSourceSetOutputDirsAsRuntimeDependenciesOfDependantModules() throws Exception {
    createSettingsFile("include 'projectA', 'projectB', 'projectC' ");
    importProject(
      "project(':projectA') {\n" +
      "  apply plugin: 'java'\n" +
      "  sourceSets.main.output.dir file(\"$buildDir/generated-resources/main\")\n" +
      "}\n" +
      "project(':projectB') {\n" +
      "  apply plugin: 'java'\n" +
      "  dependencies {\n" +
      "    compile project(':projectA')\n" +
      "  }\n" +
      "}\n" +
      "project(':projectC') {\n" +
      "  apply plugin: 'java'\n" +
      "  dependencies {\n" +
      "    runtime project(':projectB')\n" +
      "  }\n" +
      "}"
    );

    assertModules("project", "projectA", "projectA_main", "projectA_test", "projectB", "projectB_main", "projectB_test", "projectC", "projectC_main", "projectC_test");

    assertModuleModuleDepScope("projectB_main", "projectA_main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("projectC_main", "projectA_main", DependencyScope.RUNTIME);
    assertModuleModuleDepScope("projectC_main", "projectB_main", DependencyScope.RUNTIME);

    final String path = pathFromBasedir("projectA/build/generated-resources/main");
    final String classesPath = "file://" + path;
    final String depName = PathUtil.toPresentableUrl(path);
    assertModuleLibDep("projectA_main", depName, classesPath);
    assertModuleLibDepScope("projectA_main", depName, DependencyScope.RUNTIME);
    assertModuleLibDep("projectB_main", depName, classesPath);
    assertModuleLibDepScope("projectB_main", depName, DependencyScope.COMPILE);
    assertModuleLibDep("projectC_main", depName, classesPath);
    assertModuleLibDepScope("projectC_main", depName, DependencyScope.RUNTIME);
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

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project","api", "impl");

    assertModuleModuleDepScope("impl", "api", DependencyScope.TEST);
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

    createProjectSubDirs("project1", "project2", "project-tests");
    assertCompileClasspathOrdering("project-tests_main");

    importProjectUsingSingeModulePerGradleProject();

    assertMergedModuleCompileModuleDepScope("project-tests", "project1");

    boolean gradleOlderThen_3_4 = isGradleOlderThen_3_4();
    if (gradleOlderThen_3_4) {
      assertModuleModuleDepScope("project-tests", "project2", DependencyScope.RUNTIME);
    }
    else {
      assertModuleModuleDepScope("project-tests", "project2", DependencyScope.RUNTIME, DependencyScope.TEST);
    }
    if(GradleVersion.version(gradleVersion).compareTo(GradleVersion.version("2.0")) > 0) {
      assertModuleLibDepScope("project-tests", "Gradle: org.apache.geronimo.specs:geronimo-jms_1.1_spec:1.0",
                              gradleOlderThen_3_4 ? ar(DependencyScope.COMPILE) : ar(DependencyScope.PROVIDED, DependencyScope.TEST));
      assertModuleLibDepScope("project-tests", "Gradle: org.apache.geronimo.specs:geronimo-jms_1.1_spec:1.1.1",
                              gradleOlderThen_3_4 ? ar(DependencyScope.RUNTIME) : ar(DependencyScope.RUNTIME, DependencyScope.TEST));
    }
  }

  @Test
  public void testNonDefaultProjectConfigurationDependency() throws Exception {
    createSettingsFile("include 'project1'\n" +
                       "include 'project2'\n");

    importProject(
      "project(':project1') {\n" +
      "  configurations {\n" +
      "    myConf {\n" +
      "      description = 'My Conf'\n" +
      "      transitive = true\n" +
      "    }\n" +
      "  }\n" +
      "  dependencies {\n" +
      "    myConf 'junit:junit:4.11'\n" +
      "  }\n" +
      "}\n" +
      "\n" +
      "project(':project2') {\n" +
      "  apply plugin: 'java'\n" +
      "  dependencies {\n" +
      "    compile project(path: ':project1', configuration: 'myConf')\n" +
      "  }\n" +
      "}\n"
    );

    assertModules("project", "project1", "project2", "project2_main", "project2_test");

    assertModuleModuleDeps("project2_main");
    assertModuleLibDepScope("project2_main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleLibDepScope("project2_main", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project", "project1", "project2");
    assertMergedModuleCompileModuleDepScope("project2", "project1");
    if(GradleVersion.version(gradleVersion).compareTo(GradleVersion.version("2.0")) > 0) {
      assertMergedModuleCompileLibDepScope("project2", "Gradle: org.hamcrest:hamcrest-core:1.3");
      assertMergedModuleCompileLibDepScope("project2", "Gradle: junit:junit:4.11");
    }
  }

  @Test
  public void testNonDefaultProjectConfigurationDependencyWithMultipleArtifacts() throws Exception {
    createSettingsFile("include 'project1'\n" +
                       "include 'project2'\n");

    importProject(
      "project(':project1') {\n" +
      "  apply plugin: 'java'\n" +
      "  configurations {\n" +
      "    tests.extendsFrom testRuntime\n" +
      "  }\n" +
      "  task testJar(type: Jar) {\n" +
      "    classifier 'test'\n" +
      "    from project.sourceSets.test.output\n" +
      "  }\n" +
      "\n" +
      "  artifacts {\n" +
      "    tests testJar\n" +
      "    archives testJar\n" +
      "  }\n" +
      "\n" +
      "  dependencies {\n" +
      "    testCompile 'junit:junit:4.11'\n" +
      "  }\n" +
      "}\n" +
      "\n" +
      "project(':project2') {\n" +
      "  apply plugin: 'java'\n" +
      "  dependencies {\n" +
      "    testCompile project(path: ':project1', configuration: 'tests')\n" +
      "  }\n" +
      "}\n"
    );

    assertModules("project", "project1", "project1_main", "project1_test", "project2", "project2_main", "project2_test");

    assertModuleModuleDeps("project1_main");
    assertModuleLibDeps("project1_main");
    assertModuleLibDepScope("project1_test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleLibDepScope("project1_test", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);

    assertModuleModuleDeps("project2_main");
    assertModuleLibDeps("project2_main");
    assertModuleLibDepScope("project2_test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleLibDepScope("project2_test", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);

    assertModuleModuleDeps("project2_test", "project2_main", "project1_main", "project1_test");
    assertModuleModuleDepScope("project2_test", "project2_main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project2_test", "project1_main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project2_test", "project1_test", DependencyScope.COMPILE);
  }


  @Test
  @TargetVersions("2.0+")
  public void testTestModuleDependencyAsArtifactFromTestSourceSetOutput() throws Exception {
    createSettingsFile("include 'project1'\n" +
                       "include 'project2'\n");

    importProject(
      "project(':project1') {\n" +
      "  apply plugin: 'java'\n" +
      "  configurations {\n" +
      "    testArtifacts\n" +
      "  }\n" +
      "\n" +
      "  task testJar(type: Jar) {\n" +
      "    classifier = 'tests'\n" +
      "    from sourceSets.test.output\n" +
      "  }\n" +
      "\n" +
      "  artifacts {\n" +
      "    testArtifacts testJar\n" +
      "  }\n" +
      "}\n" +
      "\n" +
      "project(':project2') {\n" +
      "  apply plugin: 'java'\n" +
      "  dependencies {\n" +
      "    testCompile project(path: ':project1', configuration: 'testArtifacts')\n" +
      "  }\n" +
      "}\n"
    );

    assertModules("project", "project1", "project1_main", "project1_test", "project2", "project2_main", "project2_test");

    assertModuleModuleDeps("project2_main");
    assertModuleModuleDeps("project2_test", "project2_main", "project1_test");

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project", "project1", "project2");
    assertModuleModuleDeps("project2", "project1");
  }

  @Test
  @TargetVersions("2.0+")
  public void testTestModuleDependencyAsArtifactFromTestSourceSetOutput2() throws Exception {
    createSettingsFile("include 'project1'\n" +
                       "include 'project2'\n");

    importProject(
      "project(':project1') {\n" +
      "  apply plugin: 'java'\n" +
      "  configurations {\n" +
      "    testArtifacts\n" +
      "  }\n" +
      "\n" +
      "  task testJar(type: Jar) {\n" +
      "    classifier = 'tests'\n" +
      "    from sourceSets.test.output\n" +
      "  }\n" +
      "\n" +
      "  artifacts {\n" +
      "    testArtifacts testJar\n" +
      "  }\n" +
      "}\n" +
      "\n" +
      "project(':project2') {\n" +
      "  apply plugin: 'java'\n" +
      "  dependencies {\n" +
      "    compile project(path: ':project1')\n" +
      "    testCompile project(path: ':project1', configuration: 'testArtifacts')\n" +
      "  }\n" +
      "}\n"
    );

    assertModules("project", "project1", "project1_main", "project1_test", "project2", "project2_main", "project2_test");

    assertModuleModuleDeps("project2_main", "project1_main");
    assertModuleModuleDeps("project2_test", "project2_main", "project1_main", "project1_test");

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project", "project1", "project2");
  }

  @Test
  @TargetVersions("2.0+")
  public void testTestModuleDependencyAsArtifactFromTestSourceSetOutput3() throws Exception {
    createSettingsFile("include 'project1'\n" +
                       "include 'project2'\n");

    importProject(
      "allprojects {\n" +
      "  apply plugin: 'idea'\n" +
      "  idea {\n" +
      "    module {\n" +
      "      inheritOutputDirs = false\n" +
      "      outputDir = file(\"buildIdea/main\")\n" +
      "      testOutputDir = file(\"buildIdea/test\")\n" +
      "      excludeDirs += file('buildIdea')\n" +
      "    }\n" +
      "  }\n" +
      "}\n" +
      "\n" +
      "project(':project1') {\n" +
      "  apply plugin: 'java'\n" +
      "  configurations {\n" +
      "    testArtifacts\n" +
      "  }\n" +
      "\n" +
      "  task testJar(type: Jar) {\n" +
      "    classifier = 'tests'\n" +
      "    from sourceSets.test.output\n" +
      "  }\n" +
      "\n" +
      "  artifacts {\n" +
      "    testArtifacts testJar\n" +
      "  }\n" +
      "}\n" +
      "\n" +
      "project(':project2') {\n" +
      "  apply plugin: 'java'\n" +
      "  dependencies {\n" +
      "    testCompile project(path: ':project1', configuration: 'testArtifacts')\n" +
      "  }\n" +
      "}\n"
    );

    assertModules("project", "project1", "project1_main", "project1_test", "project2", "project2_main", "project2_test");

    assertModuleOutput("project1_main", getProjectPath() + "/project1/buildIdea/main", "");
    assertModuleOutput("project1_test", "", getProjectPath() + "/project1/buildIdea/test");

    assertModuleOutput("project2_main", getProjectPath() + "/project2/buildIdea/main", "");
    assertModuleOutput("project2_test", "", getProjectPath() + "/project2/buildIdea/test");

    assertModuleModuleDeps("project2_main");
    assertModuleModuleDeps("project2_test", "project2_main", "project1_test");

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project", "project1", "project2");
    assertModuleModuleDeps("project2", "project1");
  }

  @Test
  @TargetVersions("2.6+")
  public void testProjectSubstitutions() throws Exception {
    createSettingsFile("include 'core'\n" +
                       "include 'service'\n" +
                       "include 'util'\n");

    importProject(
      "subprojects {\n" +
      "  apply plugin: 'java'\n" +
      "  configurations.all {\n" +
      "    resolutionStrategy.dependencySubstitution {\n" +
      "      substitute module('mygroup:core') with project(':core')\n" +
      "      substitute project(':util') with module('junit:junit:4.11')\n" +
      "    }\n" +
      "  }\n" +
      "}\n" +
      "\n" +
      "project(':core') {\n" +
      "  apply plugin: 'java'\n" +
      "  dependencies {\n" +
      "    compile project(':util')\n" +
      "  }\n" +
      "}\n" +
      "\n" +
      "project(':service') {\n" +
      "  dependencies {\n" +
      "    compile 'mygroup:core:latest.release'\n" +
      "  }\n" +
      "}\n"
    );

    assertModules("project", "core", "core_main", "core_test", "service", "service_main", "service_test", "util", "util_main", "util_test");

    assertModuleModuleDeps("service_main", "core_main");
    assertModuleModuleDepScope("service_main", "core_main", DependencyScope.COMPILE);
    assertModuleLibDepScope("service_main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleLibDepScope("service_main", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project", "core", "service", "util");

    assertMergedModuleCompileModuleDepScope("service", "core");
    assertMergedModuleCompileLibDepScope("service", "Gradle: org.hamcrest:hamcrest-core:1.3");
    assertMergedModuleCompileLibDepScope("service", "Gradle: junit:junit:4.11");
  }

  @Test
  @TargetVersions("2.6+")
  public void testProjectSubstitutionsWithTransitiveDeps() throws Exception {
    createSettingsFile("include 'modA'\n" +
                       "include 'modB'\n" +
                       "include 'app'\n");

    importProject(
      "subprojects {\n" +
      "  apply plugin: 'java'\n" +
      "  version '1.0.0'\n" +
      "}\n" +
      "project(':app') {\n" +
      "  dependencies {\n" +
      "    runtime 'org.hamcrest:hamcrest-core:1.3'\n" +
      "    testCompile 'project:modA:1.0.0'\n" +
      "  }\n" +
      "\n" +
      "  configurations.all {\n" +
      "    resolutionStrategy.dependencySubstitution {\n" +
      "      substitute module('project:modA:1.0.0') with project(':modA')\n" +
      "      substitute module('project:modB:1.0.0') with project(':cmodB')\n" +
      "    }\n" +
      "  }\n" +
      "}\n" +
      "project(':modA') {\n" +
      "  dependencies {\n" +
      "    compile project(':modB')\n" +
      "  }\n" +
      "}\n" +
      "project(':modB') {\n" +
      "  dependencies {\n" +
      "    compile 'org.hamcrest:hamcrest-core:1.3'\n" +
      "  }\n" +
      "}"
    );

    assertModules("project", "app", "app_main", "app_test", "modA", "modA_main", "modA_test", "modB", "modB_main", "modB_test");

    assertModuleLibDepScope("app_main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.RUNTIME);
    assertModuleModuleDeps("app_main");
    assertModuleLibDepScope("app_test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleModuleDeps("app_test", "app_main", "modA_main", "modB_main");

    assertModuleLibDepScope("modA_main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleModuleDeps("modA_main", "modB_main");
    assertModuleLibDepScope("modA_test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleModuleDeps("modA_test", "modA_main", "modB_main");

    assertModuleLibDepScope("modB_main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleModuleDeps("modB_main");
    assertModuleLibDepScope("modB_test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleModuleDeps("modB_test", "modB_main");

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project", "app", "modA", "modB");

    assertModuleModuleDeps("app", "modA", "modB");
    assertModuleModuleDepScope("app", "modA", DependencyScope.TEST);
    assertModuleModuleDepScope("app", "modB", DependencyScope.TEST);
    assertModuleLibDepScope("app", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.RUNTIME, DependencyScope.TEST);

    assertMergedModuleCompileModuleDepScope("modA", "modB");
    assertMergedModuleCompileLibDepScope("modA", "Gradle: org.hamcrest:hamcrest-core:1.3");

    assertModuleModuleDeps("modB");
    assertMergedModuleCompileLibDepScope("modB", "Gradle: org.hamcrest:hamcrest-core:1.3");
  }

  @Test
  @TargetVersions("2.12+")
  public void testCompileOnlyScope() throws Exception {
    importProject(
      "apply plugin: 'java'\n" +
      "dependencies {\n" +
      "  compileOnly 'junit:junit:4.11'\n" +
      "}"
    );

    assertModules("project", "project_main", "project_test");
    assertModuleModuleDepScope("project_test", "project_main", DependencyScope.COMPILE);

    assertModuleLibDepScope("project_main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.PROVIDED);
    assertModuleLibDepScope("project_main", "Gradle: junit:junit:4.11", DependencyScope.PROVIDED);

    assertModuleLibDeps("project_test");

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project");

    assertModuleLibDepScope("project", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.PROVIDED);
    assertModuleLibDepScope("project", "Gradle: junit:junit:4.11", DependencyScope.PROVIDED);
  }

  @Test
  @TargetVersions("2.12+")
  public void testCompileOnlyAndRuntimeScope() throws Exception {
    importProject(
      "apply plugin: 'java'\n" +
      "dependencies {\n" +
      "  runtime 'org.hamcrest:hamcrest-core:1.3'\n" +
      "  compileOnly 'org.hamcrest:hamcrest-core:1.3'\n" +
      "}"
    );

    assertModules("project", "project_main", "project_test");
    assertModuleModuleDepScope("project_test", "project_main", DependencyScope.COMPILE);

    assertModuleLibDepScope("project_main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleLibDepScope("project_test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.RUNTIME);

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project");

    if (isGradleOlderThen_3_4()) {
      assertModuleLibDepScope("project", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.PROVIDED, DependencyScope.RUNTIME);
    } else {
      assertModuleLibDepScope("project", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.TEST, DependencyScope.PROVIDED, DependencyScope.RUNTIME);
    }
  }

  @Test
  @TargetVersions("2.12+")
  public void testCompileOnlyAndCompileScope() throws Exception {
    createSettingsFile("include 'app'\n");
    importProject(
      "apply plugin: 'java'\n" +
      "dependencies {\n" +
      "  compileOnly project(':app')\n" +
      "  compile 'junit:junit:4.11'\n" +
      "}\n" +
      "project(':app') {\n" +
      "  apply plugin: 'java'\n" +
      "  repositories {\n" +
      "    mavenCentral()\n" +
      "  }\n" +
      "  dependencies {\n" +
      "    compile 'junit:junit:4.11'\n" +
      "  }\n" +
      "}"
    );

    assertModules("project", "project_main", "project_test", "app", "app_main", "app_test");

    assertModuleModuleDepScope("project_main", "app_main", DependencyScope.PROVIDED);
    assertModuleLibDepScope("project_main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleLibDepScope("project_main", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);

    assertModuleModuleDeps("project_test", "project_main");
    assertModuleModuleDepScope("project_test", "project_main", DependencyScope.COMPILE);
    assertModuleLibDepScope("project_test", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);
    assertModuleLibDepScope("project_test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
  }

  @Test
  @TargetVersions("3.4+")
  public void testJavaLibraryPluginConfigurations() throws Exception {
    createSettingsFile("include 'project1'\n" +
                       "include 'project2'\n");

    importProject(
      "project(':project1') {\n" +
      "  apply plugin: 'java'\n" +
      "  dependencies {\n" +
      "    compile project(path: ':project2')\n" +
      "  }\n" +
      "}\n" +
      "\n" +
      "project(':project2') {\n" +
      "  apply plugin: 'java-library'\n" +
      "  dependencies {\n" +
      "    implementation group: 'junit', name: 'junit', version: '4.11'\n" +
      "    api group: 'org.hamcrest', name: 'hamcrest-core', version: '1.3'\n" +
      "  }\n" +
      "\n" +
      "}\n"
    );

    assertModules("project", "project1", "project1_main", "project1_test", "project2", "project2_main", "project2_test");

    assertModuleModuleDepScope("project1_main", "project2_main", DependencyScope.COMPILE);
    assertModuleLibDepScope("project1_main", "Gradle: junit:junit:4.11", DependencyScope.RUNTIME);
    assertModuleLibDepScope("project1_main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);

    assertModuleModuleDepScope("project1_test", "project1_main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project1_test", "project2_main", DependencyScope.COMPILE);
    assertModuleLibDepScope("project1_test", "Gradle: junit:junit:4.11", DependencyScope.RUNTIME);
    assertModuleLibDepScope("project1_test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);

    assertModuleLibDepScope("project2_main", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);
    assertModuleLibDepScope("project2_main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project2_test", "project2_main", DependencyScope.COMPILE);
    assertModuleLibDepScope("project2_test", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);
    assertModuleLibDepScope("project2_test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
  }


  @Test
  @TargetVersions("2.12+")
  public void testNonTransitiveConfiguration() throws Exception {
    importProject(
      "apply plugin: 'java'\n" +
      "configurations {\n" +
      "  compile.transitive = false\n" +
      "}\n" +
      "\n" +
      "dependencies {\n" +
      "  compile 'junit:junit:4.11'\n" +
      "}"
    );

    assertModules("project", "project_main", "project_test");
    assertModuleModuleDepScope("project_test", "project_main", DependencyScope.COMPILE);

    assertModuleLibDepScope("project_main", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);
    assertModuleLibDepScope("project_main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);

    assertModuleLibDepScope("project_test", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);
    assertModuleLibDepScope("project_test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project");

    assertMergedModuleCompileLibDepScope("project", "Gradle: junit:junit:4.11");

    if (isGradleOlderThen_3_4()) {
      assertModuleLibDepScope("project", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.PROVIDED, DependencyScope.RUNTIME);
    }
    else {
      assertModuleLibDepScope("project", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.PROVIDED, DependencyScope.RUNTIME,
                              DependencyScope.TEST);
    }
  }

  @Test
  @TargetVersions("2.0+")
  public void testProvidedTransitiveDependencies() throws Exception {
    createSettingsFile("include 'projectA', 'projectB', 'projectC' ");
    importProject(
      "project(':projectA') {\n" +
      "  apply plugin: 'java'\n" +
      "}\n" +
      "project(':projectB') {\n" +
      "  apply plugin: 'java'\n" +
      "  dependencies {\n" +
      "    compile project(':projectA')\n" +
      "  }\n" +
      "}\n" +
      "project(':projectC') {\n" +
      "  apply plugin: 'war'\n" +
      "  dependencies {\n" +
      "    providedCompile project(':projectB')\n" +
      "  }\n" +
      "}"
    );

    assertModules("project", "projectA", "projectA_main", "projectA_test", "projectB", "projectB_main", "projectB_test", "projectC", "projectC_main", "projectC_test");

    assertModuleModuleDepScope("projectB_main", "projectA_main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("projectC_main", "projectA_main", DependencyScope.PROVIDED);
    assertModuleModuleDepScope("projectC_main", "projectB_main", DependencyScope.PROVIDED);

    createProjectSubDirs("projectA", "projectB", "projectC");
    assertCompileClasspathOrdering("projectC_main");

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project", "projectA", "projectB", "projectC");
    assertMergedModuleCompileModuleDepScope("projectB", "projectA");
    if(GradleVersion.version(gradleVersion).compareTo(GradleVersion.version("2.5")) >= 0) {
      assertModuleModuleDepScope("projectC", "projectA", DependencyScope.PROVIDED);
    }
    assertModuleModuleDepScope("projectC", "projectB", DependencyScope.PROVIDED);
  }

  @Test
  public void testProjectConfigurationDependencyWithDependencyOnTestOutput() throws Exception {
    createSettingsFile("include 'project1'\n" +
                       "include 'project2'\n");

    importProject(
      "project(':project1') {\n" +
      "  apply plugin: 'java'\n" +
      "  configurations {\n" +
      "    testOutput\n" +
      "    testOutput.extendsFrom (testCompile)\n" +
      "  }\n" +
      "\n" +
      "  dependencies {\n" +
      "    testOutput sourceSets.test.output\n" +
      "    testCompile group: 'junit', name: 'junit', version: '4.11'\n" +
      "  }\n" +
      "}\n" +
      "\n" +
      "project(':project2') {\n" +
      "  apply plugin: 'java'\n" +
      "  dependencies {\n" +
      "    compile project(path: ':project1')\n" +
      "\n" +
      "    testCompile group: 'junit', name: 'junit', version: '4.11'\n" +
      "    testCompile project(path: ':project1', configuration: 'testOutput')\n" +
      "  }\n" +
      "\n" +
      "}\n"
    );

    assertModules("project", "project1", "project1_main", "project1_test", "project2", "project2_main", "project2_test");

    assertModuleModuleDepScope("project1_test", "project1_main", DependencyScope.COMPILE);
    assertModuleLibDepScope("project1_test", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);
    assertModuleLibDepScope("project1_test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);

    assertModuleModuleDepScope("project2_main", "project1_main", DependencyScope.COMPILE);

    assertModuleModuleDepScope("project2_test", "project2_main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project2_test", "project1_test", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project2_test", "project1_main", DependencyScope.COMPILE);
    assertModuleLibDepScope("project2_test", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);
    assertModuleLibDepScope("project2_test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
  }

  @TargetVersions("2.0+")
  @Test
  public void testJavadocAndSourcesForDependencyWithMultipleArtifacts() throws Exception {
    createProjectSubFile("repo/depGroup/depArtifact/1.0-SNAPSHOT/ivy-1.0-SNAPSHOT.xml",
                         "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                         "<ivy-module version=\"2.0\" xmlns:m=\"http://ant.apache.org/ivy/maven\">\n" +
                         "  <info organisation=\"depGroup\" module=\"depArtifact\" revision=\"1.0-SNAPSHOT\" status=\"integration\" publication=\"20170817121528\"/>\n" +
                         "  <configurations>\n" +
                         "    <conf name=\"compile\" visibility=\"public\"/>\n" +
                         "    <conf name=\"default\" visibility=\"public\" extends=\"compile\"/>\n" +
                         "    <conf name=\"sources\" visibility=\"public\"/>\n" +
                         "    <conf name=\"javadoc\" visibility=\"public\"/>\n" +
                         "  </configurations>\n" +
                         "  <publications>\n" +
                         "    <artifact name=\"depArtifact\" type=\"jar\" ext=\"jar\" conf=\"compile\"/>\n" +
                         "    <artifact name=\"depArtifact-api\" type=\"javadoc\" ext=\"jar\" conf=\"javadoc\" m:classifier=\"javadoc\"/>\n" +
                         "    <artifact name=\"depArtifact-api\" type=\"source\" ext=\"jar\" conf=\"sources\" m:classifier=\"sources\"/>\n" +
                         "    <artifact name=\"depArtifact\" type=\"source\" ext=\"jar\" conf=\"sources\" m:classifier=\"sources\"/>\n" +
                         "    <artifact name=\"depArtifact\" type=\"javadoc\" ext=\"jar\" conf=\"javadoc\" m:classifier=\"javadoc\"/>\n" +
                         "  </publications>\n" +
                         "  <dependencies/>\n" +
                         "</ivy-module>\n");
    VirtualFile classesJar = createProjectJarSubFile("repo/depGroup/depArtifact/1.0-SNAPSHOT/depArtifact-1.0-SNAPSHOT.jar");
    VirtualFile javadocJar = createProjectJarSubFile("repo/depGroup/depArtifact/1.0-SNAPSHOT/depArtifact-1.0-SNAPSHOT-javadoc.jar");
    VirtualFile sourcesJar = createProjectJarSubFile("repo/depGroup/depArtifact/1.0-SNAPSHOT/depArtifact-1.0-SNAPSHOT-sources.jar");
    createProjectJarSubFile("repo/depGroup/depArtifact/1.0-SNAPSHOT/depArtifact-api-1.0-SNAPSHOT.jar");
    createProjectJarSubFile("repo/depGroup/depArtifact/1.0-SNAPSHOT/depArtifact-api-1.0-SNAPSHOT-javadoc.jar");
    createProjectJarSubFile("repo/depGroup/depArtifact/1.0-SNAPSHOT/depArtifact-api-1.0-SNAPSHOT-sources.jar");

    importProject(
      "apply plugin: 'java'\n" +
      "\n" +
      "repositories {\n" +
      "  ivy { url file('repo') }\n" +
      "}\n" +
      "\n" +
      "dependencies {\n" +
      "  compile 'depGroup:depArtifact:1.0-SNAPSHOT'\n" +
      "}\n" +
      "apply plugin: 'idea'\n" +
      "idea.module.downloadJavadoc true"
    );

    assertModules("project", "project_main", "project_test");

    assertModuleModuleDepScope("project_test", "project_main", DependencyScope.COMPILE);

    final String depName = "Gradle: depGroup:depArtifact:1.0-SNAPSHOT";
    assertModuleLibDep("project_main", depName, classesJar.getUrl(), sourcesJar.getUrl(), javadocJar.getUrl());
    assertModuleLibDepScope("project_main", depName, DependencyScope.COMPILE);
    assertModuleLibDep("project_test", depName, classesJar.getUrl(), sourcesJar.getUrl(), javadocJar.getUrl());
    assertModuleLibDepScope("project_test", depName, DependencyScope.COMPILE);

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project");

    // Gradle built-in models has been fixed since 2.3 version, https://issues.gradle.org/browse/GRADLE-3170
    if(GradleVersion.version(gradleVersion).compareTo(GradleVersion.version("2.3")) >= 0) {
      assertModuleLibDep("project", depName, classesJar.getUrl(), sourcesJar.getUrl(), javadocJar.getUrl());
    }
    assertMergedModuleCompileLibDepScope("project", depName);
  }

  private void assertMergedModuleCompileLibDepScope(String moduleName, String depName) {
    if (isGradleOlderThen_3_4()) {
      assertModuleLibDepScope(moduleName, depName, DependencyScope.COMPILE);
    }
    else {
      assertModuleLibDepScope(moduleName, depName, DependencyScope.PROVIDED, DependencyScope.TEST, DependencyScope.RUNTIME);
    }
  }

  private void assertMergedModuleCompileModuleDepScope(String moduleName, String depName) {
    if (isGradleOlderThen_3_4()) {
      assertModuleModuleDepScope(moduleName, depName, DependencyScope.COMPILE);
    }
    else {
      assertModuleModuleDepScope(moduleName, depName, DependencyScope.PROVIDED, DependencyScope.TEST, DependencyScope.RUNTIME);
    }
  }

  private boolean isGradleOlderThen_3_4() {
    return GradleVersion.version(gradleVersion).getBaseVersion().compareTo(GradleVersion.version("3.4")) < 0;
  }

}
