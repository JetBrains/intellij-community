// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService
import org.junit.Assert
import java.nio.file.Files

internal class SplitModePredefinedModuleKindsAdditionalFileTest : LightJavaCodeInsightFixtureTestCase() {
  fun testAdditionalPredefinedModuleKindsFileReplacesBundledListWhenPresent() {
    val additionalFile = Files.createTempFile("split-mode-predefined-module-kinds", ".json")
    Files.writeString(
      additionalFile,
      """
        [
          {"moduleName": "custom.split.mode.frontend", "moduleKind": "frontend"}
        ]
      """.trimIndent()
    )

    withAdditionalPredefinedModuleKindsFile(additionalFile.toString()) { service ->
      Assert.assertEquals(
        SplitModeApiRestrictionsService.ModuleKind.FRONTEND,
        service.getPredefinedDependencyKind("custom.split.mode.frontend"),
      )
      Assert.assertNull(service.getPredefinedDependencyKind("intellij.platform.frontend"))
    }
  }

  fun testMissingAdditionalPredefinedModuleKindsFileFallsBackToBundledList() {
    val missingPath = Files.createTempFile("split-mode-predefined-module-kinds-missing", ".json")
    Files.deleteIfExists(missingPath)

    withAdditionalPredefinedModuleKindsFile(missingPath.toString()) { service ->
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

  private fun withAdditionalPredefinedModuleKindsFile(
    filePath: String,
    action: (SplitModeApiRestrictionsService) -> Unit,
  ) {
    val service = SplitModeApiRestrictionsService.getInstance()
    val registryValue = RegistryManager.getInstance().get("devkit.remote.dev.split.mode.analysis.predefined.module.kinds.additional.file")
    val previousValue = registryValue.asString()
    try {
      registryValue.setValue(filePath)
      service.reloadRestrictionsForTest()
      action(service)
    }
    finally {
      registryValue.setValue(previousValue)
      service.reloadRestrictionsForTest()
    }
  }
}
