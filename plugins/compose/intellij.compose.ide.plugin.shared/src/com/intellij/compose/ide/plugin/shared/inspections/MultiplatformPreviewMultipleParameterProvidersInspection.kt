// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.compose.ide.plugin.shared.isPreviewParameterAnnotation
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Inspection that checks that any function annotated with `@Preview`, or with a MultiPreview, has
 * at most one `@PreviewParameter`.
 * Based on `com.android.tools.idea.compose.preview.PreviewMultipleParameterProvidersInspection`
 */
open class MultiplatformPreviewMultipleParameterProvidersInspection :
  BasePreviewAnnotationInspection(
    ComposeIdeBundle.message("compose.preview.inspection.group.name"),
    ComposePreviewAnnotationChecker
  ) {
  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    function: KtNamedFunction,
    previewAnnotation: KtAnnotationEntry,
  ) {
    // Find the second PreviewParameter annotation if any
    val secondPreviewParameter =
      function.valueParameters
        .mapNotNull { it.annotationEntries.firstOrNull { it.isPreviewParameterAnnotation() } }
        .drop(1)
        .firstOrNull() ?: return

    // Flag the second annotation as the error
    holder.registerProblem(
      secondPreviewParameter as PsiElement,
      ComposeIdeBundle.message("compose.preview.inspection.no.multiple.preview.provider.description"),
      ProblemHighlightType.ERROR,
    )
  }

  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    annotationClass: KtClass,
    previewAnnotation: KtAnnotationEntry,
  ) {
    // This inspection only applies for functions, not for Annotation classes
    return
  }
}
