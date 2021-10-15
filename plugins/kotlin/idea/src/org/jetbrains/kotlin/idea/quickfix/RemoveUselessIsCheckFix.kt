// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.inspections.ConstantConditionIfInspection
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class RemoveUselessIsCheckFix(
    element: KtIsExpression,
    private val constantConditionIfFix: ConstantConditionIfInspection.ConstantConditionIfFix?
) : KotlinQuickFixAction<KtIsExpression>(element) {
    override fun getFamilyName() = KotlinBundle.message("remove.useless.is.check")

    override fun getText(): String = familyName

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        element?.run {
            val parentIf = parentIfExpression()
            if (parentIf != null && constantConditionIfFix != null) {
                constantConditionIfFix.applyFix(parentIf)
            } else {
                val expressionsText = this.isNegated.not().toString()
                val newExpression = KtPsiFactory(project).createExpression(expressionsText)
                replace(newExpression)
            }
        }
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): KotlinQuickFixAction<KtIsExpression>? {
            val expression = diagnostic.psiElement.getNonStrictParentOfType<KtIsExpression>() ?: return null
            val constantConditionIfFix = expression.parentIfExpression()
                ?.let { ConstantConditionIfInspection.collectFixes(it, expression.isNegated.not()) }
                ?.singleOrNull()
            return RemoveUselessIsCheckFix(expression, constantConditionIfFix)
        }

        private fun KtExpression.parentIfExpression() = getStrictParentOfType<KtIfExpression>()?.takeIf { it.condition == this }
    }
}
