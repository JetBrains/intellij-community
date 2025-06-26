// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared.inspections

import com.intellij.compose.ide.plugin.shared.JETPACK_PREVIEW_CLASS_ID
import com.intellij.compose.ide.plugin.shared.JETPACK_PREVIEW_FQ_NAME
import com.intellij.compose.ide.plugin.shared.MULTIPLATFORM_PREVIEW_CLASS_ID
import com.intellij.compose.ide.plugin.shared.MULTIPLATFORM_PREVIEW_FQ_NAME
import com.intellij.compose.ide.plugin.shared.classIdMatches
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtImportDirective

/**
 * Implementation of [PreviewAnnotationChecker] specifically for Compose Previews.
 */
internal object ComposePreviewAnnotationChecker : PreviewAnnotationChecker {
  override fun isPreview(importDirective: KtImportDirective) =
    MULTIPLATFORM_PREVIEW_FQ_NAME == importDirective.importedFqName ||
    JETPACK_PREVIEW_FQ_NAME == importDirective.importedFqName

  override fun isPreview(annotation: KtAnnotationEntry) = analyze(annotation) {
    classIdMatches(annotation, MULTIPLATFORM_PREVIEW_CLASS_ID) ||
    classIdMatches(annotation, JETPACK_PREVIEW_CLASS_ID)
  }

  override fun isPreviewOrMultiPreview(annotation: KtAnnotationEntry) =
    isPreview(annotation) || annotation.isMultiPreview()
}
