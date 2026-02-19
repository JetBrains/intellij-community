// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections.suppress

import com.intellij.codeInsight.intention.FileModifier.SafeTypeForPreview
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls

@SafeTypeForPreview
class AnnotationHostKind(
    /** Human-readable `KtElement` kind on which the annotation is placed. E.g., 'file', 'class' or 'statement'. */
    @Nls val kind: String,

    /** Name of the annotation owner. Might be null if the owner is not a named declaration (for instance, if it is a statement). */
    @NlsSafe val name: String?,

    /** True if the annotation needs to be added to a separate line. */
    val newLineNeeded: Boolean
)
