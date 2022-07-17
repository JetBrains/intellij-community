// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TextResult

object ValVarExpression : Expression() {
    private val cachedLookupElements = listOf("val", "var").map { LookupElementBuilder.create(it) }.toTypedArray<LookupElement>()

    override fun calculateResult(context: ExpressionContext?): Result = TextResult("val")

    override fun calculateQuickResult(context: ExpressionContext?): Result? = calculateResult(context)

    override fun calculateLookupItems(context: ExpressionContext?): Array<LookupElement> = cachedLookupElements
}