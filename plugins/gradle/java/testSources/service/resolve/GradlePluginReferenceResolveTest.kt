package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.platform.backend.navigation.impl.RawNavigationRequest
import com.intellij.psi.PsiFile
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest

/**
 * Tests for navigation to Gradle plugin source from a string literal with plugin ID in `plugins` closure.
 * @see <a href="https://docs.gradle.org/current/userguide/custom_plugins.html#sec:precompile_script_plugin">Gradle Precompiled plugins</a>
 */
class GradlePluginReferenceResolveTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource("""
    id '<caret>my-plugin',
    id "<caret>my-plugin",
    id ('<caret>my-plugin'),
    id ("<caret>my-plugin")
  """)
  fun `Groovy Precompiled plugin is navigatable`(gradleVersion: GradleVersion, pluginIdStatement: String) {
    val pluginOnGroovy = "tasks.register('taskFromPlugin') {}"
    val buildScript = """
      |plugins {
      |   $pluginIdStatement
      |}""".trimMargin()
    testEmptyProject(gradleVersion) {
      val pluginFile = writeText("buildSrc/src/main/groovy/my-plugin.gradle", pluginOnGroovy)
      testBuildscript(buildScript) {
        assertPluginReferenceNavigatesTo(pluginFile.path)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `Kotlin Precompiled plugin is navigatable`(gradleVersion: GradleVersion) {
    val pluginOnKotlin = "tasks.register(\"taskFromPlugin\"){}"
    val buildScript = """
      |plugins {
      |   id '<caret>my-plugin'
      |}""".trimMargin()
    testEmptyProject(gradleVersion) {
      val pluginFile = writeText("buildSrc/src/main/kotlin/my-plugin.gradle.kts", pluginOnKotlin)
      testBuildscript(buildScript) {
        assertPluginReferenceNavigatesTo(pluginFile.path)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `Kotlin Precompiled plugin with a package is navigatable`(gradleVersion: GradleVersion) {
    val pluginOnKotlin = """
      |package com.example
      |
      |tasks.register(\"taskFromPlugin\"){}
      |""".trimIndent()
    val buildScript = """
      |plugins {
      |   id '<caret>com.example.my-plugin'
      |}""".trimMargin()
    testEmptyProject(gradleVersion) {
      val pluginFile = writeText("buildSrc/src/main/kotlin/com/example/my-plugin.gradle.kts", pluginOnKotlin)
      testBuildscript(buildScript) {
        assertPluginReferenceNavigatesTo(pluginFile.path)
      }
    }
  }

  /**
   * Precompiled plugins on Groovy (with .gradle extension) should contain "/src/main/groovy" in a path.
   * Precompiled plugins on Kotlin (with .gradle.kts extension) should contain "/src/main/kotlin" in a path.
   */
  @ParameterizedTest
  @AllGradleVersionsSource
  fun `Precompiled plugin from wrong directory is not navigatable`(gradleVersion: GradleVersion) {
    val pluginOnGroovy = "tasks.register('taskFromPlugin') {}"
    val buildScript = """
      |plugins {
      |   id '<caret>my-plugin'
      |}""".trimMargin()
    testEmptyProject(gradleVersion) {
      writeText("buildSrc/wrong-directory/my-plugin.gradle", pluginOnGroovy)
      testBuildscript(buildScript) {
        assertPluginIdIsNotNavigatable()
      }
    }
  }

  /**
   * Precompiled script plugin file should have `.gradle` or `.gradle.kts` extension. If there is a file with name = plugin ID, and path
   * matching to precompiled plugin conditions, it could not be a navigation target.
   */
  @ParameterizedTest
  @AllGradleVersionsSource
  fun `file with wrong extension could not be a navigation target`(gradleVersion: GradleVersion) {
    val buildScript = """
      |plugins {
      |   id '<caret>my-plugin'
      |}""".trimMargin()
    testEmptyProject(gradleVersion) {
      writeText("buildSrc/src/main/groovy/my-plugin.groovy", "any file content")
      testBuildscript(buildScript) {
        assertPluginIdIsNotNavigatable()
      }
    }
  }

  private fun assertPluginReferenceNavigatesTo(expectedPluginPath: String) {
    val pluginReference = fixture.findSingleReferenceAtCaret()
    assertNotNull(pluginReference, "A string literal with plugin ID should have a reference")

    val pluginSymbol = pluginReference.resolveReference().filterIsInstance<GradlePluginSymbol>().firstOrNull()
    assertNotNull(pluginSymbol, "GradlePluginSymbol was not found")

    val target = pluginSymbol!!.getNavigationTargets(fixture.project).firstOrNull()
    assertNotNull(target, "GradlePluginSymbol should have a target to navigate")

    val navigationRequest = target!!.navigationRequest() as? RawNavigationRequest
    assertNotNull(navigationRequest, "RawNavigationRequest type is expected for a target navigation request")
    assertTrue(navigationRequest!!.canNavigateToSource)

    val pluginPsiFile = navigationRequest.navigatable as? PsiFile
    assertNotNull(pluginPsiFile, "Unable to find PsiFile for a target plugin")
    assertEquals(expectedPluginPath, pluginPsiFile!!.virtualFile.path)
  }

  private fun assertPluginIdIsNotNavigatable() {
    val pluginReference = fixture.findSingleReferenceAtCaret()
    assertNotNull(pluginReference, "A string literal with plugin ID should have a reference")
    val pluginSymbol = pluginReference.resolveReference().filterIsInstance<GradlePluginSymbol>().firstOrNull()
    assertNull(pluginSymbol, "The plugin reference should not be resolved because plugin file is located in a wrong directory")
  }
}