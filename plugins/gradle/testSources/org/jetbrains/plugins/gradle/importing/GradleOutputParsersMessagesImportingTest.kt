// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.externalSystem.importing.ImportSpec
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.util.io.FileUtil
import groovy.json.StringEscapeUtils.escapeJava
import org.assertj.core.api.Assertions.assertThat
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.junit.Test

@Suppress("GrUnresolvedAccess")
open class GradleOutputParsersMessagesImportingTest : BuildViewMessagesImportingTestCase() {
  val itemLinePrefix by lazy { if (currentGradleVersion < GradleVersion.version("4.8")) " " else "-" }
  val isPerTaskOutputSupported by lazy { currentGradleVersion >= GradleVersion.version("4.7") }
  private var enableStackTraceImportingOption = false
  private var quietLogLevelImportingOption = false

  // do not inject repository
  override fun injectRepo(config: String): String = config

  override fun createImportSpec(): ImportSpec {
    val baseImportSpec = super.createImportSpec()
    val baseArguments = baseImportSpec.arguments
    val importSpecBuilder = ImportSpecBuilder(baseImportSpec)
    if (enableStackTraceImportingOption) {
      if (baseArguments == null || !baseArguments.contains("--stacktrace")) {
        importSpecBuilder.withArguments("${baseArguments} --stacktrace")
      }
    }
    else {
      if (baseArguments != null) {
        importSpecBuilder.withArguments(baseArguments.replace("--stacktrace", ""))
      }
    }
    if (quietLogLevelImportingOption) {
      if (baseArguments == null || !baseArguments.contains("--quiet")) {
        importSpecBuilder.withArguments("${baseArguments} --quiet")
      }
    }
    return importSpecBuilder.build()
  }

  @Test
  fun `test build script errors on Sync`() {
    createSettingsFile("include 'api', 'impl' ")
    createProjectSubFile("impl/build.gradle",
                         "dependencies {\n" +
                         "   ghostConf project(':api')\n" +
                         "}")
    importProject("subprojects { apply plugin: 'java' }")

    val expectedExecutionTree: String
    when {
      currentGradleVersion < GradleVersion.version("2.14") -> expectedExecutionTree =
        "-\n" +
        " -failed\n" +
        "  -build.gradle\n" +
        "   Could not find method ghostConf() for arguments [project ':api'] on project ':impl'"
      else -> expectedExecutionTree =
        "-\n" +
        " -failed\n" +
        "  -build.gradle\n" +
        "   Could not find method ghostConf() for arguments [project ':api'] on object of type org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler"
    }
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

    var expectedExecutionTree: String = "-\n" +
                                        " -failed\n"
    if (isGradleNewerOrSameAs("3.3") &&
        isGradleOlderThan("4.5")) {
      expectedExecutionTree += "  :buildSrc:clean\n"
    }

    if (isGradleNewerOrSameAs("3.3")) {
      expectedExecutionTree += "  :buildSrc:compileJava\n" +
                               "  :buildSrc:compileGroovy\n" +
                               "  :buildSrc:processResources\n" +
                               "  :buildSrc:classes\n" +
                               "  :buildSrc:jar\n" +
                               "  :buildSrc:assemble\n" +
                               "  :buildSrc:compileTestJava\n" +
                               "  :buildSrc:compileTestGroovy\n" +
                               "  :buildSrc:processTestResources\n" +
                               "  :buildSrc:testClasses\n" +
                               "  :buildSrc:test\n" +
                               "  :buildSrc:check\n" +
                               "  :buildSrc:build\n"
    }

    expectedExecutionTree += "  -build.gradle\n" +
                             "   Something's wrong!"
    assertSyncViewTreeEquals(expectedExecutionTree)

    val filePath = FileUtil.toSystemDependentName(myProjectConfig.path)
    val tryScanSuggestion = if (isGradleNewerOrSameAs("4.10")) " Run with --scan to get full insights." else ""
    val className = if (isGradleNewerOrSameAs("6.8")) "class 'example.SomePlugin'." else "[class 'example.SomePlugin']"
    assertSyncViewSelectedNode("Something's wrong!",
                               """
                                 |Build file '$filePath' line: 1
                                 |
                                 |A problem occurred evaluating root project 'project'.
                                 |> Failed to apply plugin $className
                                 |   > Something's wrong!
                                 |
                                 |* Try:
                                 |Run with --stacktrace option to get the stack trace. Run with --debug option to get more log output.$tryScanSuggestion
                                 |
                               """.trimMargin())

  }

