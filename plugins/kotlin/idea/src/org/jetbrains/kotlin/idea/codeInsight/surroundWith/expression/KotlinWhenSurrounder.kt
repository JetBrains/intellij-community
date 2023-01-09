// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.surroundWith.expression

import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.idea.core.surroundWith.KotlinExpressionSurrounder
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.quickfix.AddWhenRemainingBranchesFix

class KotlinWhenSurrounder : KotlinExpressionSurrounder() {
    @NlsSafe
    override fun getTemplateDescription() = "when (expr) {}"

    override fun surroundExpression(project: Project, editor: Editor, expression: KtExpression): TextRange {
        val template = "when(a) { \nb -> {}\n else -> {}\n}"
        val whenExpression = (KtPsiFactory(project).createExpression(template) as KtWhenExpression).let {
            it.subjectExpression?.replace(expression)
            expression.replaced(it)
        }

        val hasRemainingBranches = AddWhenRemainingBranchesFix.isAvailable(whenExpression)
        if (hasRemainingBranches) {
            AddWhenRemainingBranchesFix.addRemainingBranches(whenExpression)
            whenExpression.entries.also {
                it.first().delete()
                it.last().delete()
            }
        }

        CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(whenExpression)

        val firstEntry = whenExpression.entries.first()
        val offset = if (hasRemainingBranches) {
            firstEntry.expression?.startOffset ?: firstEntry.startOffset
        } else {
            val conditionRange = firstEntry.conditions.first().textRange
            editor.document.deleteString(conditionRange.startOffset, conditionRange.endOffset)
            conditionRange.startOffset
        }
        return TextRange(offset, offset)
    }
}
