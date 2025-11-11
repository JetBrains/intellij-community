// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.idea.TestFor
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.util.io.FileUtil
import groovy.json.StringEscapeUtils.escapeJava
import org.assertj.core.api.Assertions.assertThat
import org.gradle.util.GradleVersion.version
import org.jetbrains.jps.model.java.JdkVersionDetector
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.testFramework.util.importProject
import org.jetbrains.plugins.gradle.tooling.annotation.TargetJavaVersion
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test

class GradleOutputParsersMessagesImportingTest : GradleOutputParsersMessagesImportingTestCase() {

  @Test
  fun `test build script errors on Sync`() {
    createSettingsFile("include 'api', 'impl' ")
    createProjectSubDir("api")
    createProjectSubFile("impl/build.gradle",
                         "dependencies {\n" +
                         "   ghostConf project(':api')\n" +
                         "}")
    importProject("subprojects { apply plugin: 'java' }")

    val expectedExecutionTree =
      "-\n" +
      " -failed\n" +
      "  -build.gradle\n" +
      "   Could not find method ghostConf() for arguments [project ':api'] on object of type org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler"
    assertSyncViewTreeEquals(expectedExecutionTree)
  }

  @Test
  fun `test build script plugins errors on Sync`() {
    createProjectSubFile("buildSrc/src/main/java/example/SomePlugin.java",
                         """
                           package example;
                           
                           import org.gradle.api.Plugin;
                           import org.gradle.api.Project;
                           
                           public class SomePlugin implements Plugin<Project> {
                               public void apply(Project project) {
                                   throw new IllegalArgumentException("Something's wrong!");
                               }
                           }
                         """.trimIndent())
    importProject("apply plugin: example.SomePlugin")

    assertSyncViewTree {
      assertNode("failed") {
        assertNode(":buildSrc:compileJava")
        assertNode(":buildSrc:compileGroovy")
        assertNode(":buildSrc:processResources")
        assertNode(":buildSrc:classes")
        assertNode(":buildSrc:jar")
        if (isGradleOlderThan("8.0")) {
          assertNode(":buildSrc:assemble")
          assertNode(":buildSrc:compileTestJava")
          assertNode(":buildSrc:compileTestGroovy")
          assertNode(":buildSrc:processTestResources")
          assertNode(":buildSrc:testClasses")
          assertNode(":buildSrc:test")
          assertNode(":buildSrc:check")
          assertNode(":buildSrc:build")
        }
        assertNode("build.gradle") {
          assertNode("Something's wrong!")
        }
      }
    }

    val filePath = FileUtil.toSystemDependentName(myProjectConfig.path)
    val className = if (isGradleAtLeast("6.8")) "class 'example.SomePlugin'." else "[class 'example.SomePlugin']"

    val tryText = when {
      isGradleAtLeast("9.0") ->
        """|> Run with --stacktrace option to get the stack trace.
                                 |> Run with --debug option to get more log output.
                                 |> Run with --scan to generate a Build Scan (Powered by Develocity).
                                 |> Get more help at https://help.gradle.org."""
      isGradleAtLeast("8.2") ->
        """|> Run with --stacktrace option to get the stack trace.
                                 |> Run with --debug option to get more log output.
                                 |> Run with --scan to get full insights.
                                 |> Get more help at https://help.gradle.org."""
      isGradleAtLeast("7.4") ->
        """|> Run with --stacktrace option to get the stack trace.
                                 |> Run with --debug option to get more log output.
                                 |> Run with --scan to get full insights."""
      else ->
        """|Run with --stacktrace option to get the stack trace. Run with --debug option to get more log output. Run with --scan to get full insights."""
    }
    assertSyncViewSelectedNode("Something's wrong!",
                               """
                                 |Build file '$filePath' line: 1
                                 |
                                 |A problem occurred evaluating root project 'project'.
                                 |> Failed to apply plugin $className
                                 |   > Something's wrong!
                                 |
                                 |* Try:
                                 $tryText
                                 |
                               """.trimMargin())

  }

