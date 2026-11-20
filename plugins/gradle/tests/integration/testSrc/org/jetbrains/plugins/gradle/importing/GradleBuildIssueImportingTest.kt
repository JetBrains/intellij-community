// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.build.FileNavigatable
import com.intellij.build.issue.BuildIssue
import com.intellij.gradle.testFramework.annotations.AllGradleVersionsSource
import com.intellij.gradle.testFramework.annotations.processors.CsvCrossProductArgumentsProcessor
import com.intellij.gradle.testFramework.fixtures.buildViewFixture
import com.intellij.gradle.testFramework.fixtures.gradleFixture
import com.intellij.gradle.testFramework.fixtures.projectFixture
import com.intellij.gradle.testFramework.projectInfo.GradleProjectInfo
import com.intellij.gradle.testFramework.projectInfo.buildFile
import com.intellij.gradle.testFramework.projectInfo.gradleProjectInfo
import com.intellij.gradle.testFramework.projectInfo.initProject
import com.intellij.gradle.testFramework.projectInfo.simpleSettingsFile
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.openapi.Disposable
import com.intellij.platform.testFramework.assertion.BuildViewAssertions.assertBuildViewTree
import com.intellij.platform.testFramework.assertion.BuildViewNodeAssertion
import com.intellij.platform.testFramework.assertion.assertConsoleText
import com.intellij.platform.testFramework.assertion.assertIsNodeSelected
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.asDisposable
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl.Companion.buildScriptName
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl.Companion.settingsScriptName
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElementBuilder
import org.jetbrains.plugins.gradle.importing.BuildViewMessagesImportingTestCase.Companion.assertNodeWithDeprecatedGradleWarning
import org.jetbrains.plugins.gradle.issue.ConfigurableGradleBuildIssue
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.support.ParameterDeclarations

@TestApplication
@ParameterizedClass
@AllGradleVersionsSource
class GradleBuildIssueImportingTest(private val gradleVersion: GradleVersion) {

  private val testRootFixture = tempPathFixture()
  private val testRoot by testRootFixture

  private val gradleFixture = gradleFixture(gradleVersion)
  private val gradle by gradleFixture

  private val projectFixture = gradleFixture.projectFixture(testRootFixture, numProjectSyncs = 0)
  private val project by projectFixture

  private val buildViewFixture by buildViewFixture(projectFixture)
  private val syncView get() = buildViewFixture.syncView

  enum class BrokenFile {
    SETTINGS_SCRIPT,
    BUILD_SCRIPT,
  }

  private class TestArgumentsProvider : ArgumentsProvider {
    override fun provideArguments(parameters: ParameterDeclarations, context: ExtensionContext) =
      CsvCrossProductArgumentsProcessor.crossProductArguments(listOf(GradleDsl.entries, BrokenFile.entries))
  }

  @ParameterizedTest
  @ArgumentsSource(TestArgumentsProvider::class)
  fun `test build issue checker handles script exception`(gradleDsl: GradleDsl, brokenFile: BrokenFile): Unit = runBlocking {
    registerIssueChecker(TestBuildIssueChecker(TEST_BUILD_ISSUE_TITLE, TEST_BUILD_ISSUE_DESCRIPTION), asDisposable())

    val projectInfo = simpleJavaProjectInfoWithBrokenScript(gradleVersion, gradleDsl, brokenFile)

    val projectRoot = projectInfo.initProject(testRoot)

    gradle.linkProject(project, projectRoot)

    assertBuildViewTree(syncView) {
      assertNode("failed") {
        assertNodeWithDeprecatedGradleWarning(gradleVersion)
        assertBuildFailureNodes(gradleVersion, gradleDsl, brokenFile) {
          assertNode(TEST_BUILD_ISSUE_TITLE) {
            assertIsNodeSelected(false)
            assertConsoleText(TEST_BUILD_ISSUE_DESCRIPTION + "\n\n")
          }
        }
      }
    }
  }

  @ParameterizedTest
  @EnumSource(GradleDsl::class)
  fun `test build issue checker reports all issues for script exception`(gradleDsl: GradleDsl): Unit = runBlocking {
    registerIssueChecker(TestBuildIssueChecker(TEST_BUILD_ISSUE_TITLE, TEST_BUILD_ISSUE_DESCRIPTION), asDisposable())
    registerIssueChecker(TestBuildIssueChecker(TEST_BUILD_ISSUE_TITLE_2, TEST_BUILD_ISSUE_DESCRIPTION_2), asDisposable())

    val projectInfo = simpleJavaProjectInfoWithBrokenScript(gradleVersion, gradleDsl, BrokenFile.BUILD_SCRIPT)

    val projectRoot = projectInfo.initProject(testRoot)

    gradle.linkProject(project, projectRoot)

    assertBuildViewTree(syncView) {
      assertNode("failed") {
        assertNodeWithDeprecatedGradleWarning(gradleVersion)
        assertBuildFailureNodes(gradleVersion, gradleDsl, BrokenFile.BUILD_SCRIPT) {
          assertNode(TEST_BUILD_ISSUE_TITLE) {
            assertIsNodeSelected(false)
            assertConsoleText(TEST_BUILD_ISSUE_DESCRIPTION + "\n\n")
          }
          assertNode(TEST_BUILD_ISSUE_TITLE_2) {
            assertIsNodeSelected(false)
            assertConsoleText(TEST_BUILD_ISSUE_DESCRIPTION_2 + "\n\n")
          }
        }
      }
    }
  }

