// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import org.gradle.util.GradleVersion
import org.gradle.util.GradleVersion.version
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
      this.currentGradleVersion >= version("4.7") -> expectedExecutionTree =
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

    when {
      this.currentGradleVersion >= version("4.7") -> expectedExecutionTree =
        "-\n" +
        " -failed\n" +
        "  -:brokenProject:compileJava\n" +
        "   -App2.java\n" +
        "    ';' expected\n" +
        "    invalid method declaration; return type required"
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
    val files = if (currentGradleVersion < GradleVersion.version("4.0")) "dependencies" else "files"
    val projectName = if (currentGradleVersion < GradleVersion.version("3.1")) ":project:unspecified" else "project :"
    assertBuildViewSelectedNode("Could not resolve junit:junit:4.12 because no repositories are defined",
                                """
                                |Could not resolve all $files for configuration ':testCompileClasspath'.
                                |> Cannot resolve external dependency junit:junit:4.12 because no repositories are defined.
                                |  Required by:
                                |      $projectName
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
    assertBuildViewSelectedNode("Could not resolve junit:junit:4.12 because no repositories are defined",
                                """
                                |Execution failed for task ':compileTestJava'.
                                |> Could not resolve all files for configuration ':testCompileClasspath'.
                                |   > Cannot resolve external dependency junit:junit:4.12 because no repositories are defined.
                                |     Required by:
                                |         project :
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
      withMavenCentral()
      addTestImplementationDependency("junit:junit:4.12")
      addTestImplementationDependency("junit:junit:99.99")
    }
    compileModules("project.test")
    val files = if (currentGradleVersion < GradleVersion.version("4.0")) "dependencies" else "files"
    val projectName = if (currentGradleVersion < GradleVersion.version("3.1")) ":project:unspecified" else "project :"
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
                                """|Could not resolve all $files for configuration ':testCompileClasspath'.
                                   |> Could not resolve junit:junit:99.99.
                                   |  Required by:
                                   |      $projectName
                                   |   > No cached version of junit:junit:99.99 available for offline mode.
                                   |> Could not resolve junit:junit:99.99.
                                   |  Required by:
                                   |      $projectName
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
      withMavenCentral()
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
                              |  -:compileTestJava
                              |   Could not resolve junit:junit:99.99
                              """.trimMargin())
    assertBuildViewSelectedNode("Could not resolve junit:junit:99.99",
                                """|Execution failed for task ':compileTestJava'.
                                   |> Could not resolve all files for configuration ':testCompileClasspath'.
                                   |   > Could not resolve junit:junit:99.99.
                                   |     Required by:
                                   |         project :
                                   |      > No cached version of junit:junit:99.99 available for offline mode.
                                   |   > Could not resolve junit:junit:99.99.
                                   |     Required by:
                                   |         project :
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
      withMavenCentral()
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
    val files = if (currentGradleVersion < GradleVersion.version("4.0")) "dependencies" else "files"
    val projectName = if (currentGradleVersion < GradleVersion.version("3.1")) ":project:unspecified" else "project :"
    val mavenRepositoryAddress = getMavenRepositoryAddress()
    val repositoryPrefix = if (currentGradleVersion < GradleVersion.version("4.8")) " " else "-"
    assertBuildViewSelectedNode("Could not resolve junit:junit:99.99",
                                """Could not resolve all $files for configuration ':testCompileClasspath'.
                                |> Could not find junit:junit:99.99.
                                |  Searched in the following locations:
                                |    $repositoryPrefix $mavenRepositoryAddress/junit/junit/99.99/junit-99.99.pom
                                |    $repositoryPrefix $mavenRepositoryAddress/junit/junit/99.99/junit-99.99.jar
                                |  Required by:
                                |      $projectName
                                |> Could not find junit:junit:99.99.
                                |  Searched in the following locations:
                                |    $repositoryPrefix $mavenRepositoryAddress/junit/junit/99.99/junit-99.99.pom
                                |    $repositoryPrefix $mavenRepositoryAddress/junit/junit/99.99/junit-99.99.jar
                                |  Required by:
                                |      $projectName
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
      withMavenCentral()
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
    val mavenRepositoryAddress = getMavenRepositoryAddress()
    val jarPath = when {
      currentGradleVersion >= GradleVersion.version("6.0") && currentGradleVersion <= GradleVersion.version("8.2") ->
        "\n     If the artifact you are trying to retrieve can be found in the repository but without metadata in 'Maven POM' format, " +
        "you need to adjust the 'metadataSources { ... }' of the repository declaration."
      currentGradleVersion >= GradleVersion.version("8.2") -> ""
      else -> "\n       - $mavenRepositoryAddress/junit/junit/99.99/junit-99.99.jar"
    }
    assertBuildViewSelectedNode("Could not resolve junit:junit:99.99",
                                """Execution failed for task ':compileTestJava'.
                                |> Could not resolve all files for configuration ':testCompileClasspath'.
                                |   > Could not find junit:junit:99.99.
                                |     Searched in the following locations:
                                |       - $mavenRepositoryAddress/junit/junit/99.99/junit-99.99.pom$jarPath
                                |     Required by:
                                |         project :
                                |   > Could not find junit:junit:99.99.
                                |     Searched in the following locations:
                                |       - $mavenRepositoryAddress/junit/junit/99.99/junit-99.99.pom$jarPath
                                |     Required by:
                                |         project :
                                |
                                |Possible solution:
                                | - Declare repository providing the artifact, see the documentation at https://docs.gradle.org/current/userguide/declaring_repositories.html
                                |
                                |
                                """.trimMargin()
    )
  }

  private fun getMavenRepositoryAddress(): String = if (IS_UNDER_TEAMCITY) {
    "https://repo.labs.intellij.net/repo1"
  }
  else {
    if (GradleVersion.version("4.10.0") > currentGradleVersion) {
      "https://repo1.maven.org/maven2"
    }
    else {
      "https://repo.maven.apache.org/maven2"
    }
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
