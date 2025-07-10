// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.compiler;

import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.task.ProjectTaskContext;
import com.intellij.task.ProjectTaskListener;
import com.intellij.task.ProjectTaskManager;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.messages.MessageBusConnection;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.util.PathUtil.toSystemDependentName;
import static java.util.Arrays.asList;

public class GradleDelegatedBuildTest extends GradleDelegatedBuildTestCase {
  @Test
  public void testDependentModulesOutputRefresh() throws Exception {
    createSettingsFile("include 'api', 'impl' ");

    createProjectSubFile("src/main/resources/dir/file.properties");
    createProjectSubFile("src/test/resources/dir/file-test.properties");

    createProjectSubFile("api/src/main/resources/dir/file-api.properties");
    createProjectSubFile("api/src/test/resources/dir/file-api-test.properties");

    createProjectSubFile("impl/src/main/resources/dir/file-impl.properties");
    createProjectSubFile("impl/src/test/resources/dir/file-impl-test.properties");
    TestGradleBuildScriptBuilder builder = createBuildScriptBuilder();
    importProject(
      builder
        .allprojects(TestGradleBuildScriptBuilder::withJavaPlugin)
        .addImplementationDependency(builder.project(":api"))
        .project(":api", p -> {
          p.addImplementationDependency(p.project(":impl"));
        })
        .generate()
    );
    assertModules("project", "project.main", "project.test",
                  "project.api", "project.api.main", "project.api.test",
                  "project.impl", "project.impl.main", "project.impl.test");


    VfsTestUtil.syncRefresh();
    PathsList pathsBeforeMake = new PathsList();
    OrderEnumerator.orderEntries(getModule("project.main")).withoutSdk().recursively().runtimeOnly().classes()
      .collectPaths(pathsBeforeMake);
    assertSameElements(pathsBeforeMake.getPathList(), Collections.emptyList());

    compileModules("project.main");
    PathsList pathsAfterMake = new PathsList();
    OrderEnumerator.orderEntries(getModule("project.main")).withoutSdk().recursively().runtimeOnly().classes().collectPaths(pathsAfterMake);
    assertSameElements(pathsAfterMake.getPathList(),
                       toSystemDependentName(path("build/resources/main")),
                       toSystemDependentName(path("api/build/resources/main")),
                       toSystemDependentName(path("impl/build/resources/main")));

    assertCopied("build/resources/main/dir/file.properties");
    assertNotCopied("build/resources/test/dir/file-test.properties");

    assertCopied("api/build/resources/main/dir/file-api.properties");
    assertNotCopied("api/build/resources/test/dir/file-api-test.properties");

    assertCopied("impl/build/resources/main/dir/file-impl.properties");
    assertNotCopied("impl/build/resources/test/dir/file-impl-test.properties");
  }

  @Test
  @TargetVersions("8.0+")
  public void testDelegationBuildsBuildSrc() throws IOException {
    var buildPath = "build/classes/java";
    var junitTestAnnotation = isGradleAtLeast("4.7") ? "org.junit.jupiter.api.Test" : "org.junit.Test";

    createProjectSubFile("settings.gradle", settingsScript(
      it -> it.setProjectName("project")
    ));
    createProjectSubFile("build.gradle", script(
      it -> it.withJavaPlugin()
        .withJUnit()
    ));
    createProjectSubFile("buildSrc/build.gradle", script(
      it -> it.withJavaPlugin()
        .withJUnit()
    ));
    importProject();
    assertModules("project", "project.main", "project.test",
                  "project.buildSrc", "project.buildSrc.main", "project.buildSrc.test");

    createProjectSubFile("buildSrc/src/main/java/org/example/Main.java", """
        package org.example;
        
        public class Main {
        
          public static void main(String[] args) {
            sayHello();
          }
        
          public static void sayHello() {
            System.out.println("Hello!");
          }
        }
      """);
    createProjectSubFile("buildSrc/src/test/java/org/example/TestCase.java", """
        package org.example;
        
        import %s;
        
        public class TestCase {
        
          @Test
          public void test() {
            System.out.println("Test!");
            Main.sayHello();
          }
        }
      """.formatted(junitTestAnnotation));

    assertDoesntExist(new File(getProjectPath(), "buildSrc/" + buildPath + "/main/org/example/Main.class"));
    assertDoesntExist(new File(getProjectPath(), "buildSrc/" + buildPath + "/test/org/example/TestCase.class"));
    compileModules("project.buildSrc.test");
    assertExists(new File(getProjectPath(), "buildSrc/" + buildPath + "/main/org/example/Main.class"));
    assertExists(new File(getProjectPath(), "buildSrc/" + buildPath + "/test/org/example/TestCase.class"));
  }

