// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeQodanaInspectionScopeLimiter
import java.nio.file.Files

internal class SplitModeInspectionScopeLimiterTest : BasePlatformTestCase() {

  fun testDisabledLimiterAllowsFileOutsideConfiguredModules() {
    val file = myFixture.configureByText(PlainTextFileType.INSTANCE, "text")

    withQodanaAnalysisScopeLimiter(false, """{ "moduleNames": ["other.module"] }""") {
      assertTrue(SplitModeQodanaInspectionScopeLimiter.getInstance().shouldInspectFileInQodanaMode(file))
    }
  }

  fun testEnabledLimiterAllowsConfiguredModule() {
    val file = myFixture.configureByText(PlainTextFileType.INSTANCE, "text")
    val moduleName = getModuleName(file)

    withQodanaAnalysisScopeLimiter(true, """{ "moduleNames": ["$moduleName"] }""") {
      assertTrue(SplitModeQodanaInspectionScopeLimiter.getInstance().shouldInspectFileInQodanaMode(file))
    }
  }

  fun testEnabledLimiterRejectsFileOutsideConfiguredModules() {
    val file = myFixture.configureByText(PlainTextFileType.INSTANCE, "text")

    withQodanaAnalysisScopeLimiter(true, """{ "moduleNames": ["other.module"] }""") {
      assertFalse(SplitModeQodanaInspectionScopeLimiter.getInstance().shouldInspectFileInQodanaMode(file))
    }
  }

  fun testEnabledLimiterAllowsAllModulesWhenScopeIsEmpty() {
    val file = myFixture.configureByText(PlainTextFileType.INSTANCE, "text")

    withQodanaAnalysisScopeLimiter(true, null) {
      assertTrue(SplitModeQodanaInspectionScopeLimiter.getInstance().shouldInspectFileInQodanaMode(file))
    }
  }

  private fun withQodanaAnalysisScopeLimiter(
    enabled: Boolean,
    @Language("JSON") scopeJson: String?,
    action: () -> Unit,
  ) {
    val additionalFile = scopeJson?.let {
      Files.createTempFile("split-mode-qodana-analysis-scope", ".json").also { file ->
        Files.writeString(file, it)
      }
    }
    val enabledRegistryValue = Registry.get("devkit.remote.dev.split.mode.qodana.analysis.scope.limiter.enabled")
    val additionalFileRegistryValue = Registry.get("devkit.remote.dev.split.mode.qodana.analysis.scope.additional.file")
    try {
      enabledRegistryValue.setValue(enabled)
      additionalFileRegistryValue.setValue(additionalFile?.toString() ?: "")
      SplitModeQodanaInspectionScopeLimiter.getInstance().reloadScopeForTest()
      action()
    }
    finally {
      enabledRegistryValue.setValue(false)
      additionalFileRegistryValue.setValue("")
      SplitModeQodanaInspectionScopeLimiter.getInstance().reloadScopeForTest()
      if (additionalFile != null) {
        Files.deleteIfExists(additionalFile)
      }
    }
  }

  private fun getModuleName(file: PsiFile): String {
    return ModuleUtilCore.findModuleForPsiElement(file)!!.name
  }
}
