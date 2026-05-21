// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.k2.inspections

import com.intellij.compose.ide.plugin.k2.intentions.K2GradleAddComposePluginQuickFix
import com.intellij.compose.ide.plugin.k2.isComposeCompilerPluginApplied
import com.intellij.compose.ide.plugin.shared.inspections.ComposeModuleConfigurationExtension
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import org.jetbrains.plugins.gradle.util.GradleConstants

internal class K2GradleComposeModuleConfigurationExtension : ComposeModuleConfigurationExtension {

  override fun isApplicable(module: Module): Boolean =
    ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)

  override fun hasComposeEnabled(module: Module): Boolean = module.isComposeCompilerPluginApplied

  override fun createEnableComposeQuickFix(module: Module) = K2GradleAddComposePluginQuickFix()
}