  @Test
  fun `test unresolved dependencies errors on Sync`() {
    // check sunny case
    importProject {
      withJavaPlugin()
    }
    assertSyncViewTree {
      assertNode("finished") {
        assertNodeWithDeprecatedGradleWarning()
      }
    }

    // check unresolved dependency w/o repositories
    importProject {
      withJavaPlugin()
      addTestImplementationDependency("junit:junit:4.12")
    }
    assertSyncViewTree {
      assertNode("finished") {
        assertNodeWithDeprecatedGradleWarning()
        assertNode("Could Not Resolve junit:junit:4.12 for project:test")
      }
    }
    val projectQualifier = when {
      isGradleAtLeast("9.0") -> "root project 'project'"
      isGradleAtLeast("8.10") -> "root project :"
      else -> "project :"
    }
    assertSyncViewSelectedNode("Could Not Resolve junit:junit:4.12 for project:test",
                               "project:test: Cannot resolve external dependency junit:junit:4.12 because no repositories are defined.\n" +
                               "Required by:\n" +
                               "    $projectQualifier\n" +
                               "\n" +
                               "Possible solution:\n" +
                               " - Declare repository providing the artifact, see the documentation at https://docs.gradle.org/current/userguide/declaring_repositories.html\n" +
                               "\n")

    // successful import when repository is added
    importProject {
      withJavaPlugin()
      withRepository {
        mavenRepository(MAVEN_REPOSITORY, isGradleAtLeast("6.0"))
      }
      addTestImplementationDependency("junit:junit:4.12")
    }
    assertSyncViewTree {
      assertNode("finished") {
        assertNodeWithDeprecatedGradleWarning()
      }
    }

    // check unresolved dependency for offline mode
    GradleSettings.getInstance(myProject).isOfflineWork = true
    importProject {
      withJavaPlugin()
      withRepository {
        mavenRepository(MAVEN_REPOSITORY, isGradleAtLeast("6.0"))
      }
      addTestImplementationDependency("junit:junit:4.12")
      addTestImplementationDependency("junit:junit:99.99")
    }
    assertSyncViewTree {
      assertNode("finished") {
        assertNodeWithDeprecatedGradleWarning()
        assertNode("Could Not Resolve junit:junit:99.99 for project:test")
      }
    }
    assertSyncViewSelectedNode("Could Not Resolve junit:junit:99.99 for project:test",
                               "project:test: No cached version of junit:junit:99.99 available for offline mode.\n" +
                               "\n" +
                               "Possible solution:\n" +
                               " - Disable offline mode and reload the project\n" +
                               "\n")

    // check unresolved dependency for offline mode when merged project used
    GradleSettings.getInstance(myProject).isOfflineWork = true
    currentExternalProjectSettings.isResolveModulePerSourceSet = false
    importProject {
      withJavaPlugin()
      withRepository {
        mavenRepository(MAVEN_REPOSITORY, isGradleAtLeast("6.0"))
      }
      addTestImplementationDependency("junit:junit:4.12")
      addTestImplementationDependency("junit:junit:99.99")
    }
    assertSyncViewTree {
      assertNode("finished") {
        assertNodeWithDeprecatedGradleWarning()
        assertNode("Could Not Resolve junit:junit:99.99 for project")
      }
    }
    assertSyncViewSelectedNode("Could Not Resolve junit:junit:99.99 for project",
                               "project: Could not resolve junit:junit:99.99.\n" +
                               "\n" +
                               "Possible solution:\n" +
                               " - Disable offline mode and reload the project\n" +
                               "\n")

    currentExternalProjectSettings.isResolveModulePerSourceSet = true
    // check unresolved dependency for disabled offline mode
    GradleSettings.getInstance(myProject).isOfflineWork = false
    val itemLinePrefix = if (isGradleOlderThan("4.8")) " " else "-"
    importProject {
      withJavaPlugin()
      withRepository {
        mavenRepository(MAVEN_REPOSITORY, isGradleAtLeast("6.0"))
      }
      addTestImplementationDependency("junit:junit:4.12")
      addTestImplementationDependency("junit:junit:99.99")
    }
    assertSyncViewTree {
      assertNode("finished") {
        assertNodeWithDeprecatedGradleWarning()
        assertNode("Could Not Resolve junit:junit:99.99 for project:test")
      }
    }
    assertSyncViewSelectedNode("Could Not Resolve junit:junit:99.99 for project:test",
                               "project:test: Could not find junit:junit:99.99.\n" +
                               "Searched in the following locations:\n" +
                               "  $itemLinePrefix $MAVEN_REPOSITORY/junit/junit/99.99/junit-99.99.pom\n" +
                               "  $itemLinePrefix $MAVEN_REPOSITORY/junit/junit/99.99/junit-99.99.jar\n" +
                               "Required by:\n" +
                               "    $projectQualifier\n" +
                               "\n" +
                               "Possible solution:\n" +
                               " - Declare repository providing the artifact, see the documentation at https://docs.gradle.org/current/userguide/declaring_repositories.html\n" +
                               "\n")
  }