  @Test
  public void testDirtyOutputPathsCollection() throws Exception {
    doTestDirtyOutputCollection(false);
  }

  @Test
  public void testDirtyOutputPathsCollectionWithBuildCacheEnabled() throws Exception {
    doTestDirtyOutputCollection(true);
  }

  @Test
  public void testSourceSetsWithNamesContainingSpacesAndHyphens() throws Exception {
    createProjectSubFile("src/main/java/App.java", "public class App {}");
    createProjectSubFile("src/main/resources/dir/file.properties");

    createProjectSubFile("src/test/java/Test.java", "public class Test {}");
    createProjectSubFile("src/test/resources/dir/file-test.properties");

    createProjectSubFile("src/integration-test/java/IntegrationTest.java", "public class IntegrationTest {}");
    createProjectSubFile("src/integration-test/resources/dir/file-integrationTest.properties");

    createProjectSubFile("src/anotherSourceSet/java/AnotherSourceSet.java", "public class AnotherSourceSet {}");
    createProjectSubFile("src/anotherSourceSet/resources/dir/file-anotherSourceSet.properties");

    createProjectSubFile("src/another cool name/java/Spaces.java", "public class Spaces {}");
    createProjectSubFile("src/another cool name/resources/dir/file-Spaces.properties");

    importProject(
      """
        apply plugin: 'java'

        sourceSets {
          'integration-test' {}
          anotherSourceSet {}
          'another cool name' {}
        }
        """
    );
    assertModules("project", "project.main", "project.test",
                  "project.integration-test",
                  "project.anotherSourceSet",
                  "project.another_cool_name");
    compileModules("project.main", "project.test", "project.integration-test", "project.anotherSourceSet", "project.another_cool_name");

    assertCopied("build/classes/java/main/App.class");
    assertNotCopied("build/classes/java/test/AppTest.class");
    assertCopied("build/classes/java/integration-test/IntegrationTest.class");
    assertCopied("build/classes/java/anotherSourceSet/AnotherSourceSet.class");
    assertCopied("build/classes/java/another cool name/Spaces.class");

    assertCopied("build/resources/main/dir/file.properties");
    assertCopied("build/resources/test/dir/file-test.properties");
    assertCopied("build/resources/integration-test/dir/file-integrationTest.properties");
    assertCopied("build/resources/anotherSourceSet/dir/file-anotherSourceSet.properties");
    assertCopied("build/resources/another cool name/dir/file-Spaces.properties");
  }

