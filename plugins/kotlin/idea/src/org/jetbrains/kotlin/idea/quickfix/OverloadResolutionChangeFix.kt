// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.utils.fqname.fqName
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinPsiOnlyQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.idea.inspections.dfa.getKotlinType
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class OverloadResolutionChangeFix(element: KtExpression) : KotlinPsiOnlyQuickFixAction<KtExpression>(element) {

    companion object : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            val expression = psiElement.safeAs<KtExpression>() ?: return emptyList()
            return listOf(OverloadResolutionChangeFix(expression))
        }
    }

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean = element != null

    private fun getTypeArgumentForCast(expression: KtExpression): String? {
        val exprType = expression.getKotlinType() ?: return null
        val fqName = exprType.fqName?.asString() ?: return null
        return when (fqName) {
            "kotlin.ranges.IntRange" -> "Int"
            "kotlin.ranges.CharRange" -> "Char"
            "kotlin.ranges.LongRange" -> "Long"
            "kotlin.ranges.UIntRange" -> "UInt"
            "kotlin.ranges.ULongRange" -> "ULong"
            else -> null
        }
    }


    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val expression = element ?: return
        val typeArgument = getTypeArgumentForCast(expression) ?: return
        val casted = KtPsiFactory(project).createExpressionByPattern("($0) as Iterable<$1>", expression, typeArgument)
        expression.replace(casted)
    }

    override fun getText(): String {
        val expression = element ?: return ""
        val typeArgument = getTypeArgumentForCast(expression) ?: return ""
        return QuickFixBundle.message("add.typecast.text", "Iterable<$typeArgument>")
    }

    override fun getFamilyName(): String = text
}