  @Test
  fun `test unresolved build script dependencies errors on Sync`() {
    val artifacts = when {
      currentGradleBaseVersion in version("4.6")..version("7.4") -> "artifacts"
      currentGradleBaseVersion >= version("8.7") -> "artifacts"
      else -> "files"
    }
    val configurationName = when {
      currentGradleBaseVersion >= version("8.11") -> "classpath"
      else -> ":classpath"
    }

    // check unresolved dependency w/o repositories
    importProject {
      addBuildScriptDependency("classpath 'junit:junit:4.12'")
    }
    assertSyncViewTree {
      assertNode("failed") {
        assertNode("Could Not Resolve junit:junit:4.12 because no repositories are defined")
      }
    }
    val projectQualifier = when {
      isGradleAtLeast("9.0") -> "buildscript of root project 'project'"
      isGradleAtLeast("8.10") -> "root project :"
      else -> "project :"
    }
    assertSyncViewSelectedNode("Could Not Resolve junit:junit:4.12 because no repositories are defined", """
      |A problem occurred configuring root project 'project'.
      |> Could not resolve all $artifacts for configuration '$configurationName'.
      |   > Cannot resolve external dependency junit:junit:4.12 because no repositories are defined.
      |     Required by:
      |         $projectQualifier
      |
      |Possible solution:
      | - Declare repository providing the artifact, see the documentation at https://docs.gradle.org/current/userguide/declaring_repositories.html
      |
      |
    """.trimMargin())

    // successful import when repository is added
    importProject {
      withBuildScriptRepository {
        mavenRepository(MAVEN_REPOSITORY, isGradleAtLeast("6.0"))
      }
      addBuildScriptDependency("classpath 'junit:junit:4.12'")
    }
    assertSyncViewTree {
      assertNode("finished") {
        assertNodeWithDeprecatedGradleWarning()
      }
    }

    // check unresolved dependency for offline mode
    GradleSettings.getInstance(myProject).isOfflineWork = true
    importProject {
      withBuildScriptRepository {
        mavenRepository(MAVEN_REPOSITORY, isGradleAtLeast("6.0"))
      }
      addBuildScriptDependency("classpath 'junit:junit:99.99'")
    }
    assertSyncViewTree {
      assertNode("failed") {
        assertNode("Could Not Resolve junit:junit:99.99")
      }
    }
    assertSyncViewSelectedNode("Could Not Resolve junit:junit:99.99", """
      |A problem occurred configuring root project 'project'.
      |> Could not resolve all $artifacts for configuration '$configurationName'.
      |   > Could not resolve junit:junit:99.99.
      |     Required by:
      |         $projectQualifier
      |      > No cached version of junit:junit:99.99 available for offline mode.
      |
      |Possible solution:
      | - Disable offline mode and rerun the build
      |
      |
    """.trimMargin())
    assertSyncViewRerunActions() // quick fix above uses Sync view 'rerun' action to restart import with changes offline mode

    // check unresolved dependency for disabled offline mode
    val itemLinePrefix = if (isGradleOlderThan("4.8")) " " else "-"
    GradleSettings.getInstance(myProject).isOfflineWork = false
    importProject {
      withBuildScriptRepository {
        mavenRepository(MAVEN_REPOSITORY, isGradleAtLeast("6.0"))
      }
      addBuildScriptDependency("classpath 'junit:junit:99.99'")
    }
    assertSyncViewTree {
      assertNode("failed") {
        assertNode("Could Not Resolve junit:junit:99.99")
      }
    }
    assertSyncViewSelectedNode("Could Not Resolve junit:junit:99.99",
                               "A problem occurred configuring root project 'project'.\n" +
                               "> Could not resolve all $artifacts for configuration '$configurationName'.\n" +
                               "   > Could not find junit:junit:99.99.\n" +
                               "     Searched in the following locations:\n" +
                               "       $itemLinePrefix $MAVEN_REPOSITORY/junit/junit/99.99/junit-99.99.pom\n" +
                               "       $itemLinePrefix $MAVEN_REPOSITORY/junit/junit/99.99/junit-99.99.jar\n" +
                               "     Required by:\n" +
                               "         $projectQualifier\n" +
                               "\n" +
                               "Possible solution:\n" +
                               " - Declare repository providing the artifact, see the documentation at https://docs.gradle.org/current/userguide/declaring_repositories.html\n" +
                               "\n")
  }

