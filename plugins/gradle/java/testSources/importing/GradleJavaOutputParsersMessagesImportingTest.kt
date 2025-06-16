// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.importProject
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test

@Suppress("GrUnresolvedAccess")
class GradleJavaOutputParsersMessagesImportingTest : GradleOutputParsersMessagesImportingTestCase() {

  @Test
  fun `test build script errors on Build`() {
    createSettingsFile("include 'api', 'impl', 'brokenProject' ")
    createBuildFile("impl") {
      addImplementationDependency(project(":api"))
    }
    createProjectSubFile("api/src/main/java/my/pack/Api.java",
                         "package my.pack;\n" +
                         "public interface Api {\n" +
                         "  @Deprecated" +
                         "  public int method();" +
                         "}")
    createProjectSubFile("impl/src/main/java/my/pack/App.java",
                         "package my.pack;\n" +
                         "import my.pack.Api;\n" +
                         "public class App implements Api {\n" +
                         "  public int method() { return 1; }" +
                         "}")
    createProjectSubFile("brokenProject/src/main/java/my/pack/App2.java",
                         "package my.pack;\n" +
                         "import my.pack.Api;\n" +
                         "public class App2 {\n" +
                         "  public int metho d() { return 1; }" +
                         "}")

    importProject {
      subprojects {
        withJavaPlugin()
      }
    }
    assertSyncViewTree {
      assertNode("finished") {
        assertNodeWithDeprecatedGradleWarning()
      }
    }

    var expectedExecutionTree: String
    when {
      isGradleAtLeast("4.7") -> expectedExecutionTree =
        "-\n" +
        " -successful\n" +
        "  :api:compileJava\n" +
        "  :api:processResources\n" +
        "  :api:classes\n" +
        "  :api:jar\n" +
        "  -:impl:compileJava\n" +
        "   -App.java\n" +
        "    uses or overrides a deprecated API.\n" +
        "  :impl:processResources\n" +
        "  :impl:classes"
      else -> expectedExecutionTree =
        "-\n" +
        " -successful\n" +
        "  :api:compileJava\n" +
        "  :api:processResources\n" +
        "  :api:classes\n" +
        "  :api:jar\n" +
        "  :impl:compileJava\n" +
        "  :impl:processResources\n" +
        "  -App.java\n" +
        "   uses or overrides a deprecated API.\n" +
        "  :impl:classes"
    }
    compileModules("project.impl.main")
    assertBuildViewTreeSame(expectedExecutionTree)

    val compilationReportErrors = when {
      isGradleAtLeast("8.14") -> "\n    invalid method declaration; return type required" +
                                 "\n    ';' expected"
      isGradleAtLeast("8.11") -> "\n    ';' expected" +
                                 "\n    invalid method declaration; return type required"
      else -> ""
    }

    when {
      isGradleAtLeast("4.7") -> expectedExecutionTree =
        "-\n" +
        " -failed\n" +
        "  -:brokenProject:compileJava\n" +
        "   -App2.java\n" +
        "    ';' expected\n" +
        "    invalid method declaration; return type required$compilationReportErrors"
      else -> expectedExecutionTree =
        "-\n" +
        " -failed\n" +
        "  :brokenProject:compileJava\n" +
        "  -App2.java\n" +
        "   ';' expected\n" +
        "   invalid method declaration; return type required"
    }
    compileModules("project.brokenProject.main")
    assertBuildViewTreeSame(expectedExecutionTree)
  }

  @Test
  fun `test compilation view tree`() {
    createProjectSources()
    importProject {
      withJavaPlugin()
    }
    assertSyncViewTree {
      assertNode("finished") {
        assertNodeWithDeprecatedGradleWarning()
      }
    }
    compileModules("project.test")
    assertBuildViewTreeEquals("""
                              |-
                              | -successful
                              |  :compileJava
                              |  :processResources
                              |  :classes
                              |  :compileTestJava
                              |  :processTestResources
                              |  :testClasses
                              """.trimMargin())
  }

