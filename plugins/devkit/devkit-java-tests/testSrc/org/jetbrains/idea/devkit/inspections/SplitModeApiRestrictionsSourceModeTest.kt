// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService
import org.junit.Assert

private const val API_RESTRICTIONS_PROJECT_RELATIVE_PATH =
  "community/plugins/devkit/devkit-core/resources/remotedevInspectionData/ApiRestrictions.json"

internal class SplitModeApiRestrictionsSourceModeTest : BasePlatformTestCase() {
  fun testProjectApiRestrictionsOverrideBundledListWhenProjectModeEnabled() {
    withApiRestrictionsSourceMode(
      "project",
      """
        [
          {"apiName": "custom.split.mode.api", "targetModules": ["frontend"]}
        ]
      """.trimIndent(),
    ) { service ->
      Assert.assertEquals(
        SplitModeApiRestrictionsService.ModuleKind.FRONTEND,
        service.getCodeApiKind("custom.split.mode.api", null),
      )
      Assert.assertNull(service.getCodeApiKind("com.intellij.openapi.wm.ToolWindowFactory", null))
    }
  }

  fun testProjectOrBundledModeFallsBackToBundledApiRestrictionsWhenProjectFileIsMissing() {
    withApiRestrictionsSourceMode("project-or-bundled", null) { service ->
      Assert.assertEquals(
        SplitModeApiRestrictionsService.ModuleKind.FRONTEND,
        service.getCodeApiKind("com.intellij.openapi.wm.ToolWindowFactory", null),
      )
    }
  }

  fun testProjectApiRestrictionsReloadAfterProjectFileChange() {
    withApiRestrictionsSourceMode(
      "project",
      """
        [
          {"apiName": "custom.split.mode.api.one", "targetModules": ["frontend"]}
        ]
      """.trimIndent(),
    ) { service ->
      Assert.assertTrue(service.ensureLoadedBlocking())
      Assert.assertEquals(
        SplitModeApiRestrictionsService.ModuleKind.FRONTEND,
        service.getCodeApiKind("custom.split.mode.api.one", null),
      )
      Assert.assertNull(service.getCodeApiKind("custom.split.mode.api.two", null))

      createProjectResourceFile(
        project,
        API_RESTRICTIONS_PROJECT_RELATIVE_PATH,
        """
          [
            {"apiName": "custom.split.mode.api.two", "targetModules": ["backend"]}
          ]
        """.trimIndent(),
      )

      Assert.assertTrue(service.ensureLoadedBlocking())
      Assert.assertNull(service.getCodeApiKind("custom.split.mode.api.one", null))
      Assert.assertEquals(
        SplitModeApiRestrictionsService.ModuleKind.BACKEND,
        service.getCodeApiKind("custom.split.mode.api.two", null),
      )
    }
  }

  private fun withApiRestrictionsSourceMode(
    sourceMode: String,
    projectFileContent: String?,
    action: (SplitModeApiRestrictionsService) -> Unit,
  ) {
    val projectFile = projectFileContent?.let { createProjectResourceFile(project, API_RESTRICTIONS_PROJECT_RELATIVE_PATH, it) }
    val registryValue = RegistryManager.getInstance().get("devkit.split.mode.analysis.api.restrictions.source")
    val previousValue = registryValue.asString()
    try {
      registryValue.setValue(sourceMode)
      val service = SplitModeApiRestrictionsService.getInstance(project)
      service.reloadRestrictionsForTest()
      action(service)
    }
    finally {
      registryValue.setValue(previousValue)
      if (projectFile != null) {
        deleteProjectResourceFile(project, projectFile)
      }
    }
  }
}