  @Test
  fun `test startup build script errors with column info`() {
    importProject {
      withJavaPlugin()
      addTestImplementationDependency(code("group: 'junit', name: 'junit', version: '4.12"))
    }

    when {
      isGradleOlderThan("7.0") -> {
        assertSyncViewTreeEquals("-\n" +
                                 " -failed\n" +
                                 "  -build.gradle\n" +
                                 "   expecting ''', found '\\n'")
      }
      isGradleOlderThan("7.4") -> {
        assertSyncViewTreeEquals("-\n" +
                                 " -failed\n" +
                                 "  -build.gradle\n" +
                                 "   Unexpected input: '{'")
      }
      else -> {
        assertSyncViewTreeEquals("-\n" +
                                 " -failed\n" +
                                 "  -build.gradle\n" +
                                 "   Unexpected character: '\\''")
      }
    }
  }

  @Test
  fun `test startup build script errors without column info`() {
    importProject(
      "projects {}\n" + // expected error
      "plugins { id 'java' }"
    )

    assertSyncViewTreeEquals("-\n" +
                             " -failed\n" +
                             "  -build.gradle\n" +
                             "   only buildscript {}" +
                             (if (isGradleAtLeast("7.4")) {", pluginManagement {}"} else {""}) +
                             " and other plugins {} script blocks are allowed before plugins {} blocks, no other statements are allowed")

  }

