// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.jetbrains.idea.devkit.inspections.remotedev.MONOREPO_FEATURES_WITHOUT_SPLIT_MODE_SUPPORT_RELATIVE_PATH
import org.jetbrains.idea.devkit.inspections.remotedev.SPLIT_MODE_EXCLUDED_MODULES_SCOPE_RELATIVE_PATHS
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionResourceReadMode
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionResourceReader
import org.jetbrains.idea.devkit.inspections.remotedev.createSplitModeExcludedModulesScopeXml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.io.path.readText
import kotlin.io.path.writeText

private const val UPDATE_SPLIT_MODE_EXCLUDED_MODULES_SCOPE_PROPERTY = "devkit.split.mode.excluded.modules.scope.update"

@TestApplication
internal class SplitModeExcludedModulesScopeConsistencyTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val project get() = projectFixture.get()

  @Test
  fun `SplitModeExcludedModules scopes are generated from monorepo features without split mode support`() {
    val projectRoot = PathManager.getHomeDir()
    val expectedXml = createSplitModeExcludedModulesScopeXml(
      SplitModeInspectionResourceReader.getInstance(project),
      SplitModeInspectionResourceReadMode.BUNDLED_ONLY,
    )

    for (relativePath in SPLIT_MODE_EXCLUDED_MODULES_SCOPE_RELATIVE_PATHS) {
      val scopeFile = projectRoot.resolve(relativePath)
      if (java.lang.Boolean.getBoolean(UPDATE_SPLIT_MODE_EXCLUDED_MODULES_SCOPE_PROPERTY)) {
        scopeFile.writeText(expectedXml)
      }

      val actualXml = scopeFile.readText()
      assertEquals(
        expectedXml,
        actualXml,
        "$relativePath must be generated from $MONOREPO_FEATURES_WITHOUT_SPLIT_MODE_SUPPORT_RELATIVE_PATH",
      )
    }
  }
}