  @Test
  fun `test unresolved dependencies errors on Sync`() {
    val buildScript = createBuildScriptBuilder().withJavaPlugin()

    // check sunny case
    importProject(buildScript.generate())
    assertSyncViewTreeEquals("-\n" +
                             " finished")

    // check unresolved dependency w/o repositories
    buildScript.addTestImplementationDependency("junit:junit:4.12")
    importProject(buildScript.generate())
    assertSyncViewTreeEquals("-\n" +
                             " -finished\n" +
                             "  Could not resolve junit:junit:4.12 for project:test")
    assertSyncViewSelectedNode("Could not resolve junit:junit:4.12 for project:test",
                               "project:test: Cannot resolve external dependency junit:junit:4.12 because no repositories are defined.\n" +
                               when {
                                 isNewDependencyResolutionApplicable -> "Required by:\n" +
                                                                        "    project :\n"
                                 else -> ""
                               } +
                               "\n" +
                               "Possible solution:\n" +
                               " - Declare repository providing the artifact, see the documentation at https://docs.gradle.org/current/userguide/declaring_repositories.html\n" +
                               "\n")

    // successful import when repository is added
    buildScript.withMavenCentral(isGradleNewerOrSameAs("6.0"))
    importProject(buildScript.generate())
    assertSyncViewTreeEquals("-\n" +
                             " finished")

    // check unresolved dependency for offline mode
    GradleSettings.getInstance(myProject).isOfflineWork = true
    buildScript.addTestImplementationDependency("junit:junit:99.99")
    importProject(buildScript.generate())
    assertSyncViewTreeEquals("-\n" +
                             " -finished\n" +
                             "  Could not resolve junit:junit:99.99 for project:test")
    assertSyncViewSelectedNode("Could not resolve junit:junit:99.99 for project:test",
                               when {
                                 isNewDependencyResolutionApplicable -> "project:test: No cached version of junit:junit:99.99 available for offline mode.\n"
                                 else -> "project:test: Could not resolve junit:junit:99.99.\n"
                               } +
                               "\n" +
                               "Possible solution:\n" +
                               " - Disable offline mode and reload the project\n" +
                               "\n")

    // check unresolved dependency for offline mode when merged project used
    GradleSettings.getInstance(myProject).isOfflineWork = true
    currentExternalProjectSettings.isResolveModulePerSourceSet = false
    importProject(buildScript.generate())
    assertSyncViewTreeEquals("-\n" +
                             " -finished\n" +
                             "  Could not resolve junit:junit:99.99 for project")
    assertSyncViewSelectedNode("Could not resolve junit:junit:99.99 for project",
                               "project: Could not resolve junit:junit:99.99.\n" +
                               "\n" +
                               "Possible solution:\n" +
                               " - Disable offline mode and reload the project\n" +
                               "\n")

    currentExternalProjectSettings.isResolveModulePerSourceSet = true
    // check unresolved dependency for disabled offline mode
    GradleSettings.getInstance(myProject).isOfflineWork = false
    importProject(buildScript.generate())
    assertSyncViewTreeEquals("-\n" +
                             " -finished\n" +
                             "  Could not resolve junit:junit:99.99 for project:test")
    assertSyncViewSelectedNode("Could not resolve junit:junit:99.99 for project:test",
                               "project:test: Could not find junit:junit:99.99.\n" +
                               "Searched in the following locations:\n" +
                               "  $itemLinePrefix https://repo.labs.intellij.net/repo1/junit/junit/99.99/junit-99.99.pom\n" +
                               "  $itemLinePrefix https://repo.labs.intellij.net/repo1/junit/junit/99.99/junit-99.99.jar\n" +
                               when {
                                 isNewDependencyResolutionApplicable -> "Required by:\n" +
                                                                        "    project :\n"
                                 else -> ""
                               } +
                               "\n" +
                               "Possible solution:\n" +
                               " - Declare repository providing the artifact, see the documentation at https://docs.gradle.org/current/userguide/declaring_repositories.html\n" +
                               "\n")
  }

