// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService
import org.junit.Assert

private const val PREDEFINED_MODULE_KINDS_PROJECT_RELATIVE_PATH =
  "community/plugins/devkit/devkit-core/resources/remotedevInspectionData/PredefinedModuleKinds.json"

internal class SplitModePredefinedModuleKindsSourceModeTest : BasePlatformTestCase() {
  fun testProjectPredefinedModuleKindsOverrideBundledListWhenProjectModeEnabled() {
    withPredefinedModuleKindsSourceMode(
      "project",
      """
        [
          {"moduleName": "custom.split.mode.frontend", "moduleKind": "frontend"}
        ]
      """.trimIndent(),
    ) { service ->
      Assert.assertEquals(
        SplitModeApiRestrictionsService.ModuleKind.FRONTEND,
        service.getPredefinedDependencyKind("custom.split.mode.frontend"),
      )
      Assert.assertNull(service.getPredefinedDependencyKind("intellij.platform.frontend"))
    }
  }

  fun testProjectOrBundledModeFallsBackToBundledListWhenProjectFileIsMissing() {
    withPredefinedModuleKindsSourceMode("project-or-bundled", null) { service ->
      Assert.assertEquals(
        SplitModeApiRestrictionsService.ModuleKind.FRONTEND,
        service.getPredefinedDependencyKind("intellij.platform.frontend"),
      )
      Assert.assertEquals(
        SplitModeApiRestrictionsService.ModuleKind.SHARED,
        service.getPredefinedDependencyKind("intellij.rd.client.base"),
      )
      Assert.assertEquals(
        SplitModeApiRestrictionsService.ModuleKind.SHARED,
        service.getPredefinedDependencyKind("intellij.rd.client"),
      )
    }
  }

  fun testProjectPredefinedModuleKindsReloadAfterProjectFileChange() {
    withPredefinedModuleKindsSourceMode(
      "project",
      """
        [
          {"moduleName": "custom.split.mode.frontend", "moduleKind": "frontend"}
        ]
      """.trimIndent(),
    ) { service ->
      Assert.assertTrue(service.ensureLoadedBlocking())
      Assert.assertEquals(
        SplitModeApiRestrictionsService.ModuleKind.FRONTEND,
        service.getPredefinedDependencyKind("custom.split.mode.frontend"),
      )
      Assert.assertNull(service.getPredefinedDependencyKind("custom.split.mode.backend"))

      createProjectResourceFile(
        project,
        PREDEFINED_MODULE_KINDS_PROJECT_RELATIVE_PATH,
        """
          [
            {"moduleName": "custom.split.mode.backend", "moduleKind": "backend"}
          ]
        """.trimIndent(),
      )

      Assert.assertTrue(service.ensureLoadedBlocking())
      Assert.assertNull(service.getPredefinedDependencyKind("custom.split.mode.frontend"))
      Assert.assertEquals(
        SplitModeApiRestrictionsService.ModuleKind.BACKEND,
        service.getPredefinedDependencyKind("custom.split.mode.backend"),
      )
    }
  }

  private fun withPredefinedModuleKindsSourceMode(
    sourceMode: String,
    projectFileContent: String?,
    action: (SplitModeApiRestrictionsService) -> Unit,
  ) {
    val projectFile = projectFileContent?.let { createProjectResourceFile(project, PREDEFINED_MODULE_KINDS_PROJECT_RELATIVE_PATH, it) }
    val registryValue = RegistryManager.getInstance().get("devkit.split.mode.analysis.predefined.module.kinds.source")
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
