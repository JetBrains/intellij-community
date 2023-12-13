// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtForExpression

class KotlinForConditionFixer : MissingConditionFixer<KtForExpression>() {
    override val keyword = "for"
    override fun getElement(element: PsiElement?) = element as? KtForExpression
    override fun getCondition(element: KtForExpression) =
        element.loopRange ?: element.loopParameter ?: element.destructuringDeclaration

    override fun getLeftParenthesis(element: KtForExpression) = element.leftParenthesis
    override fun getRightParenthesis(element: KtForExpression) = element.rightParenthesis
    override fun getBody(element: KtForExpression) = element.body
}
