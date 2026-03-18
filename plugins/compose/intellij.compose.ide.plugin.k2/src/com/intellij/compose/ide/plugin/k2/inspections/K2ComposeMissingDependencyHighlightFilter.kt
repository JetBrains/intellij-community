// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.k2.inspections

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.compose.ide.plugin.k2.requiresComposePlugin
import com.intellij.compose.ide.plugin.k2.isComposeCompilerPluginApplied
import com.intellij.compose.ide.plugin.shared.inspections.ComposeMissingPluginInspection
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Suppresses the MISSING_DEPENDENCY_CLASS and ARGUMENT_TYPE_MISMATCH diagnostic for Compose-related errors
 * when the Compose compiler plugin is missing. In this case,
 * [ComposeMissingPluginInspection] provides a more actionable error with a quick-fix.
 */
internal class K2ComposeMissingDependencyHighlightFilter : HighlightInfoFilter {

  override fun accept(highlightInfo: HighlightInfo, psiFile: PsiFile?): Boolean {
    if (psiFile == null) return true

    val description = highlightInfo.description ?: return true
    if (!description.contains(MISSING_DEPENDENCY_CLASS) && !description.contains(ARGUMENT_TYPE_MISMATCH)) return true

    val element = psiFile.findElementAt(highlightInfo.startOffset) ?: return true
    val callExpression = element.parentOfType<KtCallExpression>() ?: return true
    if (!requiresComposePlugin(callExpression)) return true

    val module = ModuleUtilCore.findModuleForFile(psiFile) ?: return true
    return module.isComposeCompilerPluginApplied
  }

  private companion object {
    private const val MISSING_DEPENDENCY_CLASS = "MISSING_DEPENDENCY_CLASS"
    private const val ARGUMENT_TYPE_MISMATCH = "ARGUMENT_TYPE_MISMATCH"
  }
}