  @Test
  fun `test build script errors with stacktrace info`() {
    enableStackTraceImportingOption = true
    importProject(
      "apply plugin: 'java'foo"  // expected syntax error
    )

    assertSyncViewTreeEquals("-\n" +
                             " -failed\n" +
                             "  -build.gradle\n" +
                             "   Cannot get property 'foo' on null object")

    val filePath = FileUtil.toSystemDependentName(myProjectConfig.path)
    assertSyncViewSelectedNode("Cannot get property 'foo' on null object") {
      val trySuggestion = when {
        isGradleAtLeast("9.0") ->
          """|> Run with --debug option to get more log output.
             |> Run with --scan to generate a Build Scan (Powered by Develocity).
             |> Get more help at https://help.gradle.org."""
        isGradleAtLeast("8.2") ->
          """|> Run with --debug option to get more log output.
             |> Run with --scan to get full insights.
             |> Get more help at https://help.gradle.org."""
        isGradleAtLeast("7.4") ->
          """|> Run with --debug option to get more log output.
             |> Run with --scan to get full insights."""
        else ->
          "|Run with --debug option to get more log output. Run with --scan to get full insights."
      }
      assertThat(it).startsWith("""|Build file '$filePath' line: 1
                                   |
                                   |A problem occurred evaluating root project 'project'.
                                   |> Cannot get property 'foo' on null object
                                   |
                                   |* Try:
                                   $trySuggestion
                                   |
                                   |* Exception is:
                                   |org.gradle.api.GradleScriptException: A problem occurred evaluating root project 'project'.""".trimMargin())
    }
  }

  @Test
  fun `test build output empty lines and output without eol at the end`() {
    quietLogLevelImportingOption = true
    val scriptOutputText = "script \noutput\n\ntext\n"
    val scriptOutputTextWOEol = "text w/o eol"
    importProject("""
      print "${escapeJava(scriptOutputText)}"
      print "${escapeJava(scriptOutputTextWOEol)}"
    """.trimIndent())

    assertSyncViewTree {
      assertNode("finished") {
        assertNodeWithDeprecatedGradleWarning()
      }
    }
    assertSyncViewNode("finished") {
      val text = it.lineSequence()
        .dropWhile { s -> s == "Starting Gradle Daemon..."
                          || s.startsWith("Gradle Daemon started in")
                          || s.startsWith("Download ") }
        .joinToString(separator = "\n")

      assertEquals( scriptOutputText + scriptOutputTextWOEol, text)
    }
  }

  @Test
  @TargetVersions("8.8+")
  fun `test build output project using Daemon Jvm criteria`() {
    val jdkHome = requireJdkHome()
    val jdkVersion = JdkVersionDetector.getInstance().detectJdkVersionInfo(jdkHome)!!.version.feature
    createProjectSubFile("gradle.properties", "org.gradle.java.installations.paths=$jdkHome")
    createProjectSubFile("gradle/gradle-daemon-jvm.properties", "toolchainVersion=$jdkVersion")

    overrideGradleUserHome(".gradle")

    importProject()
    assertSyncViewNode("finished") {
      assertThat(it)
        .containsOnlyOnce("Starting Gradle Daemon...")
        .containsOnlyOnce("Gradle Daemon started in")
    }

    importProject()
    assertSyncViewNode("finished") {
      assertThat(it)
        .doesNotContain("Starting Gradle Daemon...")
        .doesNotContain("Gradle Daemon started in")
    }
  }

  @Test
  fun `test log level settings in gradle_dot_properties applied`() {
    createProjectSubFile("gradle.properties", "org.gradle.logging.level=debug")
    importProject("""
      println("=================")
      LogLevel.values().each {
          project.logger.log(it, "Message with level ${'$'}it")
      }
      println("=================")
    """.trimIndent())

    assertSyncViewTree {
      assertNode("finished") {
        assertNodeWithDeprecatedGradleWarning()
      }
    }

    assertSyncViewNode("finished") {
      assertThat(it)
        .contains("Message with level DEBUG",
                  "Message with level INFO",
                  "Message with level LIFECYCLE",
                  "Message with level WARN",
                  "Message with level QUIET")
    }
  }

