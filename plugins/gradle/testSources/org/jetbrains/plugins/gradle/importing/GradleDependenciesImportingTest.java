// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing;

import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.gradle.util.GradleVersion;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.GradleManager;
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilderUtil;
import org.jetbrains.plugins.gradle.service.resolve.VersionCatalogsLocator;
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.BiPredicate;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.*;
import static com.intellij.openapi.util.text.StringUtil.*;
import static com.intellij.util.containers.ContainerUtil.ar;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.getSourceSetName;

/**
 * @author Vladislav.Soroka
 */
public class GradleDependenciesImportingTest extends GradleImportingTestCase {

  @Override
  public void importProject(@NonNls @Language("Groovy") String config) throws IOException {
    boolean useConventions = isGradleOlderThan("8.2");
    config += "\n\n def useConventions = " + useConventions + "\n" + """
      allprojects {
        afterEvaluate {
          if((useConventions && convention.findPlugin(JavaPluginConvention) != null)
           || (!useConventions && extensions.findByType(JavaPluginExtension) != null)) {
            sourceSets.each { SourceSet sourceSet ->
              tasks.create(name: 'print'+ sourceSet.name.capitalize() +'CompileDependencies') {
                doLast { println sourceSet.compileClasspath.files.collect {it.name}.join(' ') }
              }
            }
          }
        }
      }
      """;
    super.importProject(config);
  }

  protected void assertCompileClasspathOrdering(String moduleName) {
    assertCompileClasspathOrdering(moduleName, false);
  }

  protected void maybeAssertCompileClasspathOrderingWithEnabledClasspathPackaging(String moduleName) {
    assertCompileClasspathOrdering(moduleName, true);
  }

  private void assertCompileClasspathOrdering(String moduleName, boolean useCompileClasspathPackaging) {
    Module module = getModule(moduleName);
    String sourceSetName = getSourceSetName(module);
    assertNotNull("Can not find the sourceSet for the module", sourceSetName);

    ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();
    settings.setExternalProjectPath(getExternalProjectPath(module));
    String id = getExternalProjectId(module);
    String gradlePath = id.startsWith(":") ? trimEnd(trimEnd(id, sourceSetName), ":") : "";
    settings.setTaskNames(Collections.singletonList(gradlePath + ":print" + capitalize(sourceSetName) + "CompileDependencies"));
    settings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.getId());
    settings.setScriptParameters("--quiet");

