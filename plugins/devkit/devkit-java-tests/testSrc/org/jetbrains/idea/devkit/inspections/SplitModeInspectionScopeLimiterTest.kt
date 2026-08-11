// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeQodanaInspectionScopeLimiter

private const val QODANA_ANALYSIS_SCOPE_PROJECT_RELATIVE_PATH =
  "community/plugins/devkit/devkit-core/resources/remotedevInspectionData/SplitModeQodanaAnalysisScope.json"

internal class SplitModeInspectionScopeLimiterTest : BasePlatformTestCase() {

  fun testDisabledLimiterAllowsFileOutsideConfiguredModules() {
    val file = myFixture.configureByText(PlainTextFileType.INSTANCE, "text")

    withQodanaAnalysisScopeLimiter(false, "project", """{ "moduleNames": ["other.module"] }""") {
      assertTrue(SplitModeQodanaInspectionScopeLimiter.getInstance(project).shouldInspectFileInQodanaMode(file))
    }
  }

  fun testEnabledLimiterAllowsConfiguredModule() {
    val file = myFixture.configureByText(PlainTextFileType.INSTANCE, "text")
    val moduleName = getModuleName(file)

    withQodanaAnalysisScopeLimiter(true, "project", """{ "moduleNames": ["$moduleName"] }""") {
      assertTrue(SplitModeQodanaInspectionScopeLimiter.getInstance(project).shouldInspectFileInQodanaMode(file))
    }
  }

  fun testEnabledLimiterRejectsFileOutsideConfiguredModules() {
    val file = myFixture.configureByText(PlainTextFileType.INSTANCE, "text")

    withQodanaAnalysisScopeLimiter(true, "project", """{ "moduleNames": ["other.module"] }""") {
      assertFalse(SplitModeQodanaInspectionScopeLimiter.getInstance(project).shouldInspectFileInQodanaMode(file))
    }
  }

  fun testEnabledLimiterRejectsIgnoredModule() {
    val file = myFixture.configureByText(PlainTextFileType.INSTANCE, "text")
    val moduleName = getModuleName(file)

    withQodanaAnalysisScopeLimiter(true, "project", """{ "ignoredModules": ["$moduleName"] }""") {
      assertFalse(SplitModeQodanaInspectionScopeLimiter.getInstance(project).shouldInspectFileInQodanaMode(file))
    }
  }

  fun testEnabledLimiterAllowsModuleOutsideIgnoredModules() {
    val file = myFixture.configureByText(PlainTextFileType.INSTANCE, "text")

    withQodanaAnalysisScopeLimiter(true, "project", """{ "ignoredModules": ["other.module"] }""") {
      assertTrue(SplitModeQodanaInspectionScopeLimiter.getInstance(project).shouldInspectFileInQodanaMode(file))
    }
  }

  fun testEnabledLimiterAllowsAllModulesWhenScopeFileContainsBothAllowedAndIgnoredModules() {
    val file = myFixture.configureByText(PlainTextFileType.INSTANCE, "text")
    val moduleName = getModuleName(file)

    withQodanaAnalysisScopeLimiter(true, "project", """{ "moduleNames": ["other.module"], "ignoredModules": ["$moduleName"] }""") {
      assertTrue(SplitModeQodanaInspectionScopeLimiter.getInstance(project).shouldInspectFileInQodanaMode(file))
    }
  }

  fun testEnabledLimiterUsesProjectScopeFileInProjectOrBundledMode() {
    val file = myFixture.configureByText(PlainTextFileType.INSTANCE, "text")
    val moduleName = getModuleName(file)

    withQodanaAnalysisScopeLimiter(true, "project-or-bundled", """{ "moduleNames": ["$moduleName"] }""") {
      assertTrue(SplitModeQodanaInspectionScopeLimiter.getInstance(project).shouldInspectFileInQodanaMode(file))
    }
  }

  fun testEnabledLimiterAllowsAllModulesWhenScopeIsEmpty() {
    val file = myFixture.configureByText(PlainTextFileType.INSTANCE, "text")

    withQodanaAnalysisScopeLimiter(true, "project", null) {
      assertTrue(SplitModeQodanaInspectionScopeLimiter.getInstance(project).shouldInspectFileInQodanaMode(file))
    }
  }

  private fun withQodanaAnalysisScopeLimiter(
    enabled: Boolean,
    sourceMode: String,
    @Language("JSON") projectScopeJson: String?,
    action: () -> Unit,
  ) {
    val projectScopeFile = projectScopeJson?.let { createProjectResourceFile(project, QODANA_ANALYSIS_SCOPE_PROJECT_RELATIVE_PATH, it) }
    val enabledRegistryValue = Registry.get("devkit.split.mode.qodana.analysis.scope.limiter.enabled")
    val sourceModeRegistryValue = RegistryManager.getInstance().get("devkit.split.mode.qodana.analysis.scope.source")
    val previousSourceMode = sourceModeRegistryValue.asString()
    try {
      enabledRegistryValue.setValue(enabled)
      sourceModeRegistryValue.setValue(sourceMode)
      SplitModeQodanaInspectionScopeLimiter.getInstance(project).reloadScopeForTest()
      action()
    }
    finally {
      enabledRegistryValue.setValue(false)
      sourceModeRegistryValue.setValue(previousSourceMode)
      if (projectScopeFile != null) {
        deleteProjectResourceFile(project, projectScopeFile)
      }
      SplitModeQodanaInspectionScopeLimiter.getInstance(project).reloadScopeForTest()
    }
  }

  private fun getModuleName(file: PsiFile): String {
    return ModuleUtilCore.findModuleForPsiElement(file)!!.name
  }
}
