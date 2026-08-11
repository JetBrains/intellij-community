// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix.editable

import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.editable.EditablePostfixTemplateWithMultipleExpressions
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtStatementExpression
import org.jetbrains.kotlin.utils.yieldIfNotNull

// Note: Override of equals/hashCode is needed if we add custom properties
@Suppress("PostfixTemplateDescriptionNotFound")
@ApiStatus.Internal
class KotlinEditablePostfixTemplate : EditablePostfixTemplateWithMultipleExpressions<KotlinPostfixTemplateExpressionCondition> {

    constructor(
        templateId: String,
        templateName: String,
        liveTemplate: TemplateImpl,
        example: String,
        expressionConditions: Set<KotlinPostfixTemplateExpressionCondition>,
        useTopmostExpression: Boolean,
        provider: PostfixTemplateProvider
    ) : super(templateId, templateName, liveTemplate, example, expressionConditions, useTopmostExpression, provider)

    constructor(
        templateId: String,
        templateName: String,
        templateText: String,
        example: String,
        expressionConditions: Set<KotlinPostfixTemplateExpressionCondition>,
        useTopmostExpression: Boolean,
        provider: PostfixTemplateProvider
    ) : super(
        /* templateId = */ templateId,
        /* templateName = */ templateName,
        /* liveTemplate = */ createTemplate(templateText),
        /* example = */ example,
        /* expressionConditions = */ expressionConditions,
        /* useTopmostExpression = */ useTopmostExpression,
        /* provider = */ provider
    )

    private fun PsiElement.applicableElements(): Sequence<KtExpression> = sequence {
        var current = this@applicableElements
        while (true) {
            yieldIfNotNull(current as? KtExpression)
            val parent = current.parent
            val parentParent = parent?.parent
            when {
                parentParent is KtIfExpression && parentParent.`else` == current -> current = parentParent
                // In Kotlin, we do not want to stop at the argument list boundary if we are at a lambda argument
                // that is supplied as the last argument outside the parentheses.
                // For example, `listOf(1, 2).forEach { }.<caret>` should include the entire expression rather than just the function literal.
                parent is KtStatementExpression && parent !is KtFunctionLiteral -> break
                parent is KtExpression || parent is KtLambdaArgument -> current = parent
                else -> break
            }
        }
    }

    override fun getTopmostExpression(element: PsiElement): PsiElement {
        // We take any expression going upwards until we hit a boundary of something that is not "just" an expression anymore
        // but is also a statement
        return element.applicableElements().lastOrNull() ?: element
    }

    @OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
    override fun getExpressions(
        context: PsiElement,
        document: Document,
        offset: Int
    ): List<PsiElement> {
        val expressionAtCaret = context.parentOfType<KtExpression>() ?: return emptyList()
        val expressions = if (myUseTopmostExpression) {
            val containingExpression = getTopmostExpression(expressionAtCaret) as? KtExpression
            listOfNotNull(containingExpression)
        } else {
            expressionAtCaret.applicableElements()
                .filter { it !is KtParenthesizedExpression }
                .toList()
        }

        allowAnalysisOnEdt {
            allowAnalysisFromWriteAction {
                return expressions.filter {
                    it.textRange.endOffset == offset && !PsiTreeUtil.hasErrorElements(it) &&
                            expressionCompositeCondition.value(it)
                }
            }
        }
    }

    override fun getRangeToRemove(element: PsiElement): TextRange {
        val elementToRemove = getElementToRemove(element)
        if (elementToRemove is KtStatementExpression) {
            var lastChild = elementToRemove.lastChild
            while (lastChild is PsiComment || lastChild is PsiWhiteSpace) {
                lastChild = lastChild.prevSibling
            }
            if (lastChild != null) {
                return TextRange(elementToRemove.textRange.startOffset, lastChild.textRange.endOffset)
            }
        }
        return elementToRemove.textRange
    }

    override fun isBuiltin(): Boolean = false
}
