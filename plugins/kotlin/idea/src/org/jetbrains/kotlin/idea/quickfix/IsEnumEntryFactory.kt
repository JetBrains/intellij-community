// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object IsEnumEntryFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val element = diagnostic.psiElement.safeAs<KtTypeReference>()?.parent ?: return null
        return when (element) {
            is KtIsExpression -> if (element.typeReference == null) null else ReplaceWithComparisonFix(element)
            is KtWhenConditionIsPattern -> if (element.typeReference == null || element.isNegated) null else RemoveIsFix(element)
            else -> null
        }
    }

    private class ReplaceWithComparisonFix(isExpression: KtIsExpression) : KotlinQuickFixAction<KtIsExpression>(isExpression) {
        private val comparison = if (isExpression.isNegated) "!=" else "=="

        override fun getText() = KotlinBundle.message("replace.with.0", comparison)

        override fun getFamilyName() = text

        override fun invoke(project: Project, editor: Editor?, file: KtFile) {
            val leftHandSide = element?.leftHandSide ?: return
            val typeReference = element?.typeReference?.text ?: return
            val binaryExpression = KtPsiFactory(project).createExpressionByPattern("$0 $comparison $1", leftHandSide, typeReference)
            element?.replace(binaryExpression)
        }
    }

    private class RemoveIsFix(isPattern: KtWhenConditionIsPattern) : KotlinQuickFixAction<KtWhenConditionIsPattern>(isPattern) {
        override fun getText() = KotlinBundle.message("remove.expression", "is")

        override fun getFamilyName() = text

        override fun invoke(project: Project, editor: Editor?, file: KtFile) {
            val typeReference = element?.typeReference?.text ?: return
            element?.replace(KtPsiFactory(project).createWhenCondition(typeReference))
        }
    }
}
