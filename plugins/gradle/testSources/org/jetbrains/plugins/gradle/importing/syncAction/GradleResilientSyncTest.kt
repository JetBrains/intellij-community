// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing.syncAction

import com.intellij.openapi.project.Project
import com.intellij.platform.testFramework.assertion.moduleAssertion.DependencyAssertions.assertModuleDependencies
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModuleEntity
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModules
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.useProjectAsync
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.annotations.processors.CsvCrossProductArgumentsProcessor
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.projectInfo.GradleModuleInfoBuilder
import org.jetbrains.plugins.gradle.testFramework.projectInfo.GradleProjectInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.GradleProjectInfoAssertions.assertPartialProjectStructure
import org.jetbrains.plugins.gradle.testFramework.projectInfo.GradleProjectInfoAssertions.assertProjectStructure
import org.jetbrains.plugins.gradle.testFramework.projectInfo.GradleProjectInfoAssertions.collectProjectInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.GradleProjectInfoBuilder
import org.jetbrains.plugins.gradle.testFramework.projectInfo.file
import org.jetbrains.plugins.gradle.testFramework.projectInfo.gradleProjectInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.gradleWrapper
import org.jetbrains.plugins.gradle.testFramework.projectInfo.initProject
import org.jetbrains.plugins.gradle.testFramework.projectInfo.simpleJavaModuleInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.simpleJavaRootModuleInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.simpleKotlinDslRootModuleInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.simpleSettingsFile
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.support.ParameterDeclarations

@TestApplication
@ParameterizedClass
@AllGradleVersionsSource
@TargetVersions("9.3+")
@RegistryKey("gradle.use.resilient.model.fetch", true.toString())
class GradleResilientSyncTest(private val gradleVersion: GradleVersion) {

  private val testPath by tempPathFixture()
  private val gradle by gradleFixture(gradleVersion)

  enum class BrokenProject {
    ROOT_PROJECT,
    INCLUDED_BUILD,
    BUILD_LOGIC,
    BUILD_SRC
  }

  enum class BrokenFile {
    SETTINGS_SCRIPT,
    BUILD_SCRIPT,
    SOURCE_FILE
  }

  private class TestArgumentsProvider : ArgumentsProvider {
    override fun provideArguments(parameters: ParameterDeclarations, context: ExtensionContext) =
      CsvCrossProductArgumentsProcessor.crossProductArguments(listOf(BrokenProject.entries, BrokenFile.entries))
  }

