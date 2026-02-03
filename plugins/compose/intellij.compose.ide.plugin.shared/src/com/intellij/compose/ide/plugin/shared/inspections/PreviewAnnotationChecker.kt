/*
 * Copyright (C) 2024 The Android Open Source Project
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
