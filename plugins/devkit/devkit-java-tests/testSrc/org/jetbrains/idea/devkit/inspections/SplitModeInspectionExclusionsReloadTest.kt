// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.idea.devkit.inspections.remotedev.PROJECT_BASELINE_VERSION_RELATIVE_PATH
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionExclusionsService
import org.junit.Assert

internal class SplitModeInspectionExclusionsReloadTest : BasePlatformTestCase() {
  fun testQuickFixesContainOnlyBaselineByDefault() {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, true)
    val fixes = SplitModeInspectionExclusionsService.getInstance(project).createCommonSuppressionQuickFixes()

    Assert.assertEquals(
      listOf("Bypass incremental Split Mode safe-push tests"),
      fixes.map { it.familyName },
    )
  }

  fun testBaselineVersionIncreasesExistingProjectFile() {
    val baselineVersionFile = createProjectResourceFile(
      project,
      PROJECT_BASELINE_VERSION_RELATIVE_PATH,
      """
        // Keep this comment.
        {
          "currentProjectBaselineVersion": 41
        }
      """.trimIndent(),
    )

    try {
      val updatedFile = runWriteAction {
        SplitModeInspectionExclusionsService.getInstance(project).increaseProjectBaselineVersion()
      }

      Assert.assertEquals(baselineVersionFile, updatedFile)
      Assert.assertEquals(
        """
          // The file carries the current project baseline version for Split Mode Compatibility safe-push checks.
          // Increase currentProjectBaselineVersion only when incremental Split Mode Compatibility tests incorrectly
          // report pre-existing violations after large refactorings.
          // Changing this value is a legal bypass: the affected safe push may skip the incremental Split Mode
          // Compatibility tests for that run, allowing the refactoring to proceed while the baseline catches up.
          // Do not use it for real new violations. Fix those instead; see Split Mode documentation IJPL-A-632.
          // Contact the RemDev team in #ij-remote-dev if unsure.
          {
              "currentProjectBaselineVersion": 42
          }

        """.trimIndent(),
        VfsUtilCore.loadText(baselineVersionFile),
      )
    }
    finally {
      deleteProjectResourceFile(project, baselineVersionFile)
    }
  }
}
