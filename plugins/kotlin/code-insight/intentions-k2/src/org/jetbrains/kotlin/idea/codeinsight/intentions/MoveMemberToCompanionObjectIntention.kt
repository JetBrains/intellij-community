// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.intentions

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

internal class MoveMemberToCompanionObjectIntention : MoveMemberIntention(
    textGetter = KotlinBundle.messagePointer("move.to.companion.object")
) {
    override fun applicabilityRange(element: KtNamedDeclaration): TextRange? {
        if (!isApplicableForMoveMember(element)) return null
        if (element.containingClassOrObject is KtObjectDeclaration) return null
        return findTextRangeForMoveMemberIntention(element)
    }

    override fun getTarget(element: KtNamedDeclaration): K2MoveTargetDescriptor.Declaration<*>? {
        return (element.containingClassOrObject as? KtClass)?.let {
            K2MoveTargetDescriptor.CompanionObject(it)
        }
    }

    override fun startInWriteAction(): Boolean = false
}