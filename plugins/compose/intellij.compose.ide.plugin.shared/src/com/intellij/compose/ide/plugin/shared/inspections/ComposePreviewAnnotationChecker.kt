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