  @Test
  fun `test unresolved build script dependencies errors on Sync`() {
    val buildScript = createBuildScriptBuilder()
    val requiredByProject = if (currentGradleVersion < GradleVersion.version("3.1")) ":project:unspecified" else "project :"
    val artifacts = when {
      currentGradleVersion < GradleVersion.version("4.0") -> "dependencies"
      currentGradleVersion < GradleVersion.version("4.6") -> "files"
      else -> "artifacts"
    }

    // check unresolved dependency w/o repositories
    buildScript.addBuildScriptDependency("classpath 'junit:junit:4.12'")
    importProject(buildScript.generate())
    assertSyncViewTreeEquals("-\n" +
                             " -failed\n" +
                             "  Could not resolve junit:junit:4.12 because no repositories are defined")
    assertSyncViewSelectedNode("Could not resolve junit:junit:4.12 because no repositories are defined",
                               "A problem occurred configuring root project 'project'.\n" +
                               "> Could not resolve all $artifacts for configuration ':classpath'.\n" +
                               "   > Cannot resolve external dependency junit:junit:4.12 because no repositories are defined.\n" +
                               "     Required by:\n" +
                               "         $requiredByProject\n" +
                               "\n" +
                               "Possible solution:\n" +
                               " - Declare repository providing the artifact, see the documentation at https://docs.gradle.org/current/userguide/declaring_repositories.html\n" +
                               "\n")

    // successful import when repository is added
    buildScript.withBuildScriptMavenCentral(isGradleNewerOrSameAs("6.0"))
    importProject(buildScript.generate())
    assertSyncViewTreeEquals("-\n" +
                             " finished")

    // check unresolved dependency for offline mode
    GradleSettings.getInstance(myProject).isOfflineWork = true
    buildScript.addBuildScriptDependency("classpath 'junit:junit:99.99'")
    importProject(buildScript.generate())
    assertSyncViewTreeEquals("-\n" +
                             " -failed\n" +
                             "  Could not resolve junit:junit:99.99")
    assertSyncViewSelectedNode("Could not resolve junit:junit:99.99",
                               "A problem occurred configuring root project 'project'.\n" +
                               "> Could not resolve all $artifacts for configuration ':classpath'.\n" +
                               "   > Could not resolve junit:junit:99.99.\n" +
                               "     Required by:\n" +
                               "         $requiredByProject\n" +
                               "      > No cached version of junit:junit:99.99 available for offline mode.\n" +
                               "   > Could not resolve junit:junit:99.99.\n" +
                               "     Required by:\n" +
                               "         $requiredByProject\n" +
                               "      > No cached version of junit:junit:99.99 available for offline mode.\n" +
                               "\n" +
                               "Possible solution:\n" +
                               " - Disable offline mode and rerun the build\n" +
                               "\n")
    assertSyncViewRerunActions() // quick fix above uses Sync view 'rerun' action to restart import with changes offline mode

    // check unresolved dependency for disabled offline mode
    GradleSettings.getInstance(myProject).isOfflineWork = false
    importProject(buildScript.generate())
    assertSyncViewTreeEquals("-\n" +
                             " -failed\n" +
                             "  Could not resolve junit:junit:99.99")
    assertSyncViewSelectedNode("Could not resolve junit:junit:99.99",
                               "A problem occurred configuring root project 'project'.\n" +
                               "> Could not resolve all $artifacts for configuration ':classpath'.\n" +
                               "   > Could not find junit:junit:99.99.\n" +
                               "     Searched in the following locations:\n" +
                               "       $itemLinePrefix https://repo.labs.intellij.net/repo1/junit/junit/99.99/junit-99.99.pom\n" +
                               "       $itemLinePrefix https://repo.labs.intellij.net/repo1/junit/junit/99.99/junit-99.99.jar\n" +
                               "     Required by:\n" +
                               "         $requiredByProject\n" +
                               "   > Could not find junit:junit:99.99.\n" +
                               "     Searched in the following locations:\n" +
                               "       $itemLinePrefix https://repo.labs.intellij.net/repo1/junit/junit/99.99/junit-99.99.pom\n" +
                               "       $itemLinePrefix https://repo.labs.intellij.net/repo1/junit/junit/99.99/junit-99.99.jar\n" +
                               "     Required by:\n" +
                               "         $requiredByProject\n" +
                               "\n" +
                               "Possible solution:\n" +
                               " - Declare repository providing the artifact, see the documentation at https://docs.gradle.org/current/userguide/declaring_repositories.html\n" +
                               "\n")
  }

