// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.inspections

import org.jetbrains.plugins.gradle.execution.inspections.GradleLatestMinorVersionBuildIssueTaskExecutionTest.Companion.assertNewMinorGradleVersionNodeConsoleText
import org.jetbrains.plugins.gradle.execution.inspections.GradleLatestMinorVersionBuildIssueTaskExecutionTest.Companion.assertNodeWithNewMinorGradleVersionInfo
import org.jetbrains.plugins.gradle.execution.inspections.GradleLatestMinorVersionBuildIssueTaskExecutionTest.Companion.enableGradleLatestMinorVersionInspection
import org.jetbrains.plugins.gradle.execution.inspections.GradleLatestMinorVersionBuildIssueTaskExecutionTest.Companion.shouldShowMinorGradleVersionWarning
import org.jetbrains.plugins.gradle.importing.BuildViewMessagesImportingTestCase
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test

class GradleLatestMinorVersionBuildIssueImportTest : BuildViewMessagesImportingTestCase() {

  @Test
  fun testImport() {
    enableGradleLatestMinorVersionInspection(myProject)
    createSettingsFile("")
    importProject()

    assertSyncViewTree {
      assertNode("finished") {
        assertNodeWithDeprecatedGradleWarning()
        assertNodeWithNewMinorGradleVersionInfo(currentGradleVersion)
      }
    }
    if (shouldShowMinorGradleVersionWarning(currentGradleVersion)) {
      assertSyncViewNode("New Minor Gradle Version Available") { consoleText ->
        assertNewMinorGradleVersionNodeConsoleText(currentGradleVersion, consoleText)
      }
    }
  }

  @Test
  @TargetVersions("8.0")
  fun testImportDisabledInspection() {
    createSettingsFile("")
    importProject()

    assertSyncViewTree {
      assertNode("finished") {
        assertNodeWithDeprecatedGradleWarning()
      }
    }
  }
}