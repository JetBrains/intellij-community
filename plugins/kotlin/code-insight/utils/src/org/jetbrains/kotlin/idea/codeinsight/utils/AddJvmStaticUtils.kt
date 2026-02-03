// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

fun getAddJvmStaticApplicabilityRange(element: KtNamedDeclaration): TextRange? {
    if (element !is KtNamedFunction && element !is KtProperty) return null

    if (element.hasModifier(KtTokens.ABSTRACT_KEYWORD)) return null
    if (element.hasModifier(KtTokens.OPEN_KEYWORD)) return null
    if (element.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return null

    val containingObject = element.containingClassOrObject as? KtObjectDeclaration ?: return null
    if (containingObject.isObjectLiteral()) return null
    if (element is KtProperty) {
        if (element.hasModifier(KtTokens.CONST_KEYWORD)) return null
        if (KotlinPsiHeuristics.hasJvmFieldAnnotation(element)) return null
    }
    if (KotlinPsiHeuristics.hasJvmStaticAnnotation(element)) return null
    return element.nameIdentifier?.textRange
}
