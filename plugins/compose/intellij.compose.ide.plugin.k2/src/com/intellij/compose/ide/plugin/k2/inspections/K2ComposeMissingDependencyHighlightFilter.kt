// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.k2.inspections

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.compose.ide.plugin.k2.checkRequiresComposePlugin
import com.intellij.compose.ide.plugin.shared.inspections.ComposeMissingPluginInspection
import com.intellij.compose.ide.plugin.shared.inspections.ComposeModuleConfigurationExtension
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

/**
 * Suppresses the MISSING_DEPENDENCY_CLASS and ARGUMENT_TYPE_MISMATCH diagnostic for Compose-related errors
 * when the Compose compiler plugin is missing. In this case,
 * [ComposeMissingPluginInspection] provides a more actionable error with a quick-fix.
 */
internal class K2ComposeMissingDependencyHighlightFilter : HighlightInfoFilter {

  override fun accept(highlightInfo: HighlightInfo, psiFile: PsiFile?): Boolean {
    if (psiFile == null) return true

    val description = highlightInfo.description ?: return true

    if (TARGET_DIAGNOSTIC_NAMES.none(description::contains)) return true
    val element = psiFile.findElementAt(highlightInfo.startOffset) ?: return true

    val callExpression = element.parentOfType<KtCallExpression>()
    val nameExpression = element.parentOfType<KtSimpleNameExpression>()

    val isComposeCall = callExpression != null && checkRequiresComposePlugin(callExpression)
    val isComposeProperty =
      nameExpression != null && nameExpression.parent !is KtCallExpression && checkRequiresComposePlugin(nameExpression)

    if (!isComposeCall && !isComposeProperty) return true

    val module = ModuleUtilCore.findModuleForFile(psiFile) ?: return true
    val composeConfiguration = ComposeModuleConfigurationExtension.findFor(module) ?: return true
    return composeConfiguration.hasComposeEnabled(module)
  }
}

private val TARGET_DIAGNOSTIC_NAMES = listOf(
  FirErrors.MISSING_DEPENDENCY_CLASS,
  FirErrors.ARGUMENT_TYPE_MISMATCH,
).map { it.name }