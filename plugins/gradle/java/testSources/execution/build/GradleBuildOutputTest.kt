// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.openapi.externalSystem.test.compileModules
import com.intellij.testFramework.utils.module.assertModules
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleExecutionTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.junit.jupiter.params.ParameterizedTest

class GradleBuildOutputTest : GradleExecutionTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test build script errors on Build`(gradleVersion: GradleVersion) {
    val fixtureBuilder = GradleTestFixtureBuilder.create("GradleExecutionOutputTest.test build script errors on Build") {
      withSettingsFile {
        setProjectName("project")
        include("api", "impl", "brokenProject")
      }
      withBuildFile(gradleVersion, "api") {
        withJavaPlugin()
      }
      withBuildFile(gradleVersion, "impl") {
        withJavaPlugin()
        addImplementationDependency(project(":api"))
      }
      withBuildFile(gradleVersion, "brokenProject") {
        withJavaPlugin()
      }
      withFile("api/src/main/java/my/pack/Api.java", """
        |package my.pack;
        |public interface Api {
        |  @Deprecated
        |  public int method();
        |}
      """.trimMargin())
      withFile("impl/src/main/java/my/pack/App.java", """
        |package my.pack;
        |import my.pack.Api;
        |public class App implements Api {
        |  public int method() { 
        |    return 1;
        |  }
        |}
      """.trimMargin())
      withFile("brokenProject/src/main/java/my/pack/App2.java", """
        |package my.pack;
        |import my.pack.Api;
        |public class App2 {
        |  public int metho d() { // expected error
        |    return 1;
        |  } 
        |}
      """.trimMargin())
    }
    test(gradleVersion, fixtureBuilder) {
      assertModules(project, "project",
                    "project.api", "project.api.main", "project.api.test",
                    "project.impl", "project.impl.main", "project.impl.test",
                    "project.brokenProject", "project.brokenProject.main", "project.brokenProject.test")

      executeTasks("clean")

      waitForAnyGradleTaskExecution {
        compileModules(project, true, "project.impl.main")
      }
      assertBuildViewTree {
        assertNode("successful") {
          assertNode(":api:compileJava")
          assertNode(":api:processResources")
          assertNode(":api:classes")
          assertNode(":api:jar")
          assertNode(":impl:compileJava") {
            assertNode("App.java", skipIf = !isPerTaskOutputSupported()) {
              assertNode("uses or overrides a deprecated API.")
            }
          }
          assertNode("App.java", skipIf = isPerTaskOutputSupported(), isUnordered = true) {
            assertNode("uses or overrides a deprecated API.")
          }
          assertNode(":impl:processResources")
          assertNode(":impl:classes")
        }
      }

      waitForAnyGradleTaskExecution {
        compileModules(project, true, "project.brokenProject.main")
      }
      assertBuildViewTree {
        assertNode("failed") {
          assertNode(":brokenProject:compileJava") {
            assertNode("App2.java", skipIf = !isPerTaskOutputSupported()) {
              assertNode("';' expected")
              assertNode("invalid method declaration; return type required")
              assertNode("';' expected", skipIf = !isBuildCompilationReportSupported())
              assertNode("invalid method declaration; return type required", skipIf = !isBuildCompilationReportSupported())
            }
          }
          assertNode("App2.java", skipIf = isPerTaskOutputSupported(), isUnordered = true) {
            assertNode("';' expected")
            assertNode("invalid method declaration; return type required")
          }
        }
      }
    }
  }
}