// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.intentions

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/**
 * Checks the common applicability conditions for the intentions that move a member to a companion object/block
 */
internal fun isApplicableForMoveMember(element: KtNamedDeclaration): Boolean {
    if (element !is KtNamedFunction && element !is KtProperty && element !is KtClassOrObject) return false
    if (element is KtEnumEntry) return false
    if (element is KtNamedFunction && element.bodyExpression == null) return false
    if (element is KtNamedFunction && element.valueParameterList == null) return false
    if ((element is KtNamedFunction || element is KtProperty) && element.hasModifier(KtTokens.ABSTRACT_KEYWORD)) return false
    if (element.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return false
    val containingClass = element.containingClassOrObject ?: return false
    if (containingClass.isLocal) return false
    if (containingClass is KtClass && containingClass.isInner()) return false

    return true
}

/**
 * Finds an applicability range for the intentions that move a member to a companion object/block.
 * For most of the declarations selects the name identifier range.
 * For properties with const modifier selects the range from the const keyword to the end of the name identifier.
 */
internal fun findTextRangeForMoveMemberIntention(element: KtNamedDeclaration): TextRange? {
    val nameIdentifier = element.nameIdentifier ?: return null
    if (element is KtProperty && element.hasModifier(KtTokens.CONST_KEYWORD) && !element.isVar) {
        val constElement = element.modifierList?.allChildren?.find { it.node.elementType == KtTokens.CONST_KEYWORD }
        if (constElement != null) return TextRange(constElement.startOffset, nameIdentifier.endOffset)
    }
    return nameIdentifier.textRange
}
