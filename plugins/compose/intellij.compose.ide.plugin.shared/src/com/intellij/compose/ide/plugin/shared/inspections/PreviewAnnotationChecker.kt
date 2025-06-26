// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared.inspections

import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtImportDirective

/**
 * Base interface that can be used for checking whether a given [KtAnnotationEntry] or
 * [KtImportDirective] is associated with a corresponding Preview tool annotation.
 * This can potentially be expanded to not only Compose previews (like Android Designer does), hence an interface.
 * Forked from `com.android.tools.compose.inspection.PreviewAnnotationChecker`
 */
interface PreviewAnnotationChecker {
  fun isPreview(importDirective: KtImportDirective): Boolean

  fun isPreview(annotation: KtAnnotationEntry): Boolean

  @RequiresReadLock
  fun isPreviewOrMultiPreview(annotation: KtAnnotationEntry): Boolean
}