  @Test
  @TargetVersions("6.0+", "<9.0.0")
  @TargetJavaVersion(value = "<17", reason = "JUnit 6 requires Java 17 or newer")
  @TestFor(issues = ["IDEA-380461"])
  fun `test unresolved dependencies errors on Sync due to outdated Gradle JVM`() {
    val gradleJvmInfo = JdkVersionDetector.getInstance().detectJdkVersionInfo(gradleJdkHome!!)!!
    val javaSdkVersion = JavaSdkVersion.fromJavaVersion(gradleJvmInfo.version)!!.description

    importProject {
      withJavaPlugin()
      withRepository {
        mavenRepository(MAVEN_REPOSITORY, isGradleAtLeast("6.0"))
      }
      addTestImplementationDependency("org.junit.jupiter:junit-jupiter:6.0.0")
    }
    assertSyncViewTree {
      assertNode("finished") {
        assertNode("Could Not Resolve org.junit.jupiter:junit-jupiter:6.0.0 for project:test")
        if (isGradleAtLeast("8.10"))
          assertNode("Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0")
      }
    }

    val expectedCauseDescription = when {
      isGradleAtLeast("8.8") -> "Dependency resolution is looking for a library compatible with JVM runtime version $javaSdkVersion, but 'org.junit.jupiter:junit-jupiter:6.0.0' is only compatible with JVM runtime version 17 or newer."

      isGradleAtLeast("8.7") -> """
        No matching variant of org.junit.jupiter:junit-jupiter:6.0.0 was found. The consumer was configured to find a library for use during compile-time, compatible with Java $javaSdkVersion, preferably in the form of class files, preferably optimized for standard JVMs, and its dependencies declared externally but:
          - Variant 'apiElements' declares a library for use during compile-time, packaged as a jar, and its dependencies declared externally:
              - Incompatible because this component declares a component, compatible with Java 17 and the consumer needed a component, compatible with Java $javaSdkVersion
          - Variant 'javadocElements' declares a component for use during runtime, and its dependencies declared externally:
              - Incompatible because this component declares documentation and the consumer needed a library
          - Variant 'runtimeElements' declares a library for use during runtime, packaged as a jar, and its dependencies declared externally:
              - Incompatible because this component declares a component, compatible with Java 17 and the consumer needed a component, compatible with Java $javaSdkVersion
          - Variant 'sourcesElements' declares a component for use during runtime, and its dependencies declared externally:
              - Incompatible because this component declares documentation and the consumer needed a library
      """.trimIndent()

      isGradleAtLeast("8.0") -> """
        No matching variant of org.junit.jupiter:junit-jupiter:6.0.0 was found. The consumer was configured to find a library for use during compile-time, compatible with Java $javaSdkVersion, preferably in the form of class files, preferably optimized for standard JVMs, and its dependencies declared externally but:
          - Variant 'apiElements' capability org.junit.jupiter:junit-jupiter:6.0.0 declares a library for use during compile-time, packaged as a jar, and its dependencies declared externally:
              - Incompatible because this component declares a component, compatible with Java 17 and the consumer needed a component, compatible with Java $javaSdkVersion
          - Variant 'javadocElements' capability org.junit.jupiter:junit-jupiter:6.0.0 declares a component for use during runtime, and its dependencies declared externally:
              - Incompatible because this component declares documentation and the consumer needed a library
          - Variant 'runtimeElements' capability org.junit.jupiter:junit-jupiter:6.0.0 declares a library for use during runtime, packaged as a jar, and its dependencies declared externally:
              - Incompatible because this component declares a component, compatible with Java 17 and the consumer needed a component, compatible with Java $javaSdkVersion
          - Variant 'sourcesElements' capability org.junit.jupiter:junit-jupiter:6.0.0 declares a component for use during runtime, and its dependencies declared externally:
              - Incompatible because this component declares documentation and the consumer needed a library
      """.trimIndent()

      isGradleAtLeast("7.0") -> """
        No matching variant of org.junit.jupiter:junit-jupiter:6.0.0 was found. The consumer was configured to find an API of a library compatible with Java $javaSdkVersion, preferably in the form of class files, preferably optimized for standard JVMs, and its dependencies declared externally but:
          - Variant 'apiElements' capability org.junit.jupiter:junit-jupiter:6.0.0 declares an API of a library, packaged as a jar, and its dependencies declared externally:
              - Incompatible because this component declares a component compatible with Java 17 and the consumer needed a component compatible with Java $javaSdkVersion
          - Variant 'javadocElements' capability org.junit.jupiter:junit-jupiter:6.0.0 declares a runtime of a component, and its dependencies declared externally:
              - Incompatible because this component declares documentation and the consumer needed a library
          - Variant 'runtimeElements' capability org.junit.jupiter:junit-jupiter:6.0.0 declares a runtime of a library, packaged as a jar, and its dependencies declared externally:
              - Incompatible because this component declares a component compatible with Java 17 and the consumer needed a component compatible with Java $javaSdkVersion
          - Variant 'sourcesElements' capability org.junit.jupiter:junit-jupiter:6.0.0 declares a runtime of a component, and its dependencies declared externally:
              - Incompatible because this component declares documentation and the consumer needed a library
      """.trimIndent()

      isGradleAtLeast("6.4") -> """
        No matching variant of org.junit.jupiter:junit-jupiter:6.0.0 was found. The consumer was configured to find an API of a library compatible with Java $javaSdkVersion, preferably in the form of class files, and its dependencies declared externally but:
          - Variant 'apiElements' capability org.junit.jupiter:junit-jupiter:6.0.0 declares an API of a library, packaged as a jar, and its dependencies declared externally:
              - Incompatible because this component declares a component compatible with Java 17 and the consumer needed a component compatible with Java $javaSdkVersion
          - Variant 'javadocElements' capability org.junit.jupiter:junit-jupiter:6.0.0 declares a runtime of a component, and its dependencies declared externally:
              - Incompatible because this component declares documentation and the consumer needed a library
          - Variant 'runtimeElements' capability org.junit.jupiter:junit-jupiter:6.0.0 declares a runtime of a library, packaged as a jar, and its dependencies declared externally:
              - Incompatible because this component declares a component compatible with Java 17 and the consumer needed a component compatible with Java $javaSdkVersion
          - Variant 'sourcesElements' capability org.junit.jupiter:junit-jupiter:6.0.0 declares a runtime of a component, and its dependencies declared externally:
              - Incompatible because this component declares documentation and the consumer needed a library
      """.trimIndent()

      isGradleAtLeast("6.2") -> """
        Unable to find a matching variant of org.junit.jupiter:junit-jupiter:6.0.0:
          - Variant 'apiElements' capability org.junit.jupiter:junit-jupiter:6.0.0:
              - Incompatible attribute:
                  - Required org.gradle.jvm.version '$javaSdkVersion' and found incompatible value '17'.
          - Variant 'javadocElements' capability org.junit.jupiter:junit-jupiter:6.0.0:
              - Incompatible attribute:
                  - Required org.gradle.category 'library' and found incompatible value 'documentation'.
          - Variant 'runtimeElements' capability org.junit.jupiter:junit-jupiter:6.0.0:
              - Incompatible attribute:
                  - Required org.gradle.jvm.version '$javaSdkVersion' and found incompatible value '17'.
          - Variant 'sourcesElements' capability org.junit.jupiter:junit-jupiter:6.0.0:
              - Incompatible attribute:
                  - Required org.gradle.category 'library' and found incompatible value 'documentation'.
      """.trimIndent()

      else -> "Dependency resolution is looking for a library compatible with JVM runtime version $javaSdkVersion, but JUnit 6 is only compatible with JVM runtime version 17 or newer."
    }

    assertSyncViewSelectedNode(
      "Could Not Resolve org.junit.jupiter:junit-jupiter:6.0.0 for project:test",
      """
      project:test: ${expectedCauseDescription.lines().joinToString("\n      ")}
      
      Possible solution:
       - Use Java 17 or newer as Gradle JVM: Open Gradle settings
      
      
      """.trimIndent()
    )
  }
}
