// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.preview

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.idea.devkit.util.PsiUtil

/**
 * @Preview methods are useful for UI previews in IDE even when unused in production, suppress unused symbol inspection for them.
 */
internal class ComposePreviewImplicitUsageProvider : ImplicitUsageProvider {
  override fun isImplicitRead(element: PsiElement): Boolean = false
  override fun isImplicitWrite(element: PsiElement): Boolean = false

  override fun isImplicitUsage(element: PsiElement): Boolean {
    if (element !is PsiMethod) return false
    val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return false

    return PsiUtil.isPluginModule(module)
           && AnnotationUtil.isAnnotated(element, PREVIEW_ANNOTATIONS, 0)
  }
}
