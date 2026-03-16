// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelector
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.endOffset
import com.intellij.util.Function
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtBreakExpression
import org.jetbrains.kotlin.psi.KtContinueExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTypeElement
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
                    @Suppress("TestOnlyProblems")
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

internal class ExpressionTypeFilter(val predicate: KaSession.(KaType) -> Boolean) : (KtExpression) -> Boolean {
    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun invoke(expression: KtExpression): Boolean {
        allowAnalysisOnEdt {
            @OptIn(KaAllowAnalysisFromWriteAction::class)
            allowAnalysisFromWriteAction {
                analyze(expression) {
                    val type = expression.expressionType
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