// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelector
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilCore
import com.intellij.refactoring.suggested.endOffset
import com.intellij.util.Function
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiverOrThis
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

fun allExpressions(vararg filters: (KtExpression) -> Boolean): PostfixTemplateExpressionSelector {
    return selector { file, offset ->
        collectExpressions(file, offset).filter { expression ->
            filters.all { it(expression) }
        }.also { expressions ->
            if (isUnitTestMode()) {
                val expressionTexts = expressions.toList().map { it.text }
                if (expressionTexts.size > 1) {
                    with(KotlinPostfixTemplateInfo) { file.suggestedExpressions = expressionTexts }
                }
            }
        }
    }
}

internal object ValuedFilter : (KtExpression) -> Boolean {
    override fun invoke(expression: KtExpression): Boolean {
        val isAnonymousFunction = expression is KtFunctionLiteral
                || (expression is KtNamedFunction && expression.name == null)

        return when {
            KtPsiUtil.isAssignment(expression) -> false
            expression is KtNamedDeclaration && !isAnonymousFunction -> false
            expression is KtLoopExpression -> false
            expression is KtReturnExpression -> false
            expression is KtBreakExpression -> false
            expression is KtContinueExpression -> false
            expression is KtIfExpression && expression.`else` == null -> false
            else -> true
        }
    }
}

internal object StatementFilter : (KtExpression) -> Boolean {
    override fun invoke(expression: KtExpression): Boolean {
        return KtPsiUtil.isStatement(expression.getQualifiedExpressionForReceiverOrThis())
    }
}

internal class ExpressionTypeFilter(val predicate: KtAnalysisSession.(KtType) -> Boolean) : (KtExpression) -> Boolean {
    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun invoke(expression: KtExpression): Boolean {
        allowAnalysisOnEdt {
            @OptIn(KtAllowAnalysisFromWriteAction::class)
            allowAnalysisFromWriteAction {
                analyze(expression) {
                    val type = expression.getKtType()
                    return type != null && predicate(type)
                }
            }
        }
    }
}

internal object NonPackageAndNonImportFilter : (KtExpression) -> Boolean {
    override fun invoke(expression: KtExpression): Boolean {
        val parent = expression.parent
        return parent !is KtPackageDirective && parent !is KtImportDirective
    }
}

private fun selector(collector: (PsiFile, Int) -> Sequence<KtExpression>): PostfixTemplateExpressionSelector {
    return object : PostfixTemplateExpressionSelector {
        override fun getExpressions(context: PsiElement, document: Document, offset: Int): List<PsiElement> {
            return collector(context.containingFile, offset).toList()
        }

        override fun hasExpression(context: PsiElement, copyDocument: Document, newOffset: Int): Boolean {
            return collector(context.containingFile, newOffset).any()
        }

        override fun getRenderer(): Function<PsiElement, String> {
            return Function(PsiElement::getText)
        }
    }
}

private fun collectExpressions(file: PsiFile, offset: Int): Sequence<KtExpression> {
    return PsiUtilCore.getElementAtOffset(file, offset - 1).parentsWithSelf
        .filterIsInstance<KtExpression>()
        .filter { it.endOffset == offset }
        .filter { expression ->
            val parent = expression.parent
            when {
                expression is KtBlockExpression -> false
                expression is KtFunctionLiteral -> false // Use containing 'KtLambdaExpression' instead
                expression is KtLambdaExpression && parent is KtLambdaArgument -> false
                parent is KtThisExpression -> false
                parent is KtQualifiedExpression && expression == parent.selectorExpression -> false
                expression.node.elementType == KtNodeTypes.OPERATION_REFERENCE -> false
                expression.getParentOfType<KtTypeElement>(strict = false) != null -> false
                else -> true
            }
        }
}