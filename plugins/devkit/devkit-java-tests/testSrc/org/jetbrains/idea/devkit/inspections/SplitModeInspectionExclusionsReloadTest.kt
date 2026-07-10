// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.idea.devkit.inspections.remotedev.EXCLUSIONS_RELATIVE_PATH
import org.jetbrains.idea.devkit.inspections.remotedev.PROJECT_BASELINE_VERSION_RELATIVE_PATH
import org.jetbrains.idea.devkit.inspections.remotedev.SPLIT_MODE_API_USAGE_SHORT_NAME
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionExclusionProblem
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionExclusionsService
import org.junit.Assert

internal class SplitModeInspectionExclusionsReloadTest : BasePlatformTestCase() {
  fun testQuickFixesContainOnlyBaselineByDefault() {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, true)
    val file = myFixture.addFileToProject("src/example.kt", "class Example")

    val fixes = SplitModeInspectionExclusionsService.getInstance(project).createCommonSuppressionQuickFixes(
      file,
      SPLIT_MODE_API_USAGE_SHORT_NAME,
    )

    Assert.assertEquals(
      listOf("Bypass incremental Split Mode safe-push tests"),
      fixes.map { it.familyName },
    )
  }

  fun testSuppressionQuickFixIsHiddenBehindRegistry() {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, true)
    RegistryManager.getInstance().get("devkit.split.mode.add.to.exclusions.quick.fix.enabled")
      .setValue(true, testRootDisposable)
    val file = myFixture.addFileToProject("src/example.kt", "class Example")

    val fixes = SplitModeInspectionExclusionsService.getInstance(project).createCommonSuppressionQuickFixes(
      file,
      SPLIT_MODE_API_USAGE_SHORT_NAME,
    )

    Assert.assertEquals(
      listOf(
        "Bypass incremental Split Mode safe-push tests",
        "Add this violation to the exclusions list",
      ),
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

  fun testExclusionsReloadAfterProjectFileChange() {
    val exclusionsFile = createProjectResourceFile(
      project,
      EXCLUSIONS_RELATIVE_PATH,
      """
        {
          "exclusions": [
            {"inspection": "$SPLIT_MODE_API_USAGE_SHORT_NAME", "file": "src/example.kt", "line": 7}
          ]
        }
      """.trimIndent(),
    )
    val service = SplitModeInspectionExclusionsService.getInstance(project)
    val problem = SplitModeInspectionExclusionProblem(
      inspection = SPLIT_MODE_API_USAGE_SHORT_NAME,
      file = "src/example.kt",
      line = 7,
    )

    try {
      Assert.assertTrue(service.isExcluded(problem))

      createProjectResourceFile(
        project,
        EXCLUSIONS_RELATIVE_PATH,
        """
          {
            "exclusions": []
          }
        """.trimIndent(),
      )

      Assert.assertFalse(service.isExcluded(problem))
    }
    finally {
      deleteProjectResourceFile(project, exclusionsFile)
    }
  }
}