  @Test
  fun `test startup build script errors with column info`() {
    val builder = createBuildScriptBuilder()
    importProject(
      builder
        .withJavaPlugin()
        .addTestImplementationDependency(builder.code("group: 'junit', name: 'junit', version: '4.12"))
        .generate())

    if (isGradleOlderThan("7.0")) {
      assertSyncViewTreeEquals("-\n" +
                               " -failed\n" +
                               "  -build.gradle\n" +
                               "   expecting ''', found '\\n'")
    }
    else {
      assertSyncViewTreeEquals("-\n" +
                               " -failed\n" +
                               "  -build.gradle\n" +
                               "   Unexpected input: '{'")
    }
  }

  @Test
  fun `test startup build script errors without column info`() {
    importProject("projects {}\n" +
                  "plugins { id 'java' }")

    assertSyncViewTreeEquals("-\n" +
                             " -failed\n" +
                             "  -build.gradle\n" +
                             "   only buildscript {} and other plugins {} script blocks are allowed before plugins {} blocks, no other statements are allowed")
  }

  @Test
  fun `test build script errors with stacktrace info`() {
    enableStackTraceImportingOption = true
    importProject("apply plugin: 'java'foo")

    assertSyncViewTreeEquals("-\n" +
                             " -failed\n" +
                             "  -build.gradle\n" +
                             "   Cannot get property 'foo' on null object")

    val filePath = FileUtil.toSystemDependentName(myProjectConfig.path)
    assertSyncViewSelectedNode("Cannot get property 'foo' on null object", true) {
      val tryScanSuggestion = if (isGradleNewerOrSameAs("4.10")) " Run with --scan to get full insights." else ""
      assertThat(it).startsWith("Build file '$filePath' line: 1\n\n" +
                                "A problem occurred evaluating root project 'project'.\n" +
                                "> Cannot get property 'foo' on null object\n" +
                                "\n" +
                                "* Try:\n" +
                                "Run with --debug option to get more log output.$tryScanSuggestion\n" +
                                "\n" +
                                "* Exception is:\n" +
                                "org.gradle.api.GradleScriptException: A problem occurred evaluating root project 'project'.")
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

    assertSyncViewTreeEquals("-\n" +
                             " finished")

    assertSyncViewSelectedNode("finished", false) {
      val text = it!!.lineSequence()
        .dropWhile { s -> s == "Starting Gradle Daemon..."
                          || s.startsWith("Gradle Daemon started in")
                          || s.startsWith("Download ") }
        .joinToString(separator = "\n")

      assertEquals( scriptOutputText + scriptOutputTextWOEol, text)
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

    assertSyncViewTreeEquals("-\n" +
                             " finished")

    assertSyncViewSelectedNode("finished", false) {
      assertThat(it)
        .contains("Message with level DEBUG",
                  "Message with level INFO",
                  "Message with level LIFECYCLE",
                  "Message with level WARN",
                  "Message with level QUIET")
    }
  }
}
