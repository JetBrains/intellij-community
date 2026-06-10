// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService
import org.junit.Assert
import java.nio.file.Files
import java.nio.file.Path

private const val API_RESTRICTIONS_PROJECT_RELATIVE_PATH =
  "community/plugins/devkit/devkit-core/resources/remotedevInspectionData/ApiRestrictions.json"

internal class SplitModeApiRestrictionsSourceModeTest : BasePlatformTestCase() {
  fun testProjectApiRestrictionsOverrideBundledListWhenProjectModeEnabled() {
    withApiRestrictionsSourceMode(
      sourceMode = "project",
      projectFileContent = """
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
    withApiRestrictionsSourceMode(sourceMode = "project-or-bundled") { service ->
      Assert.assertEquals(
        SplitModeApiRestrictionsService.ModuleKind.FRONTEND,
        service.getCodeApiKind("com.intellij.openapi.wm.ToolWindowFactory", null),
      )
    }
  }

  private fun withApiRestrictionsSourceMode(
    sourceMode: String,
    projectFileContent: String? = null,
    action: (SplitModeApiRestrictionsService) -> Unit,
  ) {
    val projectFile = projectFileContent?.let {
      Path.of(project.basePath!!).resolve(API_RESTRICTIONS_PROJECT_RELATIVE_PATH).also { file ->
        Files.createDirectories(file.parent)
        Files.writeString(file, it)
      }
    }
    val service = SplitModeApiRestrictionsService.getInstance(project)
    val registryValue = RegistryManager.getInstance().get("devkit.split.mode.analysis.api.restrictions.source")
    val previousValue = registryValue.asString()
    try {
      registryValue.setValue(sourceMode)
      service.reloadRestrictionsForTest()
      action(service)
    }
    finally {
      registryValue.setValue(previousValue)
      if (projectFile != null) {
        Files.deleteIfExists(projectFile)
      }
      service.reloadRestrictionsForTest()
    }
  }
}
