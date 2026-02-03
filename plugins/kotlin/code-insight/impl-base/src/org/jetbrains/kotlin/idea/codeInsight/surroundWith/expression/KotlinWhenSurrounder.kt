// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.surroundWith.expression

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.diagnostics.WhenMissingCase
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.KotlinExpressionSurrounder
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddRemainingWhenBranchesUtils
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtWhenExpression

class KotlinWhenSurrounder : KotlinExpressionSurrounder() {
    @NlsSafe
    override fun getTemplateDescription(): String = "when (expr) {}"

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun surroundExpression(context: ActionContext, expression: KtExpression, updater: ModPsiUpdater) {
        val template = "when(a) { \nb -> {}\n else -> {}\n}"

        val project = context.project
        val factory = KtPsiFactory(project)
        val whenExpression =
            (factory.createExpression(template) as KtWhenExpression).let {
                it.subjectExpression?.replace(expression)
                expression.replaced(it)
            }

        val remainingBranches = allowAnalysisOnEdt {
            analyze(whenExpression) {
                whenExpression.computeMissingCases().takeIf {
                    it.isNotEmpty() && it.singleOrNull() != WhenMissingCase.Unknown
                }
            }
        }

        if (remainingBranches != null) {
            val elementContext = AddRemainingWhenBranchesUtils.ElementContext(remainingBranches, enumToStarImport = null)
            AddRemainingWhenBranchesUtils.addRemainingWhenBranches(whenExpression, elementContext)
            whenExpression.entries.also {
                //remove `b -> {}` fake branch
                it.first().delete()
                //remove `else -> {}` fake branch
                it.last().delete()
            }
        }

        val document = whenExpression.containingFile.getFileDocument()
        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        psiDocumentManager.doPostponedOperationsAndUnblockDocument(document)

        val firstEntry = whenExpression.entries.first()
        val offset = if (remainingBranches != null) {
            firstEntry.expression?.startOffset ?: firstEntry.startOffset
        } else {
            val condition = firstEntry.conditions.first()
            val textRange = condition.textRange
            val offset = textRange.startOffset
            document.deleteString(textRange.startOffset, textRange.endOffset)
            offset
        }
        updater.select(TextRange.from(offset, 0))
        psiDocumentManager.commitDocument(document)
    }
}
