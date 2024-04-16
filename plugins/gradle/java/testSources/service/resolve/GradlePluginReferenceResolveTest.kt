package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.platform.backend.navigation.impl.RawNavigationRequest
import com.intellij.psi.PsiFile
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest

private const val KOTLIN_DSL_PLUGIN_VERSION = "4.2.1"

/**
 * Tests for navigation to Gradle plugin source from a string literal with plugin ID in `plugins` closure.
 * @see <a href="https://docs.gradle.org/current/userguide/custom_plugins.html#sec:precompile_script_plugin">Gradle Precompiled plugins</a>
 */
class GradlePluginReferenceResolveTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `Groovy Precompiled plugin in buildSrc is navigatable`(gradleVersion: GradleVersion) {
    val pluginOnGroovy = "tasks.register('taskFromPlugin') {}"
    // A module containing precompiled plugin requires including a corresponding language support plugin in a module build script
    val buildScriptForPluginModule = """
      |plugins {
      |   id 'groovy-gradle-plugin'
      |}""".trimIndent()

    val buildScript = """
      |plugins {
      |   id '<caret>my-plugin'
      |}
      |tasks.named('taskFromPlugin') {
      |   doLast { println 'taskFromPlugin is available in build.gradle' }
      |}""".trimMargin()
    testEmptyProject(gradleVersion) {
      val pluginFile = writeText("buildSrc/src/main/groovy/my-plugin.gradle", pluginOnGroovy)
      writeText("buildSrc/build.gradle", buildScriptForPluginModule)
      testBuildscript(buildScript) {
        assertPluginReferenceNavigatesTo(pluginFile.path)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `Kotlin Precompiled plugin in buildSrc is navigatable`(gradleVersion: GradleVersion) {
    val pluginOnKotlin = "tasks.register(\"taskFromPlugin\"){}"
    // A module containing precompiled plugin requires including a corresponding language support plugin in a module build script
    val buildScriptForPluginModule = """
      |plugins {
      |    id "org.gradle.kotlin.kotlin-dsl" version "$KOTLIN_DSL_PLUGIN_VERSION"
      |}
      |repositories {
      |    mavenCentral()
      |}""".trimIndent()
    val buildScript = """
      |plugins {
      |   id '<caret>my-plugin'
      |}
      |tasks.named('taskFromPlugin') {
      |   doLast { println 'taskFromPlugin is available in build.gradle' }
      |}""".trimMargin()
    testEmptyProject(gradleVersion) {
      val pluginFile = writeText("buildSrc/src/main/groovy/my-plugin.gradle.kts", pluginOnKotlin)
      writeText("buildSrc/build.gradle", buildScriptForPluginModule)
      testBuildscript(buildScript) {
        assertPluginReferenceNavigatesTo(pluginFile.path)
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
}