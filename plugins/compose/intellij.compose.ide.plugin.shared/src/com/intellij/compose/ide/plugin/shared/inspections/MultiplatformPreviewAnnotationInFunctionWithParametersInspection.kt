/*
 * Copyright (C) 2019 The Android Open Source Project
 * Modified 2025 by JetBrains s.r.o.
 * Copyright (C) 2025 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compose.ide.plugin.shared.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.compose.ide.plugin.shared.isPreviewParameterAnnotation
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter

/**
 * Inspection that checks a preview function has no non-default or @PreviewParameter-provided parameters.
 */
internal class MultiplatformPreviewAnnotationInFunctionWithParametersInspection :
  BasePreviewAnnotationInspection(
    ComposeIdeBundle.message("compose.preview.inspection.group.name"),
    ComposePreviewAnnotationChecker
  ) {
  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    function: KtNamedFunction,
    previewAnnotation: KtAnnotationEntry,
  ) {
    if (function.valueParameters.any { !isAcceptableForPreview(it) }) {
      holder.registerProblem(
        previewAnnotation.psiOrParent as PsiElement,
        ComposeIdeBundle.message("compose.preview.inspection.no.parameters.description"),
        ProblemHighlightType.ERROR,
      )
    }
  }

  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    annotationClass: KtClass,
    previewAnnotation: KtAnnotationEntry,
  ) {
    // This inspection only applies for functions, not for Annotation classes
    return
  }

  override fun getStaticDescription(): String = ComposeIdeBundle.message("compose.preview.inspection.no.parameters.description")

  /**
   * Returns whether the [KtParameter] can be used in the preview.
   * This will return true if the parameter has a default value or a value provider.
   */
  private fun isAcceptableForPreview(parameter: KtParameter): Boolean =
    parameter.hasDefaultValue() || parameter.annotationEntries.any { it.isPreviewParameterAnnotation() }
}
