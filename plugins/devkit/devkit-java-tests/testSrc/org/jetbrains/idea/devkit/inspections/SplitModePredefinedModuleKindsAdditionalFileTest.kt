// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService
import org.junit.Assert
import java.nio.file.Files
import java.nio.file.Path

private const val PREDEFINED_MODULE_KINDS_PROJECT_RELATIVE_PATH =
  "community/plugins/devkit/devkit-core/resources/remotedevInspectionData/PredefinedModuleKinds.json"

internal class SplitModePredefinedModuleKindsSourceModeTest : BasePlatformTestCase() {
  fun testProjectPredefinedModuleKindsOverrideBundledListWhenProjectModeEnabled() {
    withPredefinedModuleKindsSourceMode(
      sourceMode = "project",
      projectFileContent = """
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
    withPredefinedModuleKindsSourceMode(sourceMode = "project-or-bundled") { service ->
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

  private fun withPredefinedModuleKindsSourceMode(
    sourceMode: String,
    projectFileContent: String? = null,
    action: (SplitModeApiRestrictionsService) -> Unit,
  ) {
    val projectFile = projectFileContent?.let {
      Path.of(project.basePath!!).resolve(PREDEFINED_MODULE_KINDS_PROJECT_RELATIVE_PATH).also { file ->
        Files.createDirectories(file.parent)
        Files.writeString(file, it)
      }
    }
    val service = SplitModeApiRestrictionsService.getInstance(project)
    val registryValue = RegistryManager.getInstance().get("devkit.split.mode.analysis.predefined.module.kinds.source")
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
