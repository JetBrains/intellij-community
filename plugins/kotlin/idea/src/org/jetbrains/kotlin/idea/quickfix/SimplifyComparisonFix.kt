// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticWithParameters2
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.core.replaced
import org.jetbrains.kotlin.idea.intentions.SimplifyBooleanWithConstantsIntention
import org.jetbrains.kotlin.idea.quickfix.SimplifyIfExpressionFix.Companion.getConditionConstantValueIfAny
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class SimplifyComparisonFix(element: KtExpression, val value: Boolean) : KotlinQuickFixAction<KtExpression>(element) {
    override fun getFamilyName() = KotlinBundle.message("simplify.0.to.1", element.toString(), value)

    override fun getText() = KotlinBundle.message("simplify.comparison")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val replacement = KtPsiFactory(element).createExpression("$value")
        val result = element.replaced(replacement)

        val booleanExpression = result.getNonStrictParentOfType<KtBinaryExpression>()
        val simplifyIntention = SimplifyBooleanWithConstantsIntention()
        if (booleanExpression != null && simplifyIntention.isApplicableTo(booleanExpression)) {
            simplifyIntention.applyTo(booleanExpression, editor)
        } else {
            simplifyIntention.removeRedundantAssertion(result)
        }

        val ifExpression = result.getStrictParentOfType<KtIfExpression>()?.takeIf { it.condition == result }
        if (ifExpression != null) {
            val conditionValue = ifExpression.getConditionConstantValueIfAny()
            if (conditionValue != null) {
                SimplifyIfExpressionFix.simplifyIfPossible(ifExpression, conditionValue)
            }
        }
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val expression = diagnostic.psiElement as? KtExpression ?: return null
            val value = (diagnostic as? DiagnosticWithParameters2<*, *, *>)?.b as? Boolean ?: return null
            return SimplifyComparisonFix(expression, value)
        }
    }
}