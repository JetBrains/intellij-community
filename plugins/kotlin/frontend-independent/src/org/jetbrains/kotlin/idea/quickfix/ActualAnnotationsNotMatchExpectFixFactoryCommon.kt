// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixActionBase
import org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix
import org.jetbrains.kotlin.psi.KtAnnotationEntry

object ActualAnnotationsNotMatchExpectFixFactoryCommon {
    fun createRemoveAnnotationFromExpectFix(expectAnnotationEntry: KtAnnotationEntry): QuickFixActionBase<*>? {
        val annotationName = expectAnnotationEntry.shortName ?: return null
        return RemoveAnnotationFix(
            KotlinBundle.message("fix.remove.mismatched.annotation.from.expect.declaration.may.change.semantics", annotationName),
            expectAnnotationEntry
        )
    }
}