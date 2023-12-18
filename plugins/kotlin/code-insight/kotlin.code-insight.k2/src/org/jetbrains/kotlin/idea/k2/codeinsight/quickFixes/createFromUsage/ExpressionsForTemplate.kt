// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TextResult
import com.intellij.psi.PsiDocumentManager

/**
 * An [Expression] class used by the template in [CreateKotlinCallablePsiEditor]. This class simply returns
 * [candidates] for the item look-up.
 */
internal open class ExpressionForCreateCallable(private val candidates: List<String>): Expression() {
    override fun calculateResult(context: ExpressionContext?): Result? {
        return if (candidates.isNotEmpty()) TextResult(candidates.first()) else null
    }

    override fun calculateLookupItems(context: ExpressionContext?): Array<LookupElement>? {
        val project = context?.project ?: return null
        val editor = context.editor ?: return null
        val document = editor.document
        PsiDocumentManager.getInstance(project).commitDocument(document)
        return if (candidates.isNotEmpty()) {
            Array(candidates.size) { index -> LookupElementBuilder.create(candidates[index]) }
        } else {
            emptyArray()
        }
    }
}

/**
 * This class is the same as [ExpressionForCreateCallable], but it provides an alternative parameter name `p$parameterIndex`
 * when it has no candidates for the parameter name.
 */
internal class ParameterNameExpression(private val parameterIndex: Int, candidates: List<String>): ExpressionForCreateCallable(candidates) {
    override fun calculateResult(context: ExpressionContext?): Result {
        return super.calculateResult(context) ?: TextResult("p$parameterIndex")
    }

    override fun calculateLookupItems(context: ExpressionContext?): Array<LookupElement>? {
        val result = super.calculateLookupItems(context) ?: return null
        return if (result.isNotEmpty()) result else arrayOf(LookupElementBuilder.create("p$parameterIndex"))
    }
}