  @ParameterizedTest(name = "[{index}] {0} has errors in {1}")
  @ArgumentsSource(TestArgumentsProvider::class)
  fun `test resilient sync when`(brokenProject: BrokenProject, brokenFile: BrokenFile) = runBlocking {
    val projectInfo = gradleProjectInfo(gradleVersion, "project") {
      gradleWrapper()
      configureJavaProjectInfoWithUtils(
        settingsFile = {
          pluginManagement {
            call("includeBuild", "build-logic")
            if (brokenProject == BrokenProject.BUILD_LOGIC) {
              call("includeBuild", "broken-build-logic")
            }
          }
          includeBuild("included-build")
          if (brokenProject == BrokenProject.INCLUDED_BUILD) {
            includeBuild("broken-included-build")
          }
          if (brokenProject == BrokenProject.ROOT_PROJECT) {
            configureBrokenSettingsScript(brokenFile)
          }
        },
        buildFile = {
          withPlugin("org.example.plugin")
          if (brokenProject == BrokenProject.BUILD_LOGIC) {
            withPlugin("org.example.broken-plugin")
          }
          if (brokenProject == BrokenProject.ROOT_PROJECT) {
            configureBrokenBuildScript(brokenFile)
          }
        },
      )
      if (brokenProject == BrokenProject.ROOT_PROJECT) {
        configureBrokenSourceFile(brokenFile)
      }
      if (brokenProject == BrokenProject.BUILD_SRC) {
        compositeInfo("project.buildSrc", "buildSrc") {
          configureJavaProjectInfoWithUtils(
            settingsFile = { configureBrokenSettingsScript(brokenFile) },
            buildFile = { configureBrokenBuildScript(brokenFile) }
          )
          configureBrokenSourceFile(brokenFile)
        }
      }
      else {
        compositeInfo("project.buildSrc", "buildSrc") {
          configureJavaProjectInfoWithUtils()
        }
      }
      if (brokenProject == BrokenProject.BUILD_LOGIC) {
        compositeInfo("broken-build-logic", "broken-build-logic") {
          configureKotlinDslProjectInfoWithUtils(
            "org.example.broken-plugin", "BrokenPlugin",
            settingsFile = { configureBrokenSettingsScript(brokenFile) },
            buildFile = { configureBrokenBuildScript(brokenFile) }
          )
          configureBrokenSourceFile(brokenFile)
        }
      }
      compositeInfo("build-logic", "build-logic") {
        configureKotlinDslProjectInfoWithUtils("org.example.plugin", "ExamplePlugin")
      }
      if (brokenProject == BrokenProject.INCLUDED_BUILD) {
        compositeInfo("broken-included-build", "broken-included-build") {
          configureJavaProjectInfoWithUtils(
            settingsFile = { configureBrokenSettingsScript(brokenFile) },
            buildFile = { configureBrokenBuildScript(brokenFile) },
          )
          configureBrokenSourceFile(brokenFile)
        }
      }
      compositeInfo("included-build", "included-build") {
        configureJavaProjectInfoWithUtils()
      }
    }

    val projectRoot = projectInfo.initProject(testPath)

    gradle.openProject(projectRoot).useProjectAsync { project ->
      when (brokenProject) {
        BrokenProject.ROOT_PROJECT -> when (brokenFile) {
          BrokenFile.SETTINGS_SCRIPT ->
            assertModules(project, "project")
          BrokenFile.BUILD_SCRIPT -> {
            // Unexpected the 'project' Gradle build
            assertPartialProjectStructure(project, projectInfo, "project.buildSrc", "included-build", "build-logic")
            assertPartialUtilsDependencies(project, projectInfo, "project.buildSrc", "included-build", "build-logic")
          }
          BrokenFile.SOURCE_FILE -> {
            assertProjectStructure(project, projectInfo)
            assertUtilsDependencies(project, projectInfo)
          }
        }
        BrokenProject.INCLUDED_BUILD -> when (brokenFile) {
          BrokenFile.SETTINGS_SCRIPT, BrokenFile.BUILD_SCRIPT -> {
            // Unexpected the 'project' and 'broken-include-build' Gradle builds
            assertPartialProjectStructure(project, projectInfo, "project.buildSrc", "included-build", "build-logic")
            assertPartialUtilsDependencies(project, projectInfo, "project.buildSrc", "included-build", "build-logic")
          }
          BrokenFile.SOURCE_FILE -> {
            assertProjectStructure(project, projectInfo)
            assertUtilsDependencies(project, projectInfo)
          }
        }
        BrokenProject.BUILD_LOGIC -> when (brokenFile) {
          BrokenFile.SETTINGS_SCRIPT -> {
            // Unexpected the 'project', 'included-build' and `broken-build-logic` Gradle builds
            assertPartialProjectStructure(project, projectInfo, "project.buildSrc", "build-logic")
            assertPartialUtilsDependencies(project, projectInfo, "project.buildSrc", "build-logic")
          }
          BrokenFile.BUILD_SCRIPT -> {
            // Unexpected the 'project' and `broken-build-logic` Gradle builds
            assertPartialProjectStructure(project, projectInfo, "project.buildSrc", "included-build", "build-logic")
            assertPartialUtilsDependencies(project, projectInfo, "project.buildSrc", "included-build", "build-logic")
          }
          // Most important case in update Gradle version scenario
          BrokenFile.SOURCE_FILE -> {
            // Unexpected the 'project' Gradle build
            assertPartialProjectStructure(project, projectInfo, "project.buildSrc", "included-build", "build-logic", "broken-build-logic")
            assertPartialUtilsDependencies(project, projectInfo, "project.buildSrc", "included-build", "build-logic", "broken-build-logic")
          }
        }
        BrokenProject.BUILD_SRC -> when (brokenFile) {
          BrokenFile.SETTINGS_SCRIPT, BrokenFile.BUILD_SCRIPT -> {
            // Unexpected the 'project' and `buildSrc` Gradle builds
            assertPartialProjectStructure(project, projectInfo, "included-build", "build-logic")
            assertPartialUtilsDependencies(project, projectInfo, "included-build", "build-logic")
          }
          // Most important case in update Gradle version scenario
          BrokenFile.SOURCE_FILE -> {
            // Unexpected the 'project' Gradle build
            assertPartialProjectStructure(project, projectInfo, "project.buildSrc", "included-build", "build-logic")
            assertPartialUtilsDependencies(project, projectInfo, "project.buildSrc", "included-build", "build-logic")
          }
        }
      }
    }
  }

