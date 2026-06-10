// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.idea.devkit.inspections.remotedev.EXCLUSIONS_RELATIVE_PATH
import org.jetbrains.idea.devkit.inspections.remotedev.SPLIT_MODE_API_USAGE_SHORT_NAME
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionExclusionProblem
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionExclusionsService
import org.junit.Assert

internal class SplitModeInspectionExclusionsReloadTest : BasePlatformTestCase() {
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
