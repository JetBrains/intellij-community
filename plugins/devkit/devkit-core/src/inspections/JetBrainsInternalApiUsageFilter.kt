// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.StaticAnalysisAnnotationManager
import com.intellij.codeInspection.UnstableApiUsageFilter
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import org.jetbrains.idea.devkit.util.PsiUtil

/**
 * Hides the internal API branches of the standard `UnstableApiUsage` inspection in a plugin module.
 *
 * [JetBrainsInternalApiUsageInspection] reports the internal API there, and it states what the use of such an API
 * means for the plugin. The other branches of the standard inspection stay active.
 */
internal class JetBrainsInternalApiUsageFilter : UnstableApiUsageFilter {

  override fun isUsageIgnored(usage: PsiElement, annotationFqn: String): Boolean =
    isInternalApiAnnotation(annotationFqn) && isInPluginModule(usage)
}

internal fun isInternalApiAnnotation(annotationFqn: String): Boolean =
  annotationFqn in StaticAnalysisAnnotationManager.getInstance().knownInternalApiAnnotations

internal fun isInPluginModule(element: PsiElement): Boolean {
  val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return false
  return PsiUtil.isPluginModule(module)
}