  @Test
  @TargetVersions("<5.0")
  fun `test unresolved dependencies errors on Build without repositories for legacy Gradle`() {
    createProjectSources()
    importProject {
      withJavaPlugin()
      addTestImplementationDependency("junit:junit:4.12")
    }
    compileModules("project.test")
    assertBuildViewTreeEquals("""
                              |-
                              | -failed
                              |  :compileJava
                              |  :processResources
                              |  :classes
                              |  :compileTestJava
                              |  Could not resolve junit:junit:4.12 because no repositories are defined
                              """.trimMargin())
    assertBuildViewSelectedNode("Could not resolve junit:junit:4.12 because no repositories are defined",
                                """
                                |Could not resolve all files for configuration ':testCompileClasspath'.
                                |> Cannot resolve external dependency junit:junit:4.12 because no repositories are defined.
                                |  Required by:
                                |      project :
                                |
                                |Possible solution:
                                | - Declare repository providing the artifact, see the documentation at https://docs.gradle.org/current/userguide/declaring_repositories.html
                                |
                                |
                                """.trimMargin()
    )
  }

  @Test
  @TargetVersions("5.0+")
  fun `test unresolved dependencies errors on Build without repositories`() {
    createProjectSources()
    importProject {
      withJavaPlugin()
      addTestImplementationDependency("junit:junit:4.12")
    }
    compileModules("project.test")
    assertBuildViewTreeEquals("""
                              |-
                              | -failed
                              |  :compileJava
                              |  :processResources
                              |  :classes
                              |  -:compileTestJava
                              |   Could not resolve junit:junit:4.12 because no repositories are defined
                              """.trimMargin()
    )
    val projectQualifier = if (isGradleAtLeast("8.10")) "root project" else "project"
    assertBuildViewSelectedNode("Could not resolve junit:junit:4.12 because no repositories are defined",
                                """
                                |Execution failed for task ':compileTestJava'.
                                |> Could not resolve all files for configuration ':testCompileClasspath'.
                                |   > Cannot resolve external dependency junit:junit:4.12 because no repositories are defined.
                                |     Required by:
                                |         $projectQualifier :
                                |
                                |Possible solution:
                                | - Declare repository providing the artifact, see the documentation at https://docs.gradle.org/current/userguide/declaring_repositories.html
                                |
                                |
                                """.trimMargin())
  }

  @Test
  @TargetVersions("<5.0")
  fun `test unresolved dependencies errors on Build in offline mode for legacy Gradle`() {
    GradleSettings.getInstance(myProject).isOfflineWork = true
    createProjectSources()
    importProject {
      withJavaPlugin()
      withRepository {
        mavenRepository(MAVEN_REPOSITORY, isGradleAtLeast("6.0"))
      }
      addTestImplementationDependency("junit:junit:4.12")
      addTestImplementationDependency("junit:junit:99.99")
    }
    compileModules("project.test")
    assertBuildViewTreeEquals("""
                              | -
                              | -failed
                              |  :compileJava
                              |  :processResources
                              |  :classes
                              |  :compileTestJava
                              |  Could not resolve junit:junit:99.99
                              """.trimMargin())
    assertBuildViewSelectedNode("Could not resolve junit:junit:99.99",
                                """|Could not resolve all files for configuration ':testCompileClasspath'.
                                   |> Could not resolve junit:junit:99.99.
                                   |  Required by:
                                   |      project :
                                   |   > No cached version of junit:junit:99.99 available for offline mode.
                                   |> Could not resolve junit:junit:99.99.
                                   |  Required by:
                                   |      project :
                                   |   > No cached version of junit:junit:99.99 available for offline mode.
                                   |
                                   |Possible solution:
                                   | - Disable offline mode and rerun the build
                                   |
                                   |
                                   """.trimMargin())
  }

  @Test
  @TargetVersions("5.0+")
  fun `test unresolved dependencies errors on Build in offline mode`() {
    GradleSettings.getInstance(myProject).isOfflineWork = true
    createProjectSources()
    importProject {
      withJavaPlugin()
      withRepository {
        mavenRepository(MAVEN_REPOSITORY, isGradleAtLeast("6.0"))
      }
      addTestImplementationDependency("junit:junit:99.99")
    }
    compileModules("project.test")
    assertBuildViewTree {
      assertNode("failed") {
        assertNode(":compileJava")
        assertNode(":processResources")
        assertNode(":classes")
        assertNode(":compileTestJava") {
          assertNode("Could not resolve junit:junit:99.99")
        }
      }
    }
    val projectQualifier = if (isGradleAtLeast("8.10")) "root project" else "project"
    assertBuildViewSelectedNode("Could not resolve junit:junit:99.99", """
      |Execution failed for task ':compileTestJava'.
      |> Could not resolve all files for configuration ':testCompileClasspath'.
      |   > Could not resolve junit:junit:99.99.
      |     Required by:
      |         $projectQualifier :
      |      > No cached version of junit:junit:99.99 available for offline mode.
      |
      |Possible solution:
      | - Disable offline mode and rerun the build
      |
      |
    """.trimMargin())
  }

