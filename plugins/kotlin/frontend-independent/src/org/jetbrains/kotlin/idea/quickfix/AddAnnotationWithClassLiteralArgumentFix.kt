// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.renderer.render

abstract class AddAnnotationWithClassLiteralArgumentFix(
    element: KtElement,
    annotationClassId: ClassId,
    kind: Kind = Kind.Self,
    existingAnnotationEntry: SmartPsiElementPointer<KtAnnotationEntry>? = null,
    private val argumentClassFqName: FqName? = null,
) : AddAnnotationFix(
    element,
    annotationClassId,
    kind,
    listOfNotNull(argumentClassFqName?.let { "${it.render()}::class" }),
    existingAnnotationEntry,
) {
    override fun renderArgumentsForIntentionName(): String {
        val shortName = argumentClassFqName?.shortName() ?: return ""
        return "($shortName::class)"
    }
}