  private class TestBuildIssueChecker(
    private val issueTitle: String,
    private val issueDescription: String,
  ) : GradleIssueChecker {

    override fun check(issueData: GradleIssueData): BuildIssue? {
      val message = issueData.failure.rootCause.message ?: return null
      if (TEST_BUILD_SCRIPT_EXCEPTION_MESSAGE !in message) return null
      return object : ConfigurableGradleBuildIssue() {}.apply {
        setTitle(issueTitle)
        addDescription(issueDescription)
        setNavigatable { project -> issueData.filePosition?.let { FileNavigatable(project, it) } }
      }
    }
  }

  companion object {
    private const val TEST_BUILD_SCRIPT_EXCEPTION_CLASS = "RuntimeException"
    private const val TEST_BUILD_SCRIPT_EXCEPTION_MESSAGE = "IDEA test build issue marker"
    private const val TEST_BUILD_ISSUE_TITLE = "IDEA-side test build issue"
    private const val TEST_BUILD_ISSUE_DESCRIPTION = "Build issue description from the IDEA-side test checker"
    private const val TEST_BUILD_ISSUE_TITLE_2 = "$TEST_BUILD_ISSUE_TITLE (2)"
    private const val TEST_BUILD_ISSUE_DESCRIPTION_2 = "$TEST_BUILD_ISSUE_DESCRIPTION (2)"
    private const val BUILD_FAILURE_TITLE = "FAILURE: Build failed with an exception."

    private fun registerIssueChecker(checker: GradleIssueChecker, disposable: Disposable) {
      GradleIssueChecker.EP_NAME.point.registerExtension(checker, disposable)
    }

    private fun simpleJavaProjectInfoWithBrokenScript(
      gradleVersion: GradleVersion,
      gradleDsl: GradleDsl,
      brokenFile: BrokenFile,
    ): GradleProjectInfo = gradleProjectInfo(gradleVersion, gradleDsl = gradleDsl) {
      simpleSettingsFile {
        if (brokenFile == BrokenFile.SETTINGS_SCRIPT) {
          addCode { throwTestBuildScriptException(gradleDsl) }
        }
      }
      buildFile {
        if (brokenFile == BrokenFile.BUILD_SCRIPT) {
          withPostfix { throwTestBuildScriptException(gradleDsl) }
        }
      }
    }

    private fun getFailureTitle(gradleVersion: GradleVersion, gradleDsl: GradleDsl, brokenFile: BrokenFile): String =
      when (gradleDsl) {
        GradleDsl.GROOVY -> when (brokenFile) {
          BrokenFile.SETTINGS_SCRIPT -> "A problem occurred evaluating settings 'project'."
          BrokenFile.BUILD_SCRIPT -> "A problem occurred evaluating root project 'project'."
        }
        GradleDsl.KOTLIN -> when {
          brokenFile == BrokenFile.BUILD_SCRIPT && !hasFilePosition(gradleVersion,
                                                                    gradleDsl) -> "A problem occurred configuring root project 'project'."
          else -> TEST_BUILD_SCRIPT_EXCEPTION_MESSAGE
        }
      }

    private fun GradleScriptElementBuilder.throwTestBuildScriptException(gradleDsl: GradleDsl) =
      throwException(gradleDsl, TEST_BUILD_SCRIPT_EXCEPTION_CLASS, TEST_BUILD_SCRIPT_EXCEPTION_MESSAGE)

    private fun GradleScriptElementBuilder.throwException(gradleDsl: GradleDsl, className: String, message: String) =
      when (gradleDsl) {
        GradleDsl.GROOVY -> code("throw new $className('$message')")
        GradleDsl.KOTLIN -> code("throw $className(\"$message\")")
      }

    private fun hasFilePosition(gradleVersion: GradleVersion, gradleDsl: GradleDsl): Boolean =
      gradleDsl == GradleDsl.GROOVY || !GradleVersionUtil.isGradleOlderThan(gradleVersion, "4.10")

    private fun BuildViewNodeAssertion.assertBuildFailureNodes(
      gradleVersion: GradleVersion,
      gradleDsl: GradleDsl,
      brokenFile: BrokenFile,
      assertIssues: BuildViewNodeAssertion.() -> Unit,
    ) {
      val scriptName = when (brokenFile) {
        BrokenFile.SETTINGS_SCRIPT -> gradleDsl.settingsScriptName
        BrokenFile.BUILD_SCRIPT -> gradleDsl.buildScriptName
      }
      val failureTitle = getFailureTitle(gradleVersion, gradleDsl, brokenFile)
      if (hasFilePosition(gradleVersion, gradleDsl)) {
        assertNode(BUILD_FAILURE_TITLE) {
          assertIsNodeSelected(true)
          assertNode(scriptName) {
            assertNode(failureTitle) {
              assertIsNodeSelected(false)
            }
            assertIssues()
          }
        }
      }
      else {
        assertNode(BUILD_FAILURE_TITLE) {
          assertIsNodeSelected(true)
          assertNode(failureTitle) {
            assertIsNodeSelected(false)
          }
        }
        assertIssues()
      }
    }
  }
}
