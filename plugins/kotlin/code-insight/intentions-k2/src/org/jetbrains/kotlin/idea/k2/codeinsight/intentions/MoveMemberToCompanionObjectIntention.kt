// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class MoveMemberToCompanionObjectIntention : MoveMemberIntention(
    textGetter = KotlinBundle.messagePointer("move.to.companion.object")
) {
    override fun applicabilityRange(element: KtNamedDeclaration): TextRange? {
        if (element !is KtNamedFunction && element !is KtProperty && element !is KtClassOrObject) return null
        if (element is KtEnumEntry) return null
        if (element is KtNamedFunction && element.bodyExpression == null) return null
        if (element is KtNamedFunction && element.valueParameterList == null) return null
        if ((element is KtNamedFunction || element is KtProperty) && element.hasModifier(KtTokens.ABSTRACT_KEYWORD)) return null
        if (element.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return null
        val containingClass = element.containingClassOrObject as? KtClass ?: return null
        if (containingClass.isLocal || containingClass.isInner()) return null

        val nameIdentifier = element.nameIdentifier ?: return null
        if (element is KtProperty && element.hasModifier(KtTokens.CONST_KEYWORD) && !element.isVar) {
            val constElement = element.modifierList?.allChildren?.find { it.node.elementType == KtTokens.CONST_KEYWORD }
            if (constElement != null) return TextRange(constElement.startOffset, nameIdentifier.endOffset)
        }
        return nameIdentifier.textRange
    }

    override fun getTarget(element: KtNamedDeclaration): K2MoveTargetDescriptor.Declaration<*>? {
        return (element.containingClassOrObject as? KtClass)?.let {
            K2MoveTargetDescriptor.CompanionObject(it)
        }
    }

    override fun startInWriteAction(): Boolean = false
}