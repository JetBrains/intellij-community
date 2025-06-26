// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.compose.ide.plugin.shared.COMPOSABLE_ANNOTATION_FQ_NAME
import com.intellij.compose.ide.plugin.shared.isComposableAnnotation
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Base class for all inspections that depend on methods and annotation classes annotated with
 * `@Preview`, or with a multi-preview (see [isMultiPreview] function for details).
 * Forked from `com.android.tools.compose.inspection.BasePreviewAnnotationInspection`
 */
abstract class BasePreviewAnnotationInspection(
  @Nls private val groupDisplayName: String,
  previewAnnotationChecker: PreviewAnnotationChecker,
) : AbstractKotlinInspection(), PreviewAnnotationChecker by previewAnnotationChecker {
  /**
   * Will be true if the inspected file imports the `@Preview` annotation. This is used as a
   * shortcut to avoid analyzing all kotlin files
   */
  var isPreviewFile: Boolean = false
  /**
   * Will be true if the inspected file imports the `@Composable` annotation. This is used as a
   * shortcut to avoid analyzing all kotlin files
   */
  var isComposableFile: Boolean = false

  /**
   * Called for every `@Preview` and MultiPreview annotation, that is annotating a function.
   *
   * @param holder A [ProblemsHolder] user to report problems
   * @param function The function that was annotated with `@Preview` or with a MultiPreview
   * @param previewAnnotation The `@Preview` or MultiPreview annotation
   */
  abstract fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    function: KtNamedFunction,
    previewAnnotation: KtAnnotationEntry,
  )

  /**
   * Called for every `@Preview` and MultiPreview annotation, that is annotating an annotation
   * class.
   *
   * @param holder A [ProblemsHolder] user to report problems
   * @param annotationClass The annotation class that was annotated with `@Preview` or with a
   *   MultiPreview
   * @param previewAnnotation The `@Preview` or MultiPreview annotation
   */
  abstract fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    annotationClass: KtClass,
    previewAnnotation: KtAnnotationEntry,
  )

  final override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor =
    object : KtVisitorVoid() {
      override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)

        isPreviewFile = isPreviewFile || isPreview(importDirective)
        isComposableFile = isComposableFile || COMPOSABLE_ANNOTATION_FQ_NAME == importDirective.importedFqName
      }

      override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
        super.visitAnnotationEntry(annotationEntry)

        isPreviewFile = isPreviewFile || isPreview(annotationEntry)
        isComposableFile = isComposableFile || annotationEntry.isComposableAnnotation()
      }

      override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)

        if (!isPreviewFile && !isComposableFile) {
          return
        }

        function.annotationEntries.forEach {
          if (isPreviewOrMultiPreview(it)) {
            visitPreviewAnnotation(holder, function, it)
          }
        }
      }

      override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        if (!klass.isAnnotation()) return

        klass.annotationEntries.forEach {
          if (isPreviewOrMultiPreview(it)) {
            visitPreviewAnnotation(holder, klass, it)
          }
        }
      }
    }

  final override fun getGroupDisplayName(): String {
    return groupDisplayName
  }
}