  @Test
  @TargetVersions("<5.0")
  fun `test unresolved dependencies errors on Build for legacy Gradle`() {
    createProjectSources()
    importProject {
      withJavaPlugin()
      withRepository {
        mavenRepository(MAVEN_REPOSITORY, isGradleAtLeast("6.0"))
      }
      addTestImplementationDependency("junit:junit:4.12")
      addTestImplementationDependency("junit:junit:99.99")
    }
    compileModules("project.test")
    assertBuildViewTreeEquals("""
                              | -
                              | -failed
                              |  :compileJava
                              |  :processResources
                              |  :classes
                              |  :compileTestJava
                              |  Could not resolve junit:junit:99.99
                              """.trimMargin()
    )
    val repositoryPrefix = if (isGradleOlderThan("4.8")) " " else "-"
    assertBuildViewSelectedNode("Could not resolve junit:junit:99.99",
                                """Could not resolve all files for configuration ':testCompileClasspath'.
                                |> Could not find junit:junit:99.99.
                                |  Searched in the following locations:
                                |    $repositoryPrefix $MAVEN_REPOSITORY/junit/junit/99.99/junit-99.99.pom
                                |    $repositoryPrefix $MAVEN_REPOSITORY/junit/junit/99.99/junit-99.99.jar
                                |  Required by:
                                |      project :
                                |> Could not find junit:junit:99.99.
                                |  Searched in the following locations:
                                |    $repositoryPrefix $MAVEN_REPOSITORY/junit/junit/99.99/junit-99.99.pom
                                |    $repositoryPrefix $MAVEN_REPOSITORY/junit/junit/99.99/junit-99.99.jar
                                |  Required by:
                                |      project :
                                |
                                |Possible solution:
                                | - Declare repository providing the artifact, see the documentation at https://docs.gradle.org/current/userguide/declaring_repositories.html
                                |
                                |
                                """.trimMargin())
  }

  @Test
  @TargetVersions("5.0+")
  fun `test unresolved dependencies errors on Build`() {
    createProjectSources()
    importProject {
      withJavaPlugin()
      withRepository {
        mavenRepository(MAVEN_REPOSITORY, isGradleAtLeast("6.0"))
      }
      addTestImplementationDependency("junit:junit:4.12")
      addTestImplementationDependency("junit:junit:99.99")
    }
    compileModules("project.test")
    assertBuildViewTreeEquals("""
                              |-
                              | -failed
                              |  :compileJava
                              |  :processResources
                              |  :classes
                              |  -:compileTestJava
                              |   Could not resolve junit:junit:99.99
                              """.trimMargin())
    val projectQualifier = if (isGradleAtLeast("8.10")) "root project" else "project"
    assertBuildViewSelectedNode("Could not resolve junit:junit:99.99",
                                """Execution failed for task ':compileTestJava'.
                                |> Could not resolve all files for configuration ':testCompileClasspath'.
                                |   > Could not find junit:junit:99.99.
                                |     Searched in the following locations:
                                |       - $MAVEN_REPOSITORY/junit/junit/99.99/junit-99.99.pom
                                |       - $MAVEN_REPOSITORY/junit/junit/99.99/junit-99.99.jar
                                |     Required by:
                                |         $projectQualifier :
                                |   > Could not find junit:junit:99.99.
                                |     Searched in the following locations:
                                |       - $MAVEN_REPOSITORY/junit/junit/99.99/junit-99.99.pom
                                |       - $MAVEN_REPOSITORY/junit/junit/99.99/junit-99.99.jar
                                |     Required by:
                                |         $projectQualifier :
                                |
                                |Possible solution:
                                | - Declare repository providing the artifact, see the documentation at https://docs.gradle.org/current/userguide/declaring_repositories.html
                                |
                                |
                                """.trimMargin()
    )
  }

  private fun createProjectSources() {
    createProjectSubFile("src/main/java/my/pack/App.java",
                         "package my.pack;\n" +
                         "public class App {\n" +
                         "  public int method() { return 1; }\n" +
                         "}")
    createProjectSubFile("src/test/java/my/pack/AppTest.java",
                         "package my.pack;\n" +
                         "public class AppTest {\n" +
                         "  public void testMethod() { }\n" +
                         "}")
  }
}
