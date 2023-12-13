// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtIfExpression

class KotlinIfConditionFixer : MissingConditionFixer<KtIfExpression>() {
    override val keyword = "if"
    override fun getElement(element: PsiElement?) = element as? KtIfExpression
    override fun getCondition(element: KtIfExpression) = element.condition
    override fun getLeftParenthesis(element: KtIfExpression) = element.leftParenthesis
    override fun getRightParenthesis(element: KtIfExpression) = element.rightParenthesis
    override fun getBody(element: KtIfExpression) = element.then
}