  private fun GradleSettingScriptBuilder<*>.configureBrokenSettingsScript(brokenFile: BrokenFile) {
    if (brokenFile == BrokenFile.SETTINGS_SCRIPT) {
      addCode("includeBuild(")
    }
  }

  private fun GradleBuildScriptBuilder<*>.configureBrokenBuildScript(brokenFile: BrokenFile) {
    if (brokenFile == BrokenFile.BUILD_SCRIPT) {
      addPostfix("""
        |dependencies {
        |  implementation(
      """.trimMargin())
    }
  }

  private fun GradleModuleInfoBuilder.configureBrokenSourceFile(brokenFile: BrokenFile) {
    if (brokenFile == BrokenFile.SOURCE_FILE) {
      file("src/main/java/BrokenJavaClass.java", """
        |public class BrokenJavaClass {
        |  void brokenMethod() {
      """.trimMargin())
      file("src/main/kotlin/BrokenKotlinClass.kt", """
        |class BrokenKotlinClass {
        |  fun brokenMethod() {
      """.trimMargin())
    }
  }

  private fun GradleProjectInfoBuilder.configureJavaProjectInfoWithUtils(
    settingsFile: GradleSettingScriptBuilder<*>.() -> Unit = {},
    buildFile: GradleBuildScriptBuilder<*>.() -> Unit = {},
  ) {
    simpleSettingsFile {
      include("utils")
      settingsFile()
    }
    simpleJavaRootModuleInfo {
      addImplementationDependency(project(":utils"))
      buildFile()
    }
    simpleJavaModuleInfo(
      "$projectName.utils", "utils",
      groupId = "org.example." + projectName.replace('-', '_')
    )
  }

  private fun GradleProjectInfoBuilder.configureKotlinDslProjectInfoWithUtils(
    pluginId: String,
    implementationClass: String,
    settingsFile: GradleSettingScriptBuilder<*>.() -> Unit = {},
    buildFile: GradleBuildScriptBuilder<*>.() -> Unit = {},
  ) {
    simpleSettingsFile {
      include("utils")
      settingsFile()
    }
    simpleKotlinDslRootModuleInfo {
      withGradlePlugin(pluginId, implementationClass)
      addImplementationDependency(project(":utils"))
      buildFile()
    }
    simpleJavaModuleInfo(
      "$projectName.utils", "utils",
      groupId = "org.example." + projectName.replace('-', '_')
    )
    file("src/main/java/$implementationClass.java", """
      |import org.gradle.api.Plugin;
      |import org.gradle.api.Project;
      |
      |public class $implementationClass implements Plugin<Project> {
      |  @Override
      |  public void apply(Project project) {
      |  }
      |}
    """.trimMargin())
  }

  private fun GradleBuildScriptBuilder<*>.withGradlePlugin(pluginId: String, implementationClass: String) {
    withPlugin("java-gradle-plugin")
    withPostfix {
      call("gradlePlugin") {
        call("plugins") {
          call("create", "plugin") {
            assign("id", pluginId)
            assign("implementationClass", implementationClass)
          }
        }
      }
    }
  }

  private fun assertUtilsDependencies(project: Project, vararg projectInfo: GradleProjectInfo) {
    assertUtilsDependencies(project, collectProjectInfo(projectInfo.asList()))
  }

  private fun assertPartialUtilsDependencies(project: Project, projectInfo: GradleProjectInfo, vararg projectNames: String) {
    assertPartialUtilsDependencies(project, listOf(projectInfo), *projectNames)
  }

  private fun assertPartialUtilsDependencies(project: Project, projectInfo: List<GradleProjectInfo>, vararg projectNames: String) {
    assertUtilsDependencies(project, collectProjectInfo(projectInfo, *projectNames))
  }

  private fun assertUtilsDependencies(project: Project, projectInfos: List<GradleProjectInfo>) {
    for (projectInfo in projectInfos) {
      assertModuleEntity(project, projectInfo.projectName + ".main") { module ->
        assertModuleDependencies(module, projectInfo.projectName + ".utils.main")
      }
    }
  }
}