  private void doTestDirtyOutputCollection(boolean enableBuildCache) throws IOException {
    createSettingsFile("include 'api', 'impl' ");
    if (enableBuildCache) {
      createProjectSubFile("gradle.properties",
                           "org.gradle.caching=true");
    }

    VirtualFile appFile = createProjectSubFile("src/main/java/my/pack/App.java",
                                               """
                                                 package my.pack;
                                                 public class App {
                                                   public int method() { return 42; }}""");
    createProjectSubFile("src/test/java/my/pack/AppTest.java",
                         """
                           package my.pack;
                           public class AppTest {
                             public void test() { new App().method(); }}""");

    createProjectSubFile("api/src/main/java/my/pack/Api.java",
                         """
                           package my.pack;
                           public class Api {
                             public int method() { return 42; }}""");
    createProjectSubFile("api/src/test/java/my/pack/ApiTest.java",
                         "package my.pack;\n" +
                         "public class ApiTest {}");

    createProjectSubFile("impl/src/main/java/my/pack/Impl.java",
                         """
                           package my.pack;
                           import my.pack.Api;
                           public class Impl extends Api {}""");
    createProjectSubFile("impl/src/test/java/my/pack/ImplTest.java",
                         """
                           package my.pack;
                           import my.pack.ApiTest;
                           public class ImplTest extends ApiTest {}""");

    importProject(script(it -> {
      it.allprojects(TestGradleBuildScriptBuilder::withJavaPlugin)
        .addImplementationDependency(it.project(":impl"))
        .project(":impl", p -> {
          p.addImplementationDependency(p.project(":api"));
        });
    }));
    assertModules("project", "project.main", "project.test",
                  "project.api", "project.api.main", "project.api.test",
                  "project.impl", "project.impl.main", "project.impl.test");

    List<String> dirtyOutputRoots = new ArrayList<>();

    MessageBusConnection connection = myProject.getMessageBus().connect(getTestRootDisposable());
    connection.subscribe(ProjectTaskListener.TOPIC, new ProjectTaskListener() {
      @Override
      public void started(@NotNull ProjectTaskContext context) {
        context.enableCollectionOfGeneratedFiles();
      }

      @Override
      public void finished(@NotNull ProjectTaskManager.Result result) {
        result.getContext().getDirtyOutputPaths()
          .ifPresent(paths -> dirtyOutputRoots.addAll(paths.map(PathUtil::toSystemIndependentName).toList()));
      }
    });

    compileModules("project.main");

    List<String> expected = new ArrayList<>(List.of(path("build/classes/java/main"),
                                    path("api/build/classes/java/main"),
                                    path("impl/build/classes/java/main"),
                                    path("api/build/libs/api.jar"),
                                    path("impl/build/libs/impl.jar")));

    if (isGradleAtLeast("5.2")) {
      expected.addAll(asList(path("build/generated/sources/annotationProcessor/java/main"),
                             path("api/build/generated/sources/annotationProcessor/java/main"),
                             path("impl/build/generated/sources/annotationProcessor/java/main")));
    }

    if (isGradleAtLeast("6.3")) {
      expected.addAll(asList(path("build/generated/sources/headers/java/main"),
                             path("api/build/generated/sources/headers/java/main"),
                             path("impl/build/generated/sources/headers/java/main")));
    }

    if (isGradleAtLeast("7.1")) {
      expected.addAll(asList(path("build/tmp/compileJava/previous-compilation-data.bin"),
                             path("api/build/tmp/compileJava/previous-compilation-data.bin"),
                             path("impl/build/tmp/compileJava/previous-compilation-data.bin")));
    }

    Assertions.assertThat(dirtyOutputRoots)
      .containsExactlyInAnyOrderElementsOf(expected);

    assertCopied("build/classes/java/main/my/pack/App.class");
    assertNotCopied("build/classes/java/test/my/pack/AppTest.class");

    assertCopied("api/build/classes/java/main/my/pack/Api.class");
    assertNotCopied("api/build/classes/java/test/my/pack/ApiTest.class");

    assertCopied("impl/build/classes/java/main/my/pack/Impl.class");
    assertNotCopied("impl/build/classes/java/test/my/pack/ImplTest.class");

    //----check incremental make and build dependant module----//
    dirtyOutputRoots.clear();
    setFileContent(appFile, """
      package my.pack;
      public class App {
        public int method() { return 42; }  public int methodX() { return 42; }}""", false);
    compileModules("project.test");

    expected = new ArrayList<>(List.of(path("build/classes/java/main"),
                       path("build/classes/java/test")));

    if (isGradleAtLeast("5.2")) {
      expected.addAll(asList(path("build/generated/sources/annotationProcessor/java/main"),
                             path("build/generated/sources/annotationProcessor/java/test")));
    }

    if (isGradleAtLeast("6.3")) {
      expected.addAll(asList(path("build/generated/sources/headers/java/main"),
                             path("build/generated/sources/headers/java/test")));
    }

    if (isGradleAtLeast("7.1")) {
      expected.addAll(asList(path("build/tmp/compileTestJava/previous-compilation-data.bin"),
                             path("build/tmp/compileJava/previous-compilation-data.bin")));
    }

    Assertions.assertThat(dirtyOutputRoots)
      .containsExactlyInAnyOrderElementsOf(expected);

    assertCopied("build/classes/java/main/my/pack/App.class");
    assertCopied("build/classes/java/test/my/pack/AppTest.class");

    assertCopied("api/build/classes/java/main/my/pack/Api.class");
    assertNotCopied("api/build/classes/java/test/my/pack/ApiTest.class");

    assertCopied("impl/build/classes/java/main/my/pack/Impl.class");
    assertNotCopied("impl/build/classes/java/test/my/pack/ImplTest.class");

    //----check reverted change -> related build result can be obtained by Gradle from cache ---//
    dirtyOutputRoots.clear();
    setFileContent(appFile, """
      package my.pack;
      public class App {
        public int method() { return 42; }}""", false);
    compileModules("project.test");
    assertUnorderedElementsAreEqual(dirtyOutputRoots, expected);
  }
}
