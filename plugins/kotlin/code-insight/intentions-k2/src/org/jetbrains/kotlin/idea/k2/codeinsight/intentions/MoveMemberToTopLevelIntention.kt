// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

internal class MoveMemberToTopLevelIntention : MoveMemberIntention(
    textGetter = KotlinBundle.messagePointer("move.to.top.level")
) {
    override fun applicabilityRange(element: KtNamedDeclaration): TextRange? {
        if (element !is KtNamedFunction && element !is KtProperty && element !is KtClassOrObject) return null
        if (element.containingClassOrObject !is KtClassOrObject) return null
        if (element is KtObjectDeclaration && element.isCompanion()) return null
        return element.nameIdentifier?.textRange
    }

    override fun getTarget(element: KtNamedDeclaration): K2MoveTargetDescriptor.Declaration<*> {
        return K2MoveTargetDescriptor.File(element.containingKtFile)
    }

    override fun startInWriteAction(): Boolean = false
}