// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.inspections

import com.intellij.platform.testFramework.assertion.BuildViewAssertions.assertBuildViewNode
import com.intellij.platform.testFramework.assertion.BuildViewAssertions.assertBuildViewTree
import com.intellij.platform.testFramework.assertion.consoleText
import org.jetbrains.plugins.gradle.execution.inspections.GradleLatestMinorVersionBuildIssueTaskExecutionTest.Companion.assertNewMinorGradleVersionNodeConsoleText
import org.jetbrains.plugins.gradle.execution.inspections.GradleLatestMinorVersionBuildIssueTaskExecutionTest.Companion.assertNodeWithNewMinorGradleVersionInfo
import org.jetbrains.plugins.gradle.execution.inspections.GradleLatestMinorVersionBuildIssueTaskExecutionTest.Companion.enableGradleLatestMinorVersionInspection
import org.jetbrains.plugins.gradle.execution.inspections.GradleLatestMinorVersionBuildIssueTaskExecutionTest.Companion.shouldShowMinorGradleVersionWarning
import org.jetbrains.plugins.gradle.importing.BuildViewMessagesImportingTestCase
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test

class GradleLatestMinorVersionBuildIssueImportTest : BuildViewMessagesImportingTestCase() {

  @Test
  @TargetVersions("8.0.x")
  fun testImport() {
    enableGradleLatestMinorVersionInspection(myProject)
    createSettingsFile("")
    importProject()

    assertBuildViewTree(syncView) {
      assertNode("finished") {
        assertNodeWithDeprecatedGradleWarning()
        assertNodeWithNewMinorGradleVersionInfo(currentGradleVersion)
      }
    }
    if (shouldShowMinorGradleVersionWarning(currentGradleVersion)) {
      assertBuildViewNode(syncView, "New Minor Gradle Version Available") {
        assertNewMinorGradleVersionNodeConsoleText(currentGradleVersion, it.consoleText)
      }
    }
  }

  @Test
  @TargetVersions("8.0.x")
  fun testImportDisabledInspection() {
    createSettingsFile("")
    importProject()

    assertBuildViewTree(syncView) {
      assertNode("finished") {
        assertNodeWithDeprecatedGradleWarning()
      }
    }
  }
}