    if (useCompileClasspathPackaging) {
      // use jars instead of class folders for everything on the compile classpath
      // https://docs.gradle.org/current/userguide/java_library_plugin.html#sec:java_library_classes_usage
      if (isJavaLibraryPluginSupported() && isGradleOlderThan("5.6.1")) {
        return;
      }
      settings.setVmOptions("-Dorg.gradle.java.compile-classpath-packaging=true");
    }
    ExternalSystemProgressNotificationManager notificationManager =
      ApplicationManager.getApplication().getService(ExternalSystemProgressNotificationManager.class);
    StringBuilder gradleClasspath = new StringBuilder();
    ExternalSystemTaskNotificationListenerAdapter listener = new ExternalSystemTaskNotificationListenerAdapter() {
      @Override
      public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
        if (!stdOut || text.isBlank()) return;
        if (text.contains("Gradle Daemon")) return;
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

    List<String> ideClasspath = new ArrayList<>();
    ModuleRootManager.getInstance(module).orderEntries().withoutSdk().withoutModuleSourceEntries().compileOnly().productionOnly().forEach(
      entry -> {
        if (entry instanceof ModuleOrderEntry) {
          Module moduleDep = ((ModuleOrderEntry)entry).getModule();
          String sourceSetDepName = getSourceSetName(moduleDep);
          // for simplicity, only project dependency on 'default' configuration allowed here
          assert "main".equals(sourceSetDepName);

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
  public void testModuleDependencies() throws IOException {
    createSettingsFile("include 'project1', 'project2'");
    createProjectSubFile("project1/build.gradle", script(it -> it.withJavaPlugin()
      .addImplementationDependency(it.project(":"))));
    createProjectSubFile("project2/build.gradle", script(it -> it.withJavaPlugin()
      .addImplementationDependency(it.project(":project1"))));
    importProject(script(it -> it.withJavaPlugin()));

    assertModules("project", "project.main", "project.test",
                  "project.project1", "project.project1.main", "project.project1.test",
                  "project.project2", "project.project2.main", "project.project2.test");

    assertModuleModuleDeps("project.main");
    assertModuleModuleDeps("project.test", "project.main");
    assertModuleModuleDeps("project.project1.main", "project.main");
    assertModuleModuleDeps("project.project1.test", "project.project1.main", "project.main");
    assertModuleModuleDeps("project.project2.main", "project.project1.main", "project.main");
    assertModuleModuleDeps("project.project2.test", "project.project2.main", "project.project1.main", "project.main");
  }

  @Test
  public void testDependencyScopeMerge() throws Exception {
    createSettingsFile("include 'api', 'impl' ");

    importProject(script(it -> {
      it.allprojects(TestGradleBuildScriptBuilder::withJavaPlugin)
        .addImplementationDependency(it.project(":api"))
        .addTestImplementationDependency(it.project(":impl"))
        .addTestImplementationDependency("junit:junit:4.11")
        .addRuntimeOnlyDependency(it.project(":impl"));
    }));

    assertModules("project", "project.main", "project.test",
                  "project.api", "project.api.main", "project.api.test",
                  "project.impl", "project.impl.main", "project.impl.test");
    assertModuleModuleDepScope("project.test", "project.main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project.api.test", "project.api.main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project.impl.test", "project.impl.main", DependencyScope.COMPILE);

    assertModuleModuleDepScope("project.main", "project.api.main", DependencyScope.COMPILE);

    assertModuleModuleDepScope("project.main", "project.impl.main", DependencyScope.RUNTIME);
    assertModuleModuleDepScope("project.test", "project.impl.main", DependencyScope.COMPILE);

    assertModuleLibDepScope("project.test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleLibDepScope("project.test", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);

    assertCompileClasspathOrdering("project.main");

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project", "project.api", "project.impl");

    if (GradleVersion.version(gradleVersion).compareTo(GradleVersion.version("1.12")) < 0) {
      assertModuleModuleDepScope("project", "project.impl", DependencyScope.RUNTIME);
    }
    else {
      assertModuleModuleDepScope("project", "project.impl", DependencyScope.RUNTIME, DependencyScope.TEST);
    }

    assertModuleLibDepScope("project", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.TEST);
    assertModuleLibDepScope("project", "Gradle: junit:junit:4.11", DependencyScope.TEST);
  }

  @Test
  @TargetVersions(BASE_GRADLE_VERSION)
  public void testSetExternalSourceForExistingLibrary() throws IOException {
    String libraryName = "Gradle: junit:junit:" + GradleBuildScriptBuilderUtil.getJunit4Version();
    WriteAction.runAndWait(() -> {
      LibraryTable.ModifiableModel model = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).getModifiableModel();
      model.createLibrary(libraryName);
      model.commit();
    });

    importProject(createBuildScriptBuilder()
                    .withJavaPlugin()
                    .withJUnit4()
                    .generate());
    assertModules("project", "project.main", "project.test");
    Library library = assertSingleLibraryOrderEntry("project.test", libraryName).getLibrary();
    assertNotNull(library);
    assertNotNull(library.getExternalSource());
  }
  
  @Test
  @TargetVersions("2.0 <=> 6.9")
  public void testTransitiveNonTransitiveDependencyScopeMerge() throws Exception {
    createSettingsFile("""
                         include 'project1'
                         include 'project2'
                         """);

    importProject(
      """
        project(':project1') {
          apply plugin: 'java'
          dependencies {
            compile 'junit:junit:4.11'
          }
        }

        project(':project2') {
          apply plugin: 'java'
          dependencies.ext.strict = { projectPath ->
            dependencies.compile dependencies.project(path: projectPath, transitive: false)
            dependencies.runtime dependencies.project(path: projectPath, transitive: true)
            dependencies.testRuntime dependencies.project(path: projectPath, transitive: true)
          }

          dependencies {
            strict ':project1'
          }
        }
        """
    );

    assertModules("project",
                  "project.project1", "project.project1.main", "project.project1.test",
                  "project.project2", "project.project2.main", "project.project2.test");

    assertModuleModuleDeps("project.project2.main", "project.project1.main");
    assertModuleModuleDepScope("project.project2.main", "project.project1.main", DependencyScope.COMPILE);
    assertModuleLibDepScope("project.project2.main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.RUNTIME);
    assertModuleLibDepScope("project.project2.main", "Gradle: junit:junit:4.11", DependencyScope.RUNTIME);

    if (GradleVersion.version(gradleVersion).compareTo(GradleVersion.version("2.5")) >= 0) {
      boolean gradleOlderThen_3_4 = isGradleOlderThan("3.4");
      importProjectUsingSingeModulePerGradleProject();
      assertModules("project", "project.project1", "project.project2");
      assertMergedModuleCompileModuleDepScope("project.project2", "project.project1");
      assertModuleLibDepScope("project.project2", "Gradle: org.hamcrest:hamcrest-core:1.3",
                              gradleOlderThen_3_4 ? ar(DependencyScope.RUNTIME)
                                                  : ar(DependencyScope.RUNTIME, DependencyScope.TEST));
      assertModuleLibDepScope("project.project2", "Gradle: junit:junit:4.11",
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
      createBuildScriptBuilder()
        .subprojects(it -> {
          it
            .withJavaPlugin()
            .addPostfix("configurations { provided }");
        })
        .project(":web", it -> { it.addDependency("provided", "junit:junit:4.11"); })
        .project(":user", it -> {
          it
            .applyPlugin("war")
            .addImplementationDependency(it.project(":web"))
            .addDependency("providedCompile", it.project(":web", "provided"));
        })
        .generate()
    );

    assertModules("project",
                  "project.web", "project.web.main", "project.web.test",
                  "project.user", "project.user.main", "project.user.test");

    assertModuleLibDeps("project.web");
    assertModuleLibDeps("project.web.main");
    assertModuleLibDeps("project.web.test");

    assertModuleModuleDeps("project.user.main", "project.web.main");
    assertModuleModuleDepScope("project.user.main", "project.web.main", DependencyScope.COMPILE);
    assertModuleLibDepScope("project.user.main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.PROVIDED);
    assertModuleLibDepScope("project.user.main", "Gradle: junit:junit:4.11", DependencyScope.PROVIDED);

    createProjectSubDirs("web", "user");
    assertCompileClasspathOrdering("project.user.main");
  }

  @Test
  public void testCustomSourceSetsDependencies() throws Exception {
    createSettingsFile("include 'api', 'impl' ");

    importProject(script(it -> {
      it.allprojects(TestGradleBuildScriptBuilder::withJavaPlugin)
        .project("impl", p -> {
          p.addPrefix("sourceSets {",
                      "  myCustomSourceSet",
                      "  myAnotherSourceSet",
                      "}")
            .addImplementationDependency(it.code("sourceSets.main.output"), "myCustomSourceSet")
            .addImplementationDependency(it.project(":api"), "myCustomSourceSet")
            .addRuntimeOnlyDependency("junit:junit:4.11", "myCustomSourceSet");
        });
    }));

    assertModules("project", "project.main", "project.test",
                  "project.api", "project.api.main", "project.api.test",
                  "project.impl", "project.impl.main", "project.impl.test",
                  "project.impl.myCustomSourceSet", "project.impl.myAnotherSourceSet");

    assertModuleModuleDepScope("project.test", "project.main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project.api.test", "project.api.main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project.impl.test", "project.impl.main", DependencyScope.COMPILE);

    assertModuleModuleDepScope("project.impl.myCustomSourceSet", "project.impl.main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project.impl.myCustomSourceSet", "project.api.main", DependencyScope.COMPILE);
    assertModuleLibDepScope("project.impl.myCustomSourceSet", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.RUNTIME);
    assertModuleLibDepScope("project.impl.myCustomSourceSet", "Gradle: junit:junit:4.11", DependencyScope.RUNTIME);
  }

  @Test
  public void testDependencyWithDifferentClassifiers() throws Exception {
    final VirtualFile depJar = createProjectJarSubFile("lib/dep/dep/1.0/dep-1.0.jar");
    final VirtualFile depTestsJar = createProjectJarSubFile("lib/dep/dep/1.0/dep-1.0-tests.jar");
    final VirtualFile depNonJar = createProjectSubFile("lib/dep/dep/1.0/dep-1.0.someExt");

    createProjectSubFile("lib/dep/dep/1.0/dep-1.0.pom", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project
        xmlns="http://maven.apache.org/POM/4.0.0"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
        <groupId>dep</groupId>
        <artifactId>dep</artifactId>
        <version>1.0</version>

      </project>
      """);
    importProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .addRepository("maven { url file('lib') }")
        .addImplementationDependency("dep:dep:1.0")
        .addTestImplementationDependency("dep:dep:1.0:tests")
        .addRuntimeOnlyDependency("dep:dep:1.0@someExt")
        .generate()
    );

    assertModules("project", "project.main", "project.test");

    assertModuleModuleDepScope("project.test", "project.main", DependencyScope.COMPILE);

    final String depName = "Gradle: dep:dep:1.0";
    assertModuleLibDep("project.main", depName, depJar.getUrl());
    assertModuleLibDepScope("project.main", depName, DependencyScope.COMPILE);
    assertModuleLibDep("project.test", depName, depJar.getUrl());
    assertModuleLibDepScope("project.test", depName, DependencyScope.COMPILE);

    final boolean isArtifactResolutionQuerySupported = GradleVersion.version(gradleVersion).compareTo(GradleVersion.version("2.0")) >= 0;
    final String depTestsName =
      isArtifactResolutionQuerySupported ? "Gradle: dep:dep:tests:1.0" : PathUtil.toPresentableUrl(depTestsJar.getUrl());
    assertModuleLibDep("project.test", depTestsName, depTestsJar.getUrl());
    assertModuleLibDepScope("project.test", depTestsName, DependencyScope.COMPILE);

    final String depNonJarName =
      isArtifactResolutionQuerySupported ? "Gradle: dep:dep:someExt:1.0" : PathUtil.toPresentableUrl(depNonJar.getUrl());
    assertModuleLibDep("project.main", depNonJarName, depNonJar.getUrl());
    assertModuleLibDepScope("project.main", depNonJarName, DependencyScope.RUNTIME);
    assertModuleLibDep("project.test", depNonJarName, depNonJar.getUrl());
    assertModuleLibDepScope("project.test", depNonJarName, DependencyScope.RUNTIME);

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project");

    assertModuleLibDep("project", depName, depJar.getUrl());
    assertMergedModuleCompileLibDepScope("project", depName);

    assertModuleLibDep("project", "Gradle: dep:dep:1.0:tests", depTestsJar.getUrl());
    assertModuleLibDepScope("project", "Gradle: dep:dep:1.0:tests", DependencyScope.TEST);

    assertModuleLibDep("project", "Gradle: dep:dep:1.0:someExt", depNonJar.getUrl());
    if (isGradleOlderThan("3.4")) {
      assertModuleLibDepScope("project", "Gradle: dep:dep:1.0:someExt", DependencyScope.RUNTIME);
    }
    else {
      assertModuleLibDepScope("project", "Gradle: dep:dep:1.0:someExt", DependencyScope.RUNTIME, DependencyScope.TEST);
    }
  }


  @Test
  public void testGlobalFileDepsImportedAsProjectLibraries() throws Exception {
    final VirtualFile depJar = createProjectJarSubFile("lib/dep.jar");
    final VirtualFile dep2Jar = createProjectJarSubFile("lib_other/dep.jar");
    createSettingsFile("include 'p1'\n" +
                       "include 'p2'");

    importProjectUsingSingeModulePerGradleProject(
      createBuildScriptBuilder()
        .allprojects(p -> {
          p
            .withJavaPlugin()
            .addImplementationDependency(p.code("rootProject.files('lib/dep.jar', 'lib_other/dep.jar')"));
        })
        .generate());

    assertModules("project", "project.p1", "project.p2");
    Set<Library> libs = new HashSet<>();
    final List<LibraryOrderEntry> moduleLibDeps = getModuleLibDeps("project.p1", "Gradle: dep");
    moduleLibDeps.addAll(getModuleLibDeps("project.p1", "Gradle: dep_1"));
    moduleLibDeps.addAll(getModuleLibDeps("project.p2", "Gradle: dep"));
    moduleLibDeps.addAll(getModuleLibDeps("project.p2", "Gradle: dep_1"));
    for (LibraryOrderEntry libDep : moduleLibDeps) {
      libs.add(libDep.getLibrary());
      assertFalse("Dependency be project level: " + libDep.toString(), libDep.isModuleLevel());
    }

    assertProjectLibraries("Gradle: dep", "Gradle: dep_1");
    assertEquals("No duplicates of libraries are expected", 2, libs.size());
    assertContain(ContainerUtil.map(libs, l -> l.getUrls(OrderRootType.CLASSES)[0]), depJar.getUrl(), dep2Jar.getUrl());
  }

  @Test
  public void testLocalFileDepsImportedAsModuleLibraries() throws Exception {
    final VirtualFile depP1Jar = createProjectJarSubFile("p1/lib/dep.jar");
    final VirtualFile depP2Jar = createProjectJarSubFile("p2/lib/dep.jar");
    createSettingsFile("include 'p1'\n" +
                       "include 'p2'");

    importProjectUsingSingeModulePerGradleProject(createBuildScriptBuilder()
                                                    .allprojects(p -> {
                                                      p
                                                        .withJavaPlugin()
                                                        .addImplementationDependency(p.code("files('lib/dep.jar')"));
                                                    })
                                                    .generate());

    assertModules("project", "project.p1", "project.p2");

    final List<LibraryOrderEntry> moduleLibDepsP1 = getModuleLibDeps("project.p1", "Gradle: dep");
    for (LibraryOrderEntry libDep : moduleLibDepsP1) {
      assertTrue("Dependency must be module level: " + libDep.toString(), libDep.isModuleLevel());
      assertEquals("Wrong library dependency", depP1Jar.getUrl(), libDep.getLibrary().getUrls(OrderRootType.CLASSES)[0]);
    }

    final List<LibraryOrderEntry> moduleLibDepsP2 = getModuleLibDeps("project.p2", "Gradle: dep");
    for (LibraryOrderEntry libDep : moduleLibDepsP2) {
      assertTrue("Dependency must be module level: " + libDep.toString(), libDep.isModuleLevel());
      assertEquals("Wrong library dependency", depP2Jar.getUrl(), libDep.getLibrary().getUrls(OrderRootType.CLASSES)[0]);
    }
  }

  @Test
  public void testProjectWithUnresolvedDependency() throws Exception {
    final VirtualFile depJar = createProjectJarSubFile("lib/dep/dep/1.0/dep-1.0.jar");
    createProjectSubFile("lib/dep/dep/1.0/dep-1.0.pom", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project
        xmlns="http://maven.apache.org/POM/4.0.0"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
        <groupId>dep</groupId>
        <artifactId>dep</artifactId>
        <version>1.0</version>

      </project>
      """);
    importProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .addRepository("maven { url file('lib') }")
        .addImplementationDependency("dep:dep:1.0")
        .addImplementationDependency("some:unresolvable-lib:0.1")
        .generate()
    );

    assertModules("project", "project.main", "project.test");

    final String depName = "Gradle: dep:dep:1.0";
    assertModuleLibDep("project.main", depName, depJar.getUrl());
    assertModuleLibDepScope("project.main", depName, DependencyScope.COMPILE);
    assertModuleLibDepScope("project.main", "Gradle: some:unresolvable-lib:0.1", DependencyScope.COMPILE);

    List<LibraryOrderEntry> unresolvableDep = getModuleLibDeps("project.main", "Gradle: some:unresolvable-lib:0.1");
    assertEquals(1, unresolvableDep.size());
    LibraryOrderEntry unresolvableEntry = unresolvableDep.iterator().next();
    assertEquals(DependencyScope.COMPILE, unresolvableEntry.getScope());
    assertEmpty(unresolvableEntry.getRootUrls(OrderRootType.CLASSES));

    assertModuleLibDep("project.test", depName, depJar.getUrl());
    assertModuleLibDepScope("project.test", depName, DependencyScope.COMPILE);

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project");

    assertModuleLibDep("project", depName, depJar.getUrl());
    assertMergedModuleCompileLibDepScope("project", depName);
    assertMergedModuleCompileLibDepScope("project", "Gradle: some:unresolvable-lib:0.1");

    unresolvableDep = getModuleLibDeps("project", "Gradle: some:unresolvable-lib:0.1");
    if (isGradleOlderThan("3.4") || isGradleNewerThan("4.5")) {
      assertEquals(1, unresolvableDep.size());
      unresolvableEntry = unresolvableDep.iterator().next();
      assertTrue(unresolvableEntry.isModuleLevel());
      assertEquals(DependencyScope.COMPILE, unresolvableEntry.getScope());
    }
    else {
      assertEquals(3, unresolvableDep.size());
      unresolvableEntry = unresolvableDep.iterator().next();
      assertTrue(unresolvableEntry.isModuleLevel());
    }
    assertEmpty(unresolvableEntry.getRootUrls(OrderRootType.CLASSES));
  }

  @Test
  @TargetVersions("3.3+") // org.gradle.api.artifacts.ConfigurationPublications was introduced since 3.3
  public void testSourceSetOutputDirsAsArtifactDependencies() throws Exception {
    createSettingsFile("""
                         rootProject.name = 'server'
                         include 'api'
                         include 'modules:X'
                         include 'modules:Y'""");
    String compileConfiguration = isJavaLibraryPluginSupported() ? "implementation" : "compile";
    importProject(
      "configure(subprojects - project(':modules')) {\n" +
      "    group 'server'\n" +
      "    version '1.0-SNAPSHOT'\n" +
      "    apply plugin: 'java'\n" +
      (isGradleNewerOrSameAs("8.2")
       ? "  java { sourceCompatibility = 1.8 }\n"
       : "    sourceCompatibility = 1.8\n") +
      "}\n" +
      "\n" +
      "project(':api') {\n" +
      "    sourceSets {\n" +
      "        webapp\n" +
      "    }\n" +
      "    configurations {\n" +
      "        webappConf {\n" +
      "            afterEvaluate {\n" +
      "                sourceSets.webapp.output.each {\n" +
      "                    outgoing.artifact(it) {\n" +
      "                        builtBy(sourceSets.webapp.output)\n" +
      "                    }\n" +
      "                }\n" +
      "            }\n" +
      "        }\n" +
      "    }\n" +
      "}\n" +
      "\n" +
      "def webProjects = [project(':modules:X'), project(':modules:Y')]\n" +
      "configure(webProjects) {\n" +
      "    dependencies {\n" +
      "        " + compileConfiguration + " project(path: ':api', configuration: 'webappConf')\n" +
      "    }\n" +
      "}"
    );

    assertModules("server", "server.modules",
                  "server.modules.X", "server.modules.X.main", "server.modules.X.test",
                  "server.modules.Y", "server.modules.Y.main", "server.modules.Y.test",
                  "server.api", "server.api.main", "server.api.test", "server.api.webapp");

    assertModuleModuleDeps("server.modules.X.main", "server.api.webapp");
    assertModuleModuleDepScope("server.modules.X.main", "server.api.webapp", DependencyScope.COMPILE);

    assertModuleModuleDeps("server.modules.Y.main", "server.api.webapp");
    assertModuleModuleDepScope("server.modules.Y.main", "server.api.webapp", DependencyScope.COMPILE);
  }

  @Test
  public void testSourceSetOutputDirsAsRuntimeDependencies() throws Exception {
    importProject(
      "apply plugin: 'java'\n" +
      "sourceSets.main.output.dir file(\"$buildDir/generated-resources/main\")"
    );

    assertModules("project", "project.main", "project.test");
    final String path = path("build/generated-resources/main");
    final String depName = PathUtil.toPresentableUrl(path);
    String root = "file://" + path;
    assertModuleLibDep("project.main", depName, root);
    assertModuleLibDepScope("project.main", depName, DependencyScope.RUNTIME);

    String[] excludedRoots = isNewDependencyResolutionApplicable() ? new String[]{root} : ArrayUtil.EMPTY_STRING_ARRAY;
    assertLibraryExcludedRoots("project.main", depName, excludedRoots);

    VirtualFile depJar = createProjectJarSubFile("lib/dep.jar");
    TestGradleBuildScriptBuilder builder = createBuildScriptBuilder();
    importProject(
      builder.withJavaPlugin()
        .addPrefix("sourceSets.main.output.dir file(\"$buildDir/generated-resources/main\")")
        .addRuntimeOnlyDependency("junit:junit:4.11")
        .addRuntimeOnlyDependency(builder.code("files('lib/dep.jar')"))
        .generate()
    );

    assertLibraryExcludedRoots("project.main", depName, excludedRoots);
    assertLibraryExcludedRoots("project.main", depJar.getPresentableUrl(), ArrayUtil.EMPTY_STRING_ARRAY);
    assertLibraryExcludedRoots("project.main", "Gradle: junit:junit:4.11", ArrayUtil.EMPTY_STRING_ARRAY);
  }

  private void assertLibraryExcludedRoots(String moduleName, String depName, String ... roots) {
    List<LibraryOrderEntry> deps = getModuleLibDeps(moduleName, depName);
    assertThat(deps).hasSize(1);
    LibraryEx library = (LibraryEx)deps.get(0).getLibrary();

    assertThat(library.getUrls(OrderRootType.CLASSES)).hasSize(1);

    String[] excludedRootUrls = library.getExcludedRootUrls();
    assertThat(excludedRootUrls).containsExactly(roots);
  }

  @Test
  public void testSourceSetOutputDirsAsRuntimeDependenciesOfDependantModules() throws Exception {
    createSettingsFile("include 'projectA', 'projectB', 'projectC' ");
    importProject(
      createBuildScriptBuilder()
        .project(":projectA", it -> {
          it
            .withJavaPlugin()
            .addPostfix("sourceSets.main.output.dir file(\"$buildDir/generated-resources/main\")");
        })
        .project(":projectB", it -> {
          it
            .withJavaPlugin()
            .addImplementationDependency(it.project(":projectA"));
        })
        .project(":projectC", it -> {
          it
            .withJavaPlugin()
            .addRuntimeOnlyDependency(it.project(":projectB"));
        })
        .generate()
    );

    assertModules("project",
                  "project.projectA", "project.projectA.main", "project.projectA.test",
                  "project.projectB", "project.projectB.main", "project.projectB.test",
                  "project.projectC", "project.projectC.main", "project.projectC.test");

    assertModuleModuleDepScope("project.projectB.main", "project.projectA.main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project.projectC.main", "project.projectA.main", DependencyScope.RUNTIME);
    assertModuleModuleDepScope("project.projectC.main", "project.projectB.main", DependencyScope.RUNTIME);

    final String path = path("projectA/build/generated-resources/main");
    final String classesPath = "file://" + path;
    final String depName = PathUtil.toPresentableUrl(path);
    assertModuleLibDep("project.projectA.main", depName, classesPath);
    assertModuleLibDepScope("project.projectA.main", depName, DependencyScope.RUNTIME);
    assertModuleLibDep("project.projectB.main", depName, classesPath);
    assertModuleLibDepScope("project.projectB.main", depName,
                            isNewDependencyResolutionApplicable() ? DependencyScope.RUNTIME : DependencyScope.COMPILE);
    assertModuleLibDep("project.projectC.main", depName, classesPath);
    assertModuleLibDepScope("project.projectC.main", depName, DependencyScope.RUNTIME);
  }

  @Test
  @TargetVersions("3.4+")
  public void testSourceSetOutputDirsAsDependenciesOfDependantModules() throws Exception {
    createSettingsFile("include 'projectA', 'projectB', 'projectC' ");
    importProject(
      """
        subprojects {\s
            apply plugin: "java"\s
        }
        project(':projectA') {
          sourceSets.main.output.dir file('generated/projectA')
        }
        project(':projectB') {
          sourceSets.main.output.dir file('generated/projectB')
          dependencies {
            implementation project(':projectA')
          }
        }
        project(':projectC') {
          dependencies {
            implementation project(':projectB')
          }
        }"""
    );

    assertModules("project",
                  "project.projectA", "project.projectA.main", "project.projectA.test",
                  "project.projectB", "project.projectB.main", "project.projectB.test",
                  "project.projectC", "project.projectC.main", "project.projectC.test");

    assertModuleModuleDepScope("project.projectB.main", "project.projectA.main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project.projectC.main", "project.projectA.main", DependencyScope.RUNTIME);
    assertModuleModuleDepScope("project.projectC.main", "project.projectB.main", DependencyScope.COMPILE);

    final String pathA =
      FileUtil.toSystemIndependentName(new File(getProjectPath(), "projectA/generated/projectA").getAbsolutePath());
    final String classesPathA = "file://" + pathA;
    final String depNameA = PathUtil.toPresentableUrl(pathA);

    final String pathB =
      FileUtil.toSystemIndependentName(new File(getProjectPath(), "projectB/generated/projectB").getAbsolutePath());
    final String classesPathB = "file://" + pathB;
    final String depNameB = PathUtil.toPresentableUrl(pathB);

    assertModuleLibDep("project.projectA.main", depNameA, classesPathA);
    assertModuleLibDepScope("project.projectA.main", depNameA, DependencyScope.RUNTIME);

    assertModuleLibDep("project.projectB.main", depNameA, classesPathA);
    assertModuleLibDepScope("project.projectB.main", depNameA,
                            isNewDependencyResolutionApplicable() ? DependencyScope.RUNTIME : DependencyScope.COMPILE);
    assertModuleLibDep("project.projectB.main", depNameB, classesPathB);
    assertModuleLibDepScope("project.projectB.main", depNameB, DependencyScope.RUNTIME);

    assertModuleLibDep("project.projectC.main", depNameA, classesPathA);
    assertModuleLibDepScope("project.projectC.main", depNameA, DependencyScope.RUNTIME);
    assertModuleLibDep("project.projectC.main", depNameB, classesPathB);
    assertModuleLibDepScope("project.projectC.main", depNameB,
                            isNewDependencyResolutionApplicable() ? DependencyScope.RUNTIME : DependencyScope.COMPILE);
  }

  @Test
  public void testProjectArtifactDependencyInTestAndArchivesConfigurations() throws Exception {
    createSettingsFile("include 'api', 'impl' ");

    importProject(
      createBuildScriptBuilder()
        .allprojects(TestGradleBuildScriptBuilder::withJavaPlugin)
        .project(":api", it -> {
          it
            .addPostfix("configurations { tests }")
            .withTask("testJar", "Jar", task -> {
              task.code("dependsOn testClasses");
              if (isGradleNewerOrSameAs("8.2")) {
                task.code("archiveBaseName = \"${project.base.archivesName}-tests\"");
              } else if (isGradleNewerOrSameAs("7.0")) {
                task.code("archiveBaseName = \"${project.archivesBaseName}-tests\"");
              } else {
                task.code("baseName = \"${project.archivesBaseName}-tests\"");
              }
              if (isGradleOlderThan("8.0")) {
                task.code("classifier 'test'");
              }
              else {
                task.code("archiveClassifier = 'test'");
              }
              task.code("from sourceSets.test.output");
              return null;
            })
            .addPostfix("artifacts {",
                        "    tests testJar",
                        "    archives testJar",
                        "}")
            .addTestImplementationDependency("junit:junit:4.11");
        })
        .project(":impl", it -> {
          it.addTestImplementationDependency(it.project(":api", "tests"));
        })
        .generate()
    );

    assertModules("project", "project.main", "project.test",
                  "project.api", "project.api.main", "project.api.test",
                  "project.impl", "project.impl.main", "project.impl.test");

    assertModuleModuleDepScope("project.test", "project.main", DependencyScope.COMPILE);
    assertProductionOnTestDependencies("project.test", ArrayUtilRt.EMPTY_STRING_ARRAY);

    assertModuleModuleDepScope("project.api.test", "project.api.main", DependencyScope.COMPILE);
    assertProductionOnTestDependencies("project.api.test", ArrayUtilRt.EMPTY_STRING_ARRAY);

    assertModuleModuleDepScope("project.impl.test", "project.impl.main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project.impl.test", "project.api.test", DependencyScope.COMPILE);
    assertProductionOnTestDependencies("project.impl.test", "project.api.test");

    assertModuleModuleDeps("project.impl.main", ArrayUtilRt.EMPTY_STRING_ARRAY);

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project", "project.api", "project.impl");

    assertModuleModuleDepScope("project.impl", "project.api", DependencyScope.TEST);
  }


  @Test
  public void testProjectDependencyOnCustomArtifacts() throws Exception {
    createSettingsFile("include 'api', 'impl' ");
    String archiveBaseName = (isGradleOlderThan("7.0") ? "baseName" : "archiveBaseName") + " = 'my-archive'\n";

    importProject(
      createBuildScriptBuilder()
        .allprojects(TestGradleBuildScriptBuilder::withJavaPlugin)
        .project(":api", it -> {
          it
            .addPostfix("""
                          configurations { myConfig }
                          sourceSets { mySourceSet }
                          tasks.create("myJar", Jar) {
                            dependsOn compileMySourceSetJava
                          """ +  archiveBaseName + """
                            from sourceSets.mySourceSet.output
                          }
                          artifacts { myConfig myJar }
                          """);
        })
        .project(":impl", it -> {
          it.addImplementationDependency(it.project(":api", "myConfig"));
        })
        .generate()
    );

    assertModules("project", "project.main", "project.test",
                  "project.api", "project.api.main", "project.api.test", "project.api.mySourceSet",
                  "project.impl", "project.impl.main", "project.impl.test");

    assertModuleModuleDepScope("project.impl.main", "project.api.mySourceSet", DependencyScope.COMPILE);
  }

  @Test
  public void testProjectDependencyOnCustomArtifacts2() throws Exception {
    createSettingsFile("include 'api', 'impl' ");
    String archiveBaseName = (isGradleOlderThan("7.0") ? "baseName" : "archiveBaseName") + " = 'my-archive'\n";

    String propertyBasedFromClasses;
    if (isGradleOlderThan("4.0")) {
      propertyBasedFromClasses = "new File(project.buildDir, 'classes/mySourceSet')";
    } else if (isGradleOlderThan("4.1")) {
      propertyBasedFromClasses = "new File(project.buildDir, 'classes/java/mySourceSet')";
    } else {
      propertyBasedFromClasses = "project.layout.getBuildDirectory().dir('classes/java/mySourceSet')";
    }

    importProject(
      createBuildScriptBuilder()
        .allprojects(TestGradleBuildScriptBuilder::withJavaPlugin)
        .project(":api", it -> {
          it
            .addPostfix("""
                          configurations { myConfig }
                          sourceSets { mySourceSet }
                          tasks.create("myJar", Jar) {
                            dependsOn compileMySourceSetJava
                          """ + archiveBaseName + """
                           from\s""" + propertyBasedFromClasses + """
                            from new File(project.buildDir, "resources/mySourceSet")
                          }
                          artifacts { myConfig myJar }
                          """);
        })
        .project(":impl", it -> {
          it.addImplementationDependency(it.project(":api", "myConfig"));
        })
        .generate()
    );

    assertModules("project", "project.main", "project.test",
                  "project.api", "project.api.main", "project.api.test", "project.api.mySourceSet",
                  "project.impl", "project.impl.main", "project.impl.test");

    assertModuleModuleDepScope("project.impl.main", "project.api.mySourceSet", DependencyScope.COMPILE);
  }

  @Test
  @TargetVersions("7.0+")
  public void testProjectJarTaskWithUnresolvableProvider() throws Exception {
    createProjectSubFile("settings.gradle", settingsScript(it -> {
      it.setProjectName("project");
    }));
    createProjectSubFile("build.gradle", script(it -> {
      it.withJavaPlugin();
      it.addPostfix("""
                      tasks.create('customJar', Jar) {
                        // unresolvable provider
                        from jar.archiveFile.map { it }
                      }
                      """);
    }));
    importProject();

    assertModules("project", "project.main", "project.test");
  }

  /**
   * At the moment, IDEA does not support depending on an artifact containing output of multiple source sets.
   * There is only one source set to choose as the module dependency.
   * "Owning" sourceSet should be preferred for a jar task with name equal to sourceSet.getJarTaskName()
   */
  @Test
  public void testProjectDependencyOnArtifactsContainingMultipleSourceSets() throws Exception {
    createSettingsFile("include 'api', 'impl' ");
    String archiveBaseName = (isGradleOlderThan("7.0") ? "baseName" : "archiveBaseName") + " = 'my-archive'\n";

    importProject(
      createBuildScriptBuilder()
        .allprojects(TestGradleBuildScriptBuilder::withJavaPlugin)
        .project(":api", it -> {
          it
            .addPostfix("""
                          configurations { myConfig }
                          sourceSets { mainX }
                          jar { from sourceSets.mainX.output }
                          def mainXJarTask = tasks.create(sourceSets.mainX.getJarTaskName(), Jar) {
                            """ +  archiveBaseName + """
                            from sourceSets.mainX.output
                          }
                          artifacts { myConfig mainXJarTask }
                          """);
        })
        .project(":impl", it -> {
          it.addImplementationDependency(it.project(":api"));
          it.addTestImplementationDependency(it.project(":api", "myConfig"));
        })
        .generate()
    );

    assertModules("project", "project.main", "project.test",
                  "project.api", "project.api.main", "project.api.test", "project.api.mainX",
                  "project.impl", "project.impl.main", "project.impl.test");

    assertModuleModuleDeps("project.impl.main", "project.api.main"); // does not depend on mainX
    assertModuleModuleDeps("project.impl.test", "project.impl.main", "project.api.main", "project.api.mainX");
  }

  @Test
  public void testCompileAndRuntimeConfigurationsTransitiveDependencyMerge() throws Exception {
    createSettingsFile("""
                         include 'project1'
                         include 'project2'
                         include 'project-tests'""");

    importProject(
      createBuildScriptBuilder()
        .subprojects(TestGradleBuildScriptBuilder::withJavaPlugin)
        .project(":project1", it -> {
          it
            .withJavaLibraryPlugin()
            .addApiDependency("org.apache.geronimo.specs:geronimo-jms_1.1_spec:1.0");
        })
        .project(":project2", it -> {
          it.addRuntimeOnlyDependency("org.apache.geronimo.specs:geronimo-jms_1.1_spec:1.1.1");
        })
        .project(":project-tests", it -> {
          it
            .addImplementationDependency(it.project(":project1"))
            .addRuntimeOnlyDependency(it.project(":project2"))
            .addImplementationDependency("junit:junit:4.11");
        })
        .generate()
    );

    assertModules("project",
                  "project.project1", "project.project1.main", "project.project1.test",
                  "project.project2", "project.project2.main", "project.project2.test",
                  "project.project-tests", "project.project-tests.main", "project.project-tests.test");

    assertModuleModuleDepScope("project.project-tests.main", "project.project1.main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project.project-tests.main", "project.project2.main", DependencyScope.RUNTIME);
    assertModuleLibDepScope("project.project-tests.main", "Gradle: org.apache.geronimo.specs:geronimo-jms_1.1_spec:1.0",
                            isNewDependencyResolutionApplicable() ? DependencyScope.PROVIDED : DependencyScope.COMPILE);
    assertModuleLibDepScope("project.project-tests.main", "Gradle: org.apache.geronimo.specs:geronimo-jms_1.1_spec:1.1.1",
                            DependencyScope.RUNTIME);

    createProjectSubDirs("project1", "project2", "project-tests");
    maybeAssertCompileClasspathOrderingWithEnabledClasspathPackaging("project.project-tests.main");

    importProjectUsingSingeModulePerGradleProject();

    assertMergedModuleCompileModuleDepScope("project.project-tests", "project.project1");

    boolean gradleOlderThen_3_4 = isGradleOlderThan("3.4");
    if (gradleOlderThen_3_4) {
      assertModuleModuleDepScope("project.project-tests", "project.project2", DependencyScope.RUNTIME);
    }
    else {
      assertModuleModuleDepScope("project.project-tests", "project.project2", DependencyScope.RUNTIME, DependencyScope.TEST);
    }
    if (GradleVersion.version(gradleVersion).compareTo(GradleVersion.version("2.0")) > 0) {
      if (isGradleNewerThan("4.5")) {
        assertModuleLibDepScope("project.project-tests", "Gradle: org.apache.geronimo.specs:geronimo-jms_1.1_spec:1.0",
                                ar(DependencyScope.PROVIDED));
        assertModuleLibDepScope("project.project-tests", "Gradle: org.apache.geronimo.specs:geronimo-jms_1.1_spec:1.1.1",
                                ar(DependencyScope.RUNTIME, DependencyScope.TEST));
      }
      else {
        assertModuleLibDepScope("project.project-tests", "Gradle: org.apache.geronimo.specs:geronimo-jms_1.1_spec:1.0",
                                gradleOlderThen_3_4 ? ar(DependencyScope.COMPILE) : ar(DependencyScope.PROVIDED, DependencyScope.TEST));
        assertModuleLibDepScope("project.project-tests", "Gradle: org.apache.geronimo.specs:geronimo-jms_1.1_spec:1.1.1",
                                gradleOlderThen_3_4 ? ar(DependencyScope.RUNTIME) : ar(DependencyScope.RUNTIME, DependencyScope.TEST));
      }
    }
  }

  @Test
  public void testNonDefaultProjectConfigurationDependency() throws Exception {
    createSettingsFile("""
                         include 'project1'
                         include 'project2'
                         """);

    String compileConfiguration = isJavaLibraryPluginSupported() ? "implementation" : "compile";
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
      "    " + compileConfiguration + " project(path: ':project1', configuration: 'myConf')\n" +
      "  }\n" +
      "}\n"
    );

    assertModules("project", "project.project1", "project.project2", "project.project2.main", "project.project2.test");

    assertModuleModuleDeps("project.project2.main");
    assertModuleLibDepScope("project.project2.main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleLibDepScope("project.project2.main", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project", "project.project1", "project.project2");
    if (isGradleNewerThan("4.5")) {
      assertModuleModuleDepScope("project.project2", "project.project1");
    }
    else if (isGradleOlderThan("3.4")) {
      assertModuleModuleDepScope("project.project2", "project.project1", DependencyScope.COMPILE);
    }
    else {
      assertModuleModuleDepScope("project.project2", "project.project1", DependencyScope.PROVIDED, DependencyScope.TEST,
                                 DependencyScope.RUNTIME);
    }
    if (GradleVersion.version(gradleVersion).compareTo(GradleVersion.version("2.0")) > 0) {
      assertMergedModuleCompileLibDepScope("project.project2", "Gradle: org.hamcrest:hamcrest-core:1.3");
      assertMergedModuleCompileLibDepScope("project.project2", "Gradle: junit:junit:4.11");
    }
  }

  @Test
  public void testNonDefaultProjectConfigurationDependencyWithMultipleArtifacts() throws Exception {
    createSettingsFile("""
                         include 'project1'
                         include 'project2'
                         """);

    importProject(
      createBuildScriptBuilder()
        .project(":project1", it -> {
          it
            .withJavaPlugin()
            .addPostfix("configurations { tests.extendsFrom testRuntime }")
            .withTask("testJar", "Jar", task -> {
              if (isGradleOlderThan("8.0")) {
                task.code("classifier 'test'");
              }
              else {
                task.code("archiveClassifier = 'test'");
              }
              task.code("from project.sourceSets.test.output");
              return null;
            })
            .addPostfix("artifacts {",
                        "    tests testJar",
                        "    archives testJar",
                        "}")
            .addTestImplementationDependency("junit:junit:4.11");
        })
        .project(":project2", it -> {
          it
            .withJavaPlugin()
            .addTestImplementationDependency(it.project(":project1", "tests"));
        })
        .generate()
    );

    assertModules("project",
                  "project.project1", "project.project1.main", "project.project1.test",
                  "project.project2", "project.project2.main", "project.project2.test");

    assertModuleModuleDeps("project.project1.main", ArrayUtilRt.EMPTY_STRING_ARRAY);
    assertModuleLibDeps("project.project1.main", ArrayUtilRt.EMPTY_STRING_ARRAY);
    assertModuleLibDepScope("project.project1.test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleLibDepScope("project.project1.test", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);

    assertModuleModuleDeps("project.project2.main", ArrayUtilRt.EMPTY_STRING_ARRAY);
    assertModuleLibDeps("project.project2.main", ArrayUtilRt.EMPTY_STRING_ARRAY);
    if (!isJavaLibraryPluginSupported()) {
      assertModuleLibDepScope("project.project2.test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
      assertModuleLibDepScope("project.project2.test", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);
    }
    if (isGradleOlderThan("7.0")) {
      assertModuleModuleDeps("project.project2.test", "project.project2.main", "project.project1.main", "project.project1.test");
      assertModuleModuleDepScope("project.project2.test", "project.project1.main", DependencyScope.COMPILE);
    }
    else {
      assertModuleModuleDeps("project.project2.test", "project.project2.main", "project.project1.test");
    }
    assertModuleModuleDepScope("project.project2.test", "project.project2.main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project.project2.test", "project.project1.test", DependencyScope.COMPILE);
    assertProductionOnTestDependencies("project.project2.test", "project.project1.test");
  }

  @Test
  @TargetVersions("3.0 <=> 6.9")
  public void testDependencyOnDefaultConfigurationWithAdditionalArtifact() throws Exception {
    createSettingsFile("include 'project1', 'project2'");
    createProjectSubFile("project1/build.gradle",
                         createBuildScriptBuilder()
                           .withJavaPlugin()
                           .addPostfix(
                             "configurations {",
                             "  aParentCfg",
                             "  compile.extendsFrom aParentCfg",
                             "}",
                             "sourceSets {",
                             "  aParentSrc { java.srcDirs = ['src/aParent/java'] }",
                             "  main { java { compileClasspath += aParentSrc.output } }",
                             "}",
                             "task aParentSrcJar(type:Jar) {",
                             "    appendix 'parent'",
                             "    from sourceSets.aParentSrc.output",
                             "}",
                             "artifacts {",
                             "  aParentCfg aParentSrcJar",
                             "}"
                           )
                           .generate()
    );

    TestGradleBuildScriptBuilder builder = createBuildScriptBuilder();
    createProjectSubFile("project2/build.gradle", builder
      .withJavaPlugin()
      .addImplementationDependency(builder.project(":project1"))
      .generate());

    importProject("");

    assertModules("project",
                  "project.project1", "project.project1.main", "project.project1.test", "project.project1.aParentSrc",
                  "project.project2", "project.project2.main", "project.project2.test");

    assertModuleModuleDeps("project.project2.main", "project.project1.main", "project.project1.aParentSrc");
  }


  @Test
  @TargetVersions("2.0+")
  public void testTestModuleDependencyAsArtifactFromTestSourceSetOutput() throws Exception {
    createSettingsFile("""
                         include 'project1'
                         include 'project2'
                         """);

    importProject(
      createBuildScriptBuilder()
        .project(":project1", it -> {
          it
            .withJavaPlugin()
            .addPrefix("configurations { testArtifacts }")
            .withTask("testJar", "Jar", task -> {
              if (isGradleOlderThan("8.0")) {
                task.code("classifier 'test'");
              }
              else {
                task.code("archiveClassifier = 'test'");
              }
              task.code("from sourceSets.test.output");
              return null;
            })
            .addPostfix("artifacts { testArtifacts testJar }");
        })
        .project(":project2", it -> {
          it
            .withJavaPlugin()
            .addTestImplementationDependency(it.project(":project1", "testArtifacts"));
        })
        .generate()
    );

    assertModules("project",
                  "project.project1", "project.project1.main", "project.project1.test",
                  "project.project2", "project.project2.main", "project.project2.test");

    assertModuleModuleDeps("project.project2.main", ArrayUtilRt.EMPTY_STRING_ARRAY);
    assertModuleModuleDeps("project.project2.test", "project.project2.main", "project.project1.test");
    assertProductionOnTestDependencies("project.project2.test", "project.project1.test");

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project", "project.project1", "project.project2");
    assertModuleModuleDeps("project.project2", "project.project1");
  }

  @Test
  @TargetVersions("2.0+")
  public void testTestModuleDependencyAsArtifactFromTestSourceSetOutput2() throws Exception {
    createSettingsFile("""
                         include 'project1'
                         include 'project2'
                         """);

    importProject(
      createBuildScriptBuilder()
        .project(":project1", it -> {
          it
            .withJavaPlugin()
            .addPrefix("configurations { testArtifacts }")
            .withTask("testJar", "Jar", task -> {
              if (isGradleOlderThan("8.0")) {
                task.code("classifier 'test'");
              }
              else {
                task.code("archiveClassifier = 'test'");
              }
              task.code("from sourceSets.test.output");
              return null;
            })
            .addPostfix("artifacts { testArtifacts testJar }");
        })
        .project(":project2", it -> {
          it
            .withJavaPlugin()
            .addImplementationDependency(it.code("project(path: ':project1')"))
            .addTestImplementationDependency(it.project(":project1", "testArtifacts"));
        })
        .generate()
    );

    assertModules("project",
                  "project.project1", "project.project1.main", "project.project1.test",
                  "project.project2", "project.project2.main", "project.project2.test");

    assertModuleModuleDeps("project.project2.main", "project.project1.main");
    assertProductionOnTestDependencies("project.project2.main", ArrayUtilRt.EMPTY_STRING_ARRAY);
    assertModuleModuleDeps("project.project2.test", "project.project2.main", "project.project1.main", "project.project1.test");
    assertProductionOnTestDependencies("project.project2.test", "project.project1.test");

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project", "project.project1", "project.project2");
  }

  @Test
  @TargetVersions("2.0+")
  public void testTestModuleDependencyAsArtifactFromTestSourceSetOutput3() throws Exception {
    createSettingsFile("""
                         include 'project1'
                         include 'project2'
                         """);

    importProject(
      createBuildScriptBuilder()
        .allprojects(p -> {
          p
            .withIdeaPlugin()
            .addPrefix("idea {",
                       "  module {",
                       "    inheritOutputDirs = false",
                       "    outputDir = file(\"buildIdea/main\")",
                       "    testOutputDir = file(\"buildIdea/test\")",
                       "    excludeDirs += file('buildIdea')",
                       "  }",
                       "}");
        })
        .project(":project1", it -> {
          it
            .withJavaPlugin()
            .addPrefix("configurations { testArtifacts }")
            .withTask("testJar", "Jar", task -> {
              if (isGradleOlderThan("8.0")) {
                task.code("classifier 'test'");
              }
              else {
                task.code("archiveClassifier = 'test'");
              }
              task.code("from sourceSets.test.output");
              return null;
            })
            .addPostfix("artifacts { testArtifacts testJar }");
        })
        .project(":project2", it -> {
          it
            .withJavaPlugin()
            .addTestImplementationDependency(it.project(":project1", "testArtifacts"));
        })
        .generate()
    );

    assertModules("project",
                  "project.project1", "project.project1.main", "project.project1.test",
                  "project.project2", "project.project2.main", "project.project2.test");

    String mainClassesOutputPath = isGradleNewerOrSameAs("4.0") ? "/build/classes/java/main" : "/build/classes/main";
    String testClassesOutputPath = isGradleNewerOrSameAs("4.0") ? "/build/classes/java/test" : "/build/classes/test";

    assertModuleOutput("project.project1.main", getProjectPath() + "/project1" + mainClassesOutputPath, "");
    assertModuleOutput("project.project1.test", "", getProjectPath() + "/project1" + testClassesOutputPath);

    assertModuleOutput("project.project2.main", getProjectPath() + "/project2" + mainClassesOutputPath, "");
    assertModuleOutput("project.project2.test", "", getProjectPath() + "/project2" + testClassesOutputPath);

    assertModuleModuleDeps("project.project2.main", ArrayUtilRt.EMPTY_STRING_ARRAY);
    assertModuleModuleDeps("project.project2.test", "project.project2.main", "project.project1.test");
    assertProductionOnTestDependencies("project.project2.test", "project.project1.test");

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project", "project.project1", "project.project2");
    assertModuleModuleDeps("project.project2", "project.project1");
  }

  @Test
  @TargetVersions("2.6+")
  public void testProjectSubstitutions() throws Exception {
    createSettingsFile("""
                         include 'core'
                         include 'service'
                         include 'util'
                         """);

    importProject(
      createBuildScriptBuilder()
        .subprojects(p -> {
          p.withJavaPlugin();
          if (isGradleOlderThan("8.0")) {
            p.addPrefix("configurations.all {",
                       "  resolutionStrategy.dependencySubstitution {",
                       "    substitute module('mygroup:core') with project(':core')",
                       "    substitute project(':util') with module('junit:junit:4.11')",
                       "  }",
                       "}");
          } else {
            p.addPrefix("configurations.all {",
                        "  resolutionStrategy.dependencySubstitution {",
                        "    substitute module('mygroup:core') using project(':core')",
                        "    substitute project(':util') using module('junit:junit:4.11')",
                        "  }",
                        "}");
          }
        })
        .project(":core", p -> {
          p
            .withJavaLibraryPlugin()
            .addApiDependency(p.project(":util"));
        })
        .project(":service", p -> {
          p.addImplementationDependency("mygroup:core:latest.release");
        })
        .generate()
    );

    assertModules("project",
                  "project.core", "project.core.main", "project.core.test",
                  "project.service", "project.service.main", "project.service.test",
                  "project.util", "project.util.main", "project.util.test");

    assertModuleModuleDeps("project.service.main", "project.core.main");
    assertModuleModuleDepScope("project.service.main", "project.core.main", DependencyScope.COMPILE);
    assertModuleLibDepScope("project.service.main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleLibDepScope("project.service.main", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project", "project.core", "project.service", "project.util");

    assertMergedModuleCompileModuleDepScope("project.service", "project.core");
    assertMergedModuleCompileLibDepScope("project.service", "Gradle: org.hamcrest:hamcrest-core:1.3");
    assertMergedModuleCompileLibDepScope("project.service", "Gradle: junit:junit:4.11");
  }

  @Test
  @TargetVersions("2.6+")
  public void testProjectSubstitutionsWithTransitiveDeps() throws Exception {
    createSettingsFile("""
                         include 'modA'
                         include 'modB'
                         include 'app'
                         """);
    importProject(
      createBuildScriptBuilder()
        .subprojects(it -> {
          it
            .withJavaLibraryPlugin()
            .addVersion("1.0.0");
        })
        .project(":app", it -> {
          it.addRuntimeOnlyDependency("org.hamcrest:hamcrest-core:1.3")
            .addTestImplementationDependency("project:modA:1.0.0");
          if (isGradleOlderThan("8.0")) {
            it.addPostfix("configurations.all {",
                          "  resolutionStrategy.dependencySubstitution {",
                          "    substitute module('project:modA:1.0.0') with project(':modA')",
                          "    substitute module('project:modB:1.0.0') with project(':modB')",
                          "  }",
                          "}");
          } else {
            it.addPostfix("configurations.all {",
                          "  resolutionStrategy.dependencySubstitution {",
                          "    substitute module('project:modA:1.0.0') using project(':modA')",
                          "    substitute module('project:modB:1.0.0') using project(':modB')",
                          "  }",
                          "}");
          }
        })
        .project(":modA", it -> { it.addApiDependency(it.project(":modB")); })
        .project(":modB", it -> { it.addApiDependency("org.hamcrest:hamcrest-core:1.3"); })
        .generate()
    );

    assertModules("project", "project.app", "project.app.main", "project.app.test",
                  "project.modA", "project.modA.main", "project.modA.test",
                  "project.modB", "project.modB.main", "project.modB.test");

    assertModuleLibDepScope("project.app.main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.RUNTIME);
    assertModuleModuleDeps("project.app.main");
    assertModuleLibDepScope("project.app.test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleModuleDeps("project.app.test", "project.app.main", "project.modA.main", "project.modB.main");

    assertModuleLibDepScope("project.modA.main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleModuleDeps("project.modA.main", "project.modB.main");
    assertModuleLibDepScope("project.modA.test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleModuleDeps("project.modA.test", "project.modA.main", "project.modB.main");

    assertModuleLibDepScope("project.modB.main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleModuleDeps("project.modB.main");
    assertModuleLibDepScope("project.modB.test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleModuleDeps("project.modB.test", "project.modB.main");

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project", "project.app", "project.modA", "project.modB");

    assertModuleModuleDeps("project.app", "project.modA", "project.modB");
    assertModuleModuleDepScope("project.app", "project.modA", DependencyScope.TEST);
    assertModuleModuleDepScope("project.app", "project.modB", DependencyScope.TEST);
    assertModuleLibDepScope("project.app", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.RUNTIME, DependencyScope.TEST);

    assertMergedModuleCompileModuleDepScope("project.modA", "project.modB");
    assertMergedModuleCompileLibDepScope("project.modA", "Gradle: org.hamcrest:hamcrest-core:1.3");

    assertModuleModuleDeps("project.modB");
    assertMergedModuleCompileLibDepScope("project.modB", "Gradle: org.hamcrest:hamcrest-core:1.3");
  }

  @Test
  @TargetVersions("2.12+")
  public void testCompileOnlyScope() throws Exception {
    importProject(
      """
        apply plugin: 'java'
        dependencies {
          compileOnly 'junit:junit:4.11'
        }"""
    );

    assertModules("project", "project.main", "project.test");
    assertModuleModuleDepScope("project.test", "project.main", DependencyScope.COMPILE);

    assertModuleLibDepScope("project.main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.PROVIDED);
    assertModuleLibDepScope("project.main", "Gradle: junit:junit:4.11", DependencyScope.PROVIDED);

    assertModuleLibDeps("project.test");

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project");

    assertModuleLibDepScope("project", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.PROVIDED);
    assertModuleLibDepScope("project", "Gradle: junit:junit:4.11", DependencyScope.PROVIDED);
  }

  @Test
  @TargetVersions("2.12+")
  public void testCompileOnlyAndRuntimeScope() throws Exception {
    importProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .addRuntimeOnlyDependency("org.hamcrest:hamcrest-core:1.3")
        .addCompileOnlyDependency("org.hamcrest:hamcrest-core:1.3")
        .generate()
    );

    assertModules("project", "project.main", "project.test");
    assertModuleModuleDepScope("project.test", "project.main", DependencyScope.COMPILE);

    assertModuleLibDepScope("project.main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleLibDepScope("project.test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.RUNTIME);

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project");

    if (isGradleNewerThan("4.5")) {
      assertModuleLibDepScope("project", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    }
    else if (isGradleOlderThan("3.4")) {
      assertModuleLibDepScope("project", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.PROVIDED, DependencyScope.RUNTIME);
    }
    else {
      assertModuleLibDepScope("project", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.TEST, DependencyScope.PROVIDED,
                              DependencyScope.RUNTIME);
    }
  }

  @Test
  @TargetVersions("2.12+")
  public void testCompileOnlyAndCompileScope() throws Exception {
    createSettingsFile("include 'app'\n");
    TestGradleBuildScriptBuilder builder = createBuildScriptBuilder();
    importProject(
      builder
        .withJavaPlugin()
        .addCompileOnlyDependency(builder.project(":app"))
        .addImplementationDependency("junit:junit:4.11")
        .project(":app", it -> {
          it
            .withJavaPlugin()
            .addImplementationDependency("junit:junit:4.11");
        })
        .generate()
    );

    assertModules("project", "project.main", "project.test",
                  "project.app", "project.app.main", "project.app.test");

    assertModuleModuleDepScope("project.main", "project.app.main", DependencyScope.PROVIDED);
    assertModuleLibDepScope("project.main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleLibDepScope("project.main", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);

    assertModuleModuleDeps("project.test", "project.main");
    assertModuleModuleDepScope("project.test", "project.main", DependencyScope.COMPILE);
    assertModuleLibDepScope("project.test", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);
    assertModuleLibDepScope("project.test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
  }

  @Test
  @TargetVersions("3.4+")
  public void testJavaLibraryPluginConfigurations() throws Exception {
    createSettingsFile("""
                         include 'project1'
                         include 'project2'
                         """);

    importProject(
      createBuildScriptBuilder()
        .project(":project1", p -> {
          p
            .withJavaPlugin()
            .addImplementationDependency(p.project(":project2"));
        })
        .project(":project2", p -> {
          p
            .withJavaLibraryPlugin()
            .addImplementationDependency("junit:junit:4.11")
            .addApiDependency("org.hamcrest:hamcrest-core:1.3");
        })
        .generate()
    );

    assertModules("project",
                  "project.project1", "project.project1.main", "project.project1.test",
                  "project.project2", "project.project2.main", "project.project2.test");

    assertModuleModuleDepScope("project.project1.main", "project.project2.main", DependencyScope.COMPILE);
    assertModuleLibDepScope("project.project1.main", "Gradle: junit:junit:4.11", DependencyScope.RUNTIME);
    assertModuleLibDepScope("project.project1.main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);

    assertModuleModuleDepScope("project.project1.test", "project.project1.main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project.project1.test", "project.project2.main", DependencyScope.COMPILE);
    assertModuleLibDepScope("project.project1.test", "Gradle: junit:junit:4.11", DependencyScope.RUNTIME);
    assertModuleLibDepScope("project.project1.test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);

    assertModuleLibDepScope("project.project2.main", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);
    assertModuleLibDepScope("project.project2.main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project.project2.test", "project.project2.main", DependencyScope.COMPILE);
    assertModuleLibDepScope("project.project2.test", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);
    assertModuleLibDepScope("project.project2.test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
  }


  @Test
  @TargetVersions("2.12 <=> 6.9")
  public void testNonTransitiveConfiguration() throws Exception {
    importProject(
      """
        apply plugin: 'java'
        configurations {
          compile.transitive = false
        }

        dependencies {
          compile 'junit:junit:4.11'
        }"""
    );

    assertModules("project", "project.main", "project.test");
    assertModuleModuleDepScope("project.test", "project.main", DependencyScope.COMPILE);

    assertModuleLibDepScope("project.main", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);
    assertModuleLibDepScope("project.main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);

    assertModuleLibDepScope("project.test", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);
    assertModuleLibDepScope("project.test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project");

    assertMergedModuleCompileLibDepScope("project", "Gradle: junit:junit:4.11");

    if (isGradleOlderThan("3.4")) {
      assertModuleLibDepScope("project", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.PROVIDED, DependencyScope.RUNTIME);
    }
    else if (isGradleNewerThan("4.5")) {
      assertModuleLibDepScope("project", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
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
      createBuildScriptBuilder()
        .project(":projectA", it -> {
          it.withJavaPlugin();
        })
        .project(":projectB", it -> {
          it
            .withJavaLibraryPlugin()
            .addApiDependency(it.project(":projectA"));
        })
        .project(":projectC", it -> {
          it
            .applyPlugin("war")
            .addDependency("providedCompile", it.project(":projectB"));
        })
        .generate()
    );

    assertModules("project",
                  "project.projectA", "project.projectA.main", "project.projectA.test",
                  "project.projectB", "project.projectB.main", "project.projectB.test",
                  "project.projectC", "project.projectC.main", "project.projectC.test");

    assertModuleModuleDepScope("project.projectB.main", "project.projectA.main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project.projectC.main", "project.projectA.main", DependencyScope.PROVIDED);
    assertModuleModuleDepScope("project.projectC.main", "project.projectB.main", DependencyScope.PROVIDED);

    createProjectSubDirs("projectA", "projectB", "projectC");
    maybeAssertCompileClasspathOrderingWithEnabledClasspathPackaging("project.projectC.main");

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project", "project.projectA", "project.projectB", "project.projectC");
    assertMergedModuleCompileModuleDepScope("project.projectB", "project.projectA");
    if (GradleVersion.version(gradleVersion).compareTo(GradleVersion.version("2.5")) >= 0) {
      assertModuleModuleDepScope("project.projectC", "project.projectA", DependencyScope.PROVIDED);
    }
    assertModuleModuleDepScope("project.projectC", "project.projectB", DependencyScope.PROVIDED);
  }

  @Test
  public void testProjectConfigurationDependencyWithDependencyOnTestOutput() throws Exception {
    createSettingsFile("""
                         include 'project1'
                         include 'project2'
                         """);

    String testCompileConfiguration = isJavaLibraryPluginSupported() ? "testImplementation" : "testCompile";
    importProject(
      createBuildScriptBuilder()
        .project(":project1", it -> {
          it.withJavaPlugin()
            .addPrefix("configurations {",
                       "  testOutput",
                       "  testOutput.extendsFrom (" + testCompileConfiguration + ")",
                       "}")
            .addDependency("testOutput", it.code("sourceSets.test.output"))
            .addTestImplementationDependency("junit:junit:4.11");
        })
        .project(":project2", it -> {
          it.withJavaPlugin()
            .addImplementationDependency(it.code("project(path: ':project1')"))
            .addTestImplementationDependency("junit:junit:4.11")
            .addTestImplementationDependency(it.project(":project1", "testOutput"));
        })
        .generate()
    );

    assertModules("project",
                  "project.project1", "project.project1.main", "project.project1.test",
                  "project.project2", "project.project2.main", "project.project2.test");

    assertModuleModuleDepScope("project.project1.test", "project.project1.main", DependencyScope.COMPILE);
    assertModuleLibDepScope("project.project1.test", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);
    assertModuleLibDepScope("project.project1.test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);

    assertModuleModuleDepScope("project.project2.main", "project.project1.main", DependencyScope.COMPILE);

    assertModuleModuleDepScope("project.project2.test", "project.project2.main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project.project2.test", "project.project1.test", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project.project2.test", "project.project1.main", DependencyScope.COMPILE);
    assertModuleLibDepScope("project.project2.test", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);
    assertModuleLibDepScope("project.project2.test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
  }

  @TargetVersions("2.5+")
  @Test
  public void testJavadocAndSourcesForDependencyWithMultipleArtifacts() throws Exception {
    createProjectSubFile("repo/depGroup/depArtifact/1.0-SNAPSHOT/ivy-1.0-SNAPSHOT.xml",
                         """
                           <?xml version="1.0" encoding="UTF-8"?>
                           <ivy-module version="2.0" xmlns:m="http://ant.apache.org/ivy/maven">
                             <info organisation="depGroup" module="depArtifact" revision="1.0-SNAPSHOT" status="integration" publication="20170817121528"/>
                             <configurations>
                               <conf name="compile" visibility="public"/>
                               <conf name="default" visibility="public" extends="compile"/>
                               <conf name="sources" visibility="public"/>
                               <conf name="javadoc" visibility="public"/>
                             </configurations>
                             <publications>
                               <artifact name="depArtifact" type="jar" ext="jar" conf="compile"/>
                               <artifact name="depArtifact" type="source" ext="jar" conf="sources" m:classifier="sources"/>
                               <artifact name="depArtifact" type="javadoc" ext="jar" conf="javadoc" m:classifier="javadoc"/>
                               <artifact name="depArtifact-api" type="javadoc" ext="jar" conf="javadoc" m:classifier="javadoc"/>
                               <artifact name="depArtifact-api" type="source" ext="jar" conf="sources" m:classifier="sources"/>
                             </publications>
                             <dependencies/>
                           </ivy-module>
                           """);
    VirtualFile classesJar = createProjectJarSubFile("repo/depGroup/depArtifact/1.0-SNAPSHOT/depArtifact-1.0-SNAPSHOT.jar");
    VirtualFile javadocJar = createProjectJarSubFile("repo/depGroup/depArtifact/1.0-SNAPSHOT/depArtifact-1.0-SNAPSHOT-javadoc.jar");
    VirtualFile sourcesJar = createProjectJarSubFile("repo/depGroup/depArtifact/1.0-SNAPSHOT/depArtifact-1.0-SNAPSHOT-sources.jar");
    createProjectJarSubFile("repo/depGroup/depArtifact/1.0-SNAPSHOT/depArtifact-api-1.0-SNAPSHOT.jar");
    createProjectJarSubFile("repo/depGroup/depArtifact/1.0-SNAPSHOT/depArtifact-api-1.0-SNAPSHOT-javadoc.jar");
    createProjectJarSubFile("repo/depGroup/depArtifact/1.0-SNAPSHOT/depArtifact-api-1.0-SNAPSHOT-sources.jar");

    importProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .addPrefix("repositories { ivy { url file('repo') } }")
        .addImplementationDependency("depGroup:depArtifact:1.0-SNAPSHOT")
        .withIdeaPlugin()
        .addPrefix("idea.module.downloadJavadoc true")
        .generate()
    );

    assertModules("project", "project.main", "project.test");

    assertModuleModuleDepScope("project.test", "project.main", DependencyScope.COMPILE);

    final String depName = "Gradle: depGroup:depArtifact:1.0-SNAPSHOT";
    assertModuleLibDep("project.main", depName, classesJar.getUrl(), sourcesJar.getUrl(), javadocJar.getUrl());
    assertModuleLibDepScope("project.main", depName, DependencyScope.COMPILE);
    assertModuleLibDep("project.test", depName, classesJar.getUrl(), sourcesJar.getUrl(), javadocJar.getUrl());
    assertModuleLibDepScope("project.test", depName, DependencyScope.COMPILE);

    importProjectUsingSingeModulePerGradleProject();
    assertModules("project");

    // Gradle built-in models has been fixed since 2.3 version, https://issues.gradle.org/browse/GRADLE-3170
    if (GradleVersion.version(gradleVersion).compareTo(GradleVersion.version("2.3")) >= 0) {
      assertModuleLibDep("project", depName, classesJar.getUrl(), sourcesJar.getUrl(), javadocJar.getUrl());
    }
    assertMergedModuleCompileLibDepScope("project", depName);
  }

  @Test
  @TargetVersions("4.6+")
  public void testAnnotationProcessorDependencies() throws Exception {
    importProject(
      """
        apply plugin: 'java'

        dependencies {
            compileOnly 'org.projectlombok:lombok:1.16.2'
            testCompileOnly 'org.projectlombok:lombok:1.16.2'
            annotationProcessor 'org.projectlombok:lombok:1.16.2'
        }
        """);

    final String depName = "Gradle: org.projectlombok:lombok:1.16.2";
    assertModuleLibDepScope("project.main", depName, DependencyScope.PROVIDED);
  }

  @Test // https://youtrack.jetbrains.com/issue/IDEA-223152
  @TargetVersions("5.3+")
  public void testTransformedProjectDependency() throws Exception {
    createSettingsFile("""
                         include 'lib-1'
                         include 'lib-2'
                         """);

    importProject(
      """
        import java.nio.file.Files
        import java.util.zip.ZipEntry
        import java.util.zip.ZipException
        import java.util.zip.ZipFile
        import org.gradle.api.artifacts.transform.TransformParameters

        abstract class Unzip implements TransformAction<TransformParameters.None> {
            @InputArtifact
            abstract Provider<FileSystemLocation> getInputArtifact()

            @Override
            void transform(TransformOutputs outputs) {
                def input = inputArtifact.get().asFile
                def unzipDir = outputs.dir(input.name)
                unzipTo(input, unzipDir)
            }

            private static void unzipTo(File zipFile, File unzipDir) {
                new ZipFile(zipFile).withCloseable { zip ->
                    def outputDirectoryCanonicalPath = unzipDir.canonicalPath
                    for (entry in zip.entries()) {
                        unzipEntryTo(unzipDir, outputDirectoryCanonicalPath, zip, entry)
                    }
                }
            }

            private static unzipEntryTo(File outputDirectory, String outputDirectoryCanonicalPath, ZipFile zip, ZipEntry entry) {
                def output = new File(outputDirectory, entry.name)
                if (!output.canonicalPath.startsWith(outputDirectoryCanonicalPath)) {
                    throw new ZipException("Zip entry '${entry.name}' is outside of the output directory")
                }
                if (entry.isDirectory()) {
                    output.mkdirs()
                } else {
                    output.parentFile.mkdirs()
                    zip.getInputStream(entry).withCloseable { Files.copy(it, output.toPath()) }
                }
            }
        }

        allprojects {
            apply plugin: 'java'
        }

        def processed = Attribute.of('processed', Boolean)
        def artifactType = Attribute.of('artifactType', String)


        dependencies {
            attributesSchema {
                attribute(processed)
            }

            artifactTypes.getByName("jar") {
                attributes.attribute(processed, false)\s
            }

            registerTransform(Unzip) {
                from.attribute(artifactType, 'jar').attribute(processed, false)
                to.attribute(artifactType, 'java-classes-directory').attribute(processed, true)
            }

            implementation project(':lib-1')
            implementation project(':lib-2')
        }


        configurations.all {
            afterEvaluate {
                if (canBeResolved) {
                    attributes.attribute(processed, true)
                }
            }
        }"""
    );

    assertModules("project", "project.main", "project.test",
                  "project.lib-1", "project.lib-1.main", "project.lib-1.test",
                  "project.lib-2", "project.lib-2.main", "project.lib-2.test");

    assertModuleModuleDepScope("project.test", "project.main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project.lib-1.test", "project.lib-1.main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project.lib-2.test", "project.lib-2.main", DependencyScope.COMPILE);

    assertModuleModuleDeps("project.main", "project.lib-1.main", "project.lib-2.main");

    runTask("build");
    importProject();

    assertModules("project", "project.main", "project.test",
                  "project.lib-1", "project.lib-1.main", "project.lib-1.test",
                  "project.lib-2", "project.lib-2.main", "project.lib-2.test");

    assertModuleModuleDepScope("project.test", "project.main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project.lib-1.test", "project.lib-1.main", DependencyScope.COMPILE);
    assertModuleModuleDepScope("project.lib-2.test", "project.lib-2.main", DependencyScope.COMPILE);

    assertModuleModuleDeps("project.main", ArrayUtil.EMPTY_STRING_ARRAY);

    assertModuleLibDeps((actual, expected) -> {
      return actual.contains("build" + File.separatorChar + ".transforms" + File.separatorChar) &&
             new File(actual).getName().equals(new File(expected).getName());
    }, "project.main", "lib-1.jar", "lib-2.jar");
  }

  @Test
  public void testSourcesExcludedFromGradleCacheOnDisabledFlag() throws Exception {
    setRegistryPropertyForTest("gradle.download.sources", "false");
    overrideGradleUserHome("project/cache");
    var dependency = "junit:junit:4.12";
    var dependencyName = "Gradle: junit:junit:4.12";
    var dependencyJar = "junit-4.12.jar";

    importProject(script(it -> {
      it
        .withJavaPlugin()
        .withMavenCentral()
        .addTestImplementationDependency(dependency);
    }));

    LibraryOrderEntry regularLibFromGradleCache = assertSingleLibraryOrderEntry("project.test", dependencyName);
    assertNoSourcesAndDocsInGradleCache(dependencyJar, regularLibFromGradleCache);
  }

  @Test
  public void testSourcesExcludedFromGradleCacheOnDisabledFlagWithIdeaPlugin() throws Exception {
    setRegistryPropertyForTest("gradle.download.sources", "false");
    overrideGradleUserHome("project/cache");
    var dependency = "junit:junit:4.12";
    var dependencyName = "Gradle: junit:junit:4.12";
    var dependencyJar = "junit-4.12.jar";

    importProject(script(it -> {
      it
        .withJavaPlugin()
        .withIdeaPlugin()
        .withMavenCentral()
        .addTestImplementationDependency(dependency);
    }));

    LibraryOrderEntry regularLibFromGradleCache = assertSingleLibraryOrderEntry("project.test", dependencyName);
    assertNoSourcesAndDocsInGradleCache(dependencyJar, regularLibFromGradleCache);
  }

  @Test
  public void testSourcesExcludedFromGradleMultiModuleProjectCacheOnDisabledFlag() throws Exception {
    setRegistryPropertyForTest("gradle.download.sources", "false");
    overrideGradleUserHome("project/cache");
    var dependency = "junit:junit:4.12";
    var dependencyName = "Gradle: junit:junit:4.12";
    var dependencyJar = "junit-4.12.jar";

    createSettingsFile("include 'projectA', 'projectB' ");
    importProject(
      createBuildScriptBuilder()
        .project(":projectA", it -> {
          it
            .withJavaPlugin()
            .withIdeaPlugin()
            .withMavenCentral()
            .addTestImplementationDependency(dependency);
        })
        .project(":projectB", it -> {
          it
            .withJavaPlugin()
            .withMavenCentral()
            .addTestImplementationDependency(dependency);
        })
        .generate()
    );

    LibraryOrderEntry projectADependencyEntry = assertSingleLibraryOrderEntry("project.projectA.test", dependencyName);
    assertNoSourcesAndDocsInGradleCache(dependencyJar, projectADependencyEntry);

    LibraryOrderEntry projectBDependencyEntry = assertSingleLibraryOrderEntry("project.projectB.test", dependencyName);
    assertNoSourcesAndDocsInGradleCache(dependencyJar, projectBDependencyEntry);
  }

  @Test
  public void testSourcesJavadocAttachmentFromGradleCache() throws Exception {
    var dependency = "junit:junit:4.12";
    var dependencyName = "Gradle: junit:junit:4.12";
    var dependencyJar = "junit-4.12.jar";
    var dependencySourcesJar = "junit-4.12-sources.jar";
    var dependencyJavadocJar = "junit-4.12-javadoc.jar";

    importProject(script(it -> {
      it.withJavaPlugin();
      it.withMavenCentral();
      // download classes and sources - the default import settings
      it.addTestImplementationDependency(dependency);
    }));
    assertModules("project", "project.main", "project.test");

    WriteAction.runAndWait(() -> {
      LibraryOrderEntry regularLibFromGradleCache = assertSingleLibraryOrderEntry("project.test", dependencyName);
      Library library = regularLibFromGradleCache.getLibrary();
      ApplicationManager.getApplication().runWriteAction(() -> library.getTable().removeLibrary(library));
    });

    importProject(script(it -> {
      it.withJavaPlugin();
      it.withIdeaPlugin();
      it.withMavenCentral();
      // download classes and sources - the default import settings
      it.addTestImplementationDependency(dependency);
      it.addPrefix(
        "idea.module {",
        "  downloadJavadoc = true",
        "  downloadSources = false", // should be already available in Gradle cache
        "}");
    }));

    assertModules("project", "project.main", "project.test");

    LibraryOrderEntry regularLibFromGradleCache = assertSingleLibraryOrderEntry("project.test", dependencyName);
    assertThat(regularLibFromGradleCache.getRootFiles(OrderRootType.CLASSES))
      .hasSize(1)
      .allSatisfy(file -> assertEquals(dependencyJar, file.getName()));

    String binaryPath = PathUtil.getLocalPath(regularLibFromGradleCache.getRootFiles(OrderRootType.CLASSES)[0]);
    Ref<Boolean> sourceFound = Ref.create(false);
    Ref<Boolean> docFound = Ref.create(false);
    checkIfSourcesOrJavadocsCanBeAttached(binaryPath, sourceFound, docFound);

    if (sourceFound.get()) {
      assertThat(regularLibFromGradleCache.getRootFiles(OrderRootType.SOURCES))
        .hasSize(1)
        .allSatisfy(file -> assertEquals(dependencySourcesJar, file.getName()));
    }
    if (docFound.get()) {
      assertThat(regularLibFromGradleCache.getRootFiles(JavadocOrderRootType.getInstance()))
        .hasSize(1)
        .allSatisfy(file -> assertEquals(dependencyJavadocJar, file.getName()));
    }
  }

  @Test
  @TargetVersions("6.1+")
  public void testSourcesJavadocAttachmentFromClassesFolder() throws Exception {
    createSettingsFile("include 'aLib'");
    createProjectSubFile("aLib/build.gradle",
                         """
                           plugins {
                               id 'java-library'
                               id 'maven-publish'
                           }
                           java {
                               withJavadocJar()
                               withSourcesJar()
                           }
                           publishing {
                               publications {
                                   mavenJava(MavenPublication) {
                                       artifactId = 'aLib'
                                       groupId = 'test'
                                       version = '1.0-SNAPSHOT'
                                       from components.java
                                   }
                                   mavenJava1(MavenPublication) {
                                       artifactId = 'aLib'
                                       groupId = 'test'
                                       version = '1.0-SNAPSHOT-1'
                                       from components.java
                                   }
                                   mavenJava2(MavenPublication) {
                                       artifactId = 'aLib'
                                       groupId = 'test'
                                       version = '1.0-SNAPSHOT-2'
                                       from components.java
                                   }
                               }
                           }
                           configurations {
                               libConf
                           }
                           dependencies {
                               libConf 'test:aLib:1.0-SNAPSHOT'
                           }
                           task moveALibToGradleUserHome() {
                               dependsOn publishToMavenLocal
                               doLast {
                                   repositories.add(repositories.mavenLocal())
                                   def libArtifact = configurations.libConf.singleFile
                                   def libRepoFolder = libArtifact.parentFile.parentFile
                                   ant.move file: libRepoFolder,
                                            todir: new File(gradle.gradleUserHomeDir, '/caches/ij_test_repo/test')
                               }
                           }
                           task removeALibFromGradleUserHome(type: Delete) {
                               delete new File(gradle.gradleUserHomeDir, '/caches/ij_test_repo/test')
                               followSymlinks = true}""");
    importProject(createBuildScriptBuilder()
                    .generate());
    assertModules("project",
                  "project.aLib", "project.aLib.main", "project.aLib.test");

    runTask(":aLib:moveALibToGradleUserHome");
    try {
      importProject(createBuildScriptBuilder()
                      .withJavaPlugin()
                      .withIdeaPlugin()
                      .addRepository(" maven { url new File(gradle.gradleUserHomeDir, 'caches/ij_test_repo')} ")
                      .addDependency("implementation 'test:aLib:1.0-SNAPSHOT-1'")
                      .addPrefix(
                        "idea.module {",
                        "  downloadJavadoc = true",
                        "  downloadSources = false",
                        "}")
                      .generate());
    }
    finally {
      runTask(":aLib:removeALibFromGradleUserHome");
    }

    assertModules("project", "project.main", "project.test",
                  "project.aLib", "project.aLib.main", "project.aLib.test");

    LibraryOrderEntry aLib = assertSingleLibraryOrderEntry("project.test", "Gradle: test:aLib:1.0-SNAPSHOT-1");
    assertThat(aLib.getRootFiles(OrderRootType.CLASSES))
      .hasSize(1)
      .allSatisfy(file -> assertEquals("aLib-1.0-SNAPSHOT-1.jar", file.getName()));
    assertThat(aLib.getRootFiles(OrderRootType.SOURCES))
      .hasSize(1)
      .allSatisfy(file -> assertEquals("aLib-1.0-SNAPSHOT-1-sources.jar", file.getName()));
    assertThat(aLib.getRootFiles(JavadocOrderRootType.getInstance()))
      .hasSize(1)
      .allSatisfy(file -> assertEquals("aLib-1.0-SNAPSHOT-1-javadoc.jar", file.getName()));
  }

  @Test
  public void testModifiedSourceSetClasspathFileCollectionDependencies() throws Exception {
    importProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .addImplementationDependency("junit:junit:4.11")
        .addPrefix("afterEvaluate {",
                   "    def mainSourceSet = sourceSets['main']",
                   "    def mainClassPath = mainSourceSet.compileClasspath",
                   "    def exclusion = mainClassPath.filter { it.name.contains('junit') }",
                   "    mainSourceSet.compileClasspath = mainClassPath - exclusion",
                   "}")
        .generate()
    );

    assertModules("project", "project.main", "project.test");

    assertModuleLibDeps("project.main", "Gradle: junit:junit:4.11", "Gradle: org.hamcrest:hamcrest-core:1.3");
    assertModuleLibDepScope("project.main", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleLibDepScope("project.main", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);

    assertModuleLibDeps("project.test", "Gradle: junit:junit:4.11", "Gradle: org.hamcrest:hamcrest-core:1.3");
    assertModuleLibDepScope("project.test", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.COMPILE);
    assertModuleLibDepScope("project.test", "Gradle: junit:junit:4.11", DependencyScope.COMPILE);
  }

  @Test
  public void testCompilationTaskClasspathDependencies() throws Exception {
    importProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .addPostfix(
          "  configurations {",
          "    custom1",
          "    custom2",
          "  }",
          "  sourceSets {",
          "    customSrc",
          "  }",
          "  dependencies {",
          "    custom1 'junit:junit:4.12'",
          "    custom2 'org.hamcrest:hamcrest-core:1.3'",
          "  }",
          "  compileJava { classpath += configurations.custom1 }",
          "  compileCustomSrcJava {classpath += configurations.custom2 }"
        )
        .generate()
    );

    assertModules("project", "project.main", "project.test", "project.customSrc");
    assertModuleLibDeps("project.test");
    if (isGradleNewerOrSameAs("3.0") && isGradleOlderOrSameAs("4.0")) {
      MultiMap<String, String> expectedVariants = MultiMap.create();
      expectedVariants.putValues("junit", asList("junit:junit:4.12", "junit-4.12.jar"));
      expectedVariants.putValues("hamcrest", asList("org.hamcrest:hamcrest-core:1.3", "hamcrest-core-1.3.jar"));
      BiPredicate<String, String> endsWithVariant = (actual, expected) -> expectedVariants.get(expected).stream().anyMatch(actual::endsWith);

      assertModuleLibDeps(endsWithVariant, "project.main", "junit", "hamcrest");
      assertModuleLibDeps(endsWithVariant, "project.customSrc", "hamcrest");

    } else {
      assertModuleLibDeps("project.main", "Gradle: junit:junit:4.12", "Gradle: org.hamcrest:hamcrest-core:1.3");
      assertModuleLibDeps("project.customSrc", "Gradle: org.hamcrest:hamcrest-core:1.3");
    }
  }

  @Test
  @TargetVersions("7.4+")
  public void testVersionCatalogsModelImport() throws Exception {
    final VirtualFile toml1 = createProjectSubFile("my_versions.toml", "[libraries]\n" +
                                                                      "mylib = \"junit:junit:4.12\"");
    final VirtualFile toml2 = createProjectSubFile("my_versions_2.toml", "[libraries]\n" +
                                                                         "myOtherLib = \"org.hamcrest:hamcrest-core:1.3\"");
    createSettingsFile("""
                         dependencyResolutionManagement {
                             versionCatalogs {
                                 fooLibs {
                                     from(files('my_versions.toml'))
                                 }
                                 barLibs {
                                     from(files('my_versions_2.toml'))
                                 }
                             }
                         }""");
    importProject(createBuildScriptBuilder()
                    .withJavaPlugin()
                    .addPostfix(
                      "dependencies {",
                      "  testImplementation fooLibs.mylib",
                      "  testImplementation barLibs.myOtherLib",
                      "}"
                    ).generate());

    VersionCatalogsLocator locator = myProject.getService(VersionCatalogsLocator.class);
    final Map<String, Path> stringStringMap = locator.getVersionCatalogsForModule(getModule("project.main"));
    assertThat(stringStringMap).containsOnly(entry("fooLibs", Path.of(toml1.getPath())),
                                             entry("barLibs", Path.of(toml2.getPath())));
  }

  @SuppressWarnings("SameParameterValue")
  private LibraryOrderEntry assertSingleLibraryOrderEntry(String moduleName, String depName) {
    List<LibraryOrderEntry> moduleLibDeps = getModuleLibDeps(moduleName, depName);
    assertThat(moduleLibDeps).hasSize(1);
    return moduleLibDeps.iterator().next();
  }

  private void runTask(String task) {
    ExternalSystemTaskId taskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, myProject);
    String projectPath = getProjectPath();
    GradleExecutionSettings settings = new GradleManager().getExecutionSettingsProvider().fun(new Pair<>(myProject, projectPath));
    new GradleTaskManager().executeTasks(
      taskId, Collections.singletonList(task), projectPath, settings, null,
      new ExternalSystemTaskNotificationListenerAdapter() {
        @Override
        public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
          if (stdOut) {
            System.out.print(text);
          }
          else {
            System.err.print(text);
          }
        }
      });
  }

  private void assertNoSourcesAndDocsInGradleCache(String dependencyJar, LibraryOrderEntry regularLibFromGradleCache) {
    assertThat(regularLibFromGradleCache.getRootFiles(OrderRootType.CLASSES))
      .hasSize(1)
      .allSatisfy(file -> assertEquals(dependencyJar, file.getName()));

    String binaryPath = PathUtil.getLocalPath(regularLibFromGradleCache.getRootFiles(OrderRootType.CLASSES)[0]);
    Ref<Boolean> sourceFound = Ref.create(false);
    Ref<Boolean> docFound = Ref.create(false);
    try {
      checkIfSourcesOrJavadocsCanBeAttached(binaryPath, sourceFound, docFound);
    }
    catch (IOException e) {
      throw new IllegalStateException("Unable to lookup dependency artifacts in " + binaryPath);
    }
    assertFalse(sourceFound.get());
    assertFalse(docFound.get());
  }

  private static void checkIfSourcesOrJavadocsCanBeAttached(String binaryPath,
                                                            Ref<Boolean> sourceFound,
                                                            Ref<Boolean> docFound) throws IOException {
    Path binaryFileParent = Paths.get(binaryPath).getParent();
    Path grandParentFile = binaryFileParent.getParent();
    Files.walkFileTree(grandParentFile, EnumSet.noneOf(FileVisitOption.class), 2, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        if (binaryFileParent.equals(dir)) {
          return FileVisitResult.SKIP_SUBTREE;
        }
        return super.preVisitDirectory(dir, attrs);
      }

      @Override
      public FileVisitResult visitFile(Path sourceCandidate, BasicFileAttributes attrs) throws IOException {
        if (!sourceCandidate.getParent().getParent().equals(grandParentFile)) {
          return FileVisitResult.SKIP_SIBLINGS;
        }
        if (attrs.isRegularFile()) {
          String candidateFileName = sourceCandidate.getFileName().toString();
          if (!sourceFound.get() && endsWith(candidateFileName, "-sources.jar")) {
            sourceFound.set(true);
          }
          else if (!docFound.get() && endsWith(candidateFileName, "-javadoc.jar")) {
            docFound.set(true);
          }
        }
        if (sourceFound.get() && docFound.get()) {
          return FileVisitResult.TERMINATE;
        }
        return super.visitFile(sourceCandidate, attrs);
      }
    });
  }
}
