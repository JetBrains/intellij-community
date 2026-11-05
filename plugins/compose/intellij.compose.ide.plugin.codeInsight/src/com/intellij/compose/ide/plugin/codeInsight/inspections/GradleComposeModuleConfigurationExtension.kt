// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.codeInsight.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.compose.ide.plugin.codeInsight.buildScriptKtFile
import com.intellij.compose.ide.plugin.codeInsight.intentions.GradleAddComposePluginQuickFix
import com.intellij.compose.ide.plugin.codeInsight.isComposeCompilerGradlePluginOnBuildScriptClasspath
import com.intellij.compose.ide.plugin.codeInsight.isComposeCompilerPluginApplied
import com.intellij.compose.ide.plugin.shared.inspections.ComposeModuleConfigurationExtension
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.base.plugin.KotlinCompilerVersionProvider
import org.jetbrains.kotlin.idea.configuration.KotlinBuildSystemDependencyManager
import org.jetbrains.kotlin.idea.configuration.isProjectSyncPendingOrInProgress
import org.jetbrains.plugins.gradle.util.GradleConstants

internal class GradleComposeModuleConfigurationExtension : ComposeModuleConfigurationExtension {

  override fun isApplicable(module: Module): Boolean =
    ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)

  override fun hasComposeEnabled(module: Module): Boolean = module.isComposeCompilerPluginApplied

  override fun createEnableComposeQuickFix(module: Module): LocalQuickFix? {
    val dependencyManager = KotlinBuildSystemDependencyManager.findApplicableConfigurator(module) ?: return null
    if (dependencyManager.isProjectSyncPendingOrInProgress()) return null
    if (module.buildScriptKtFile == null) return null

    val version =
      if (module.isComposeCompilerGradlePluginOnBuildScriptClasspath) null
      else KotlinCompilerVersionProvider.getVersion(module)?.artifactVersion ?: return null

    return GradleAddComposePluginQuickFix(dependencyManager, version)
  }
}
