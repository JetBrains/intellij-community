// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.build.FileNavigatable
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.openapi.Disposable
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion
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
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.annotations.processors.CsvCrossProductArgumentsProcessor
import org.jetbrains.plugins.gradle.testFramework.fixtures.buildViewFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.projectFixture
import org.jetbrains.plugins.gradle.testFramework.projectInfo.GradleProjectInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.buildFile
import org.jetbrains.plugins.gradle.testFramework.projectInfo.gradleProjectInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.gradleWrapper
import org.jetbrains.plugins.gradle.testFramework.projectInfo.initProject
import org.jetbrains.plugins.gradle.testFramework.projectInfo.simpleSettingsFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.support.ParameterDeclarations
import java.util.function.Consumer

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

  private val buildView by buildViewFixture(projectFixture)

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
    registerIssueChecker(TestBuildOutputFailureMessageSuppressor(), asDisposable())
    registerIssueChecker(TestBuildIssueChecker(TEST_BUILD_ISSUE_TITLE, TEST_BUILD_ISSUE_DESCRIPTION), asDisposable())

    val projectInfo = simpleJavaProjectInfoWithBrokenScript(gradleVersion, gradleDsl, brokenFile)

    val projectRoot = projectInfo.initProject(testRoot)

    gradle.linkProject(project, projectRoot)

    buildView.assertSyncViewTree {
      assertNode("(failed|finished)".toRegex()) {
        assertNodeWithDeprecatedGradleWarning(gradleVersion)
        assertFilePositionNode(gradleVersion, gradleDsl, brokenFile) {
          assertNode(TEST_BUILD_ISSUE_TITLE)
        }
      }
    }

    buildView.assertSyncViewSelectedNode(TEST_BUILD_ISSUE_TITLE) { text ->
      assertEquals(TEST_BUILD_ISSUE_DESCRIPTION + "\n\n", text)
    }
  }

  @ParameterizedTest
  @EnumSource(GradleDsl::class)
  fun `test build issue checker reports all issues for script exception`(gradleDsl: GradleDsl): Unit = runBlocking {
    registerIssueChecker(TestBuildOutputFailureMessageSuppressor(), asDisposable())
    registerIssueChecker(TestBuildIssueChecker(TEST_BUILD_ISSUE_TITLE, TEST_BUILD_ISSUE_DESCRIPTION), asDisposable())
    registerIssueChecker(TestBuildIssueChecker(TEST_BUILD_ISSUE_TITLE_2, TEST_BUILD_ISSUE_DESCRIPTION_2), asDisposable())

    val projectInfo = simpleJavaProjectInfoWithBrokenScript(gradleVersion, gradleDsl, BrokenFile.BUILD_SCRIPT)

    val projectRoot = projectInfo.initProject(testRoot)

    gradle.linkProject(project, projectRoot)

    buildView.assertSyncViewTree {
      assertNode("(failed|finished)".toRegex()) {
        assertNodeWithDeprecatedGradleWarning(gradleVersion)
        assertFilePositionNode(gradleVersion, gradleDsl, BrokenFile.BUILD_SCRIPT) {
          assertNode(TEST_BUILD_ISSUE_TITLE)
          assertNode(TEST_BUILD_ISSUE_TITLE_2)
        }
      }
    }

    buildView.assertSyncViewSelectedNode(TEST_BUILD_ISSUE_TITLE) { text ->
      assertEquals(TEST_BUILD_ISSUE_DESCRIPTION + "\n\n", text)
    }
    buildView.assertSyncViewNode(TEST_BUILD_ISSUE_TITLE_2) { text ->
      assertEquals(TEST_BUILD_ISSUE_DESCRIPTION_2 + "\n\n", text)
    }
  }

  private class TestBuildOutputFailureMessageSuppressor : GradleIssueChecker {

    override fun check(issueData: GradleIssueData): BuildIssue? = null

    override fun consumeBuildOutputFailureMessage(
      message: String,
      failureCause: String,
      stacktrace: String?,
      location: FilePosition?,
      parentEventId: Any,
      messageConsumer: Consumer<in BuildEvent>,
    ): Boolean {
      return TEST_BUILD_SCRIPT_EXCEPTION_MESSAGE in message
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

    private fun registerIssueChecker(checker: GradleIssueChecker, disposable: Disposable) {
      GradleIssueChecker.EP_NAME.point.registerExtension(checker, disposable)
    }

    private fun simpleJavaProjectInfoWithBrokenScript(
      gradleVersion: GradleVersion,
      gradleDsl: GradleDsl,
      brokenFile: BrokenFile,
    ): GradleProjectInfo = gradleProjectInfo(gradleVersion, gradleDsl = gradleDsl) {
      gradleWrapper()
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

    private fun GradleScriptElementBuilder.throwTestBuildScriptException(gradleDsl: GradleDsl) =
      throwException(gradleDsl, TEST_BUILD_SCRIPT_EXCEPTION_CLASS, TEST_BUILD_SCRIPT_EXCEPTION_MESSAGE)

    private fun GradleScriptElementBuilder.throwException(gradleDsl: GradleDsl, className: String, message: String) =
      when (gradleDsl) {
        GradleDsl.GROOVY -> code("throw new $className('$message')")
        GradleDsl.KOTLIN -> code("throw $className(\"$message\")")
      }

    private fun SimpleTreeAssertion<*>.assertFilePositionNode(
      gradleVersion: GradleVersion,
      gradleDsl: GradleDsl,
      brokenFile: BrokenFile,
      assert: SimpleTreeAssertion<*>.() -> Unit,
    ) {
      if (gradleDsl == GradleDsl.KOTLIN && GradleVersionUtil.isGradleOlderThan(gradleVersion, "6.8")) {
        // For old Gradle versions there are no information about exception location with Kotlin scripts:
        //   at Settings_gradle.<init>(Unknown Source)
        //   at Build_gradle.<init>(Unknown Source)
        assert()
      }
      else {
        when (brokenFile) {
          BrokenFile.SETTINGS_SCRIPT -> {
            assertNode(gradleDsl.settingsScriptName, assert = assert)
          }
          BrokenFile.BUILD_SCRIPT -> {
            assertNode(gradleDsl.buildScriptName, assert = assert)
          }
        }
      }
    }
  }
}
