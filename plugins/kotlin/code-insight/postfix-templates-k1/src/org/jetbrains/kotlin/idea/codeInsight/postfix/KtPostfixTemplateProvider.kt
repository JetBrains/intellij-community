// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.*
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil.findElementOfClassAtRange
import com.intellij.util.Function
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyze
import org.jetbrains.kotlin.idea.intentions.negate
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceVariable.K1IntroduceVariableHandler
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiverOrThis
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isBoolean

// K1 PostfixTemplateProvider
class KtPostfixTemplateProvider : PostfixTemplateProvider {
    private val templatesSet by lazy {
        setOf(
            KtNotPostfixTemplate(this), // k2
            KtIfExpressionPostfixTemplate(this), // k2
            KtElseExpressionPostfixTemplate(this),
            KtNotNullPostfixTemplate("notnull", this), // k2
            KtNotNullPostfixTemplate("nn", this), // k2
            KtIsNullPostfixTemplate(this),
            KtWhenExpressionPostfixTemplate(this),
            KtTryPostfixTemplate(this), // k2
            KtIntroduceVariablePostfixTemplate("val", this), // k2
            KtIntroduceVariablePostfixTemplate("var", this), // k2
            KtForEachPostfixTemplate("for", this), // k2
            KtForEachPostfixTemplate("iter", this), // k2
            KtForReversedPostfixTemplate("forr", this), // k2
            KtForWithIndexPostfixTemplate("fori", this), // k2
            KtForLoopNumbersPostfixTemplate("fori", this), // k2
            KtForLoopReverseNumbersPostfixTemplate("forr", this),
            KtAssertPostfixTemplate(this), // k2
            KtParenthesizedPostfixTemplate(this), // k2
            KtSoutPostfixTemplate(this), // k2
            KtReturnPostfixTemplate(this), // k2
            KtWhilePostfixTemplate(this), // k2
            KtWrapWithListOfPostfixTemplate(this), // k2
            KtWrapWithSetOfPostfixTemplate(this), // k2
            KtWrapWithArrayOfPostfixTemplate(this),
            KtWrapWithSequenceOfPostfixTemplate(this),
            KtSpreadPostfixTemplate(this), // k2
            KtArgumentPostfixTemplate(this),
            KtWithPostfixTemplate(this), // k2
        )
    }

    override fun getTemplates() = templatesSet

    override fun isTerminalSymbol(currentChar: Char) = currentChar == '.' || currentChar == '!'

    override fun afterExpand(file: PsiFile, editor: Editor) {
    }

    override fun preCheck(copyFile: PsiFile, realEditor: Editor, currentOffset: Int) = copyFile

    override fun preExpand(file: PsiFile, editor: Editor) {
    }
}

private class KtNotPostfixTemplate(provider: PostfixTemplateProvider) : NotPostfixTemplate(
    KtPostfixTemplatePsiInfo,
    createExpressionSelector { it.isBoolean() },
    provider
)

private class KtIntroduceVariablePostfixTemplate(
    val kind: String,
    provider: PostfixTemplateProvider
) : PostfixTemplateWithExpressionSelector(kind, kind, "$kind name = expression", createExpressionSelector(), provider) {
    override fun expandForChooseExpression(expression: PsiElement, editor: Editor) {
        K1IntroduceVariableHandler.collectCandidateTargetContainersAndDoRefactoring(
            expression.project, editor, expression as KtExpression,
            isVar = kind == "var",
            occurrencesToReplace = null,
            onNonInteractiveFinish = null
        )
    }
}

internal object KtPostfixTemplatePsiInfo : PostfixTemplatePsiInfo() {
    override fun createExpression(context: PsiElement, prefix: String, suffix: String) =
        KtPsiFactory(context.project).createExpression(prefix + context.text + suffix)

    override fun getNegatedExpression(element: PsiElement) = (element as KtExpression).negate()
}

internal fun createExpressionSelector(
    checkCanBeUsedAsValue: Boolean = true,
    statementsOnly: Boolean = false,
    typePredicate: ((KotlinType) -> Boolean)? = null
): PostfixTemplateExpressionSelector {
    val predicate: ((KtExpression, BindingContext) -> Boolean)? =
        if (typePredicate != null) { expression, bindingContext ->
            expression.getType(bindingContext)?.let(typePredicate) ?: false
        }
        else null
    return createExpressionSelectorWithComplexFilter(checkCanBeUsedAsValue, statementsOnly, predicate)
}

internal fun createExpressionSelectorWithComplexFilter(
    // Do not suggest expressions like 'val a = 1'/'for ...'
    checkCanBeUsedAsValue: Boolean = true,
    statementsOnly: Boolean = false,
    predicate: ((KtExpression, BindingContext) -> Boolean)? = null
): PostfixTemplateExpressionSelector = KtExpressionPostfixTemplateSelector(checkCanBeUsedAsValue, statementsOnly, predicate)

private class KtExpressionPostfixTemplateSelector(
    private val checkCanBeUsedAsValue: Boolean,
    private val statementsOnly: Boolean,
    private val predicate: ((KtExpression, BindingContext) -> Boolean)?
) : PostfixTemplateExpressionSelector {

    private fun filterElement(element: PsiElement): Boolean {
        if (element !is KtExpression) return false

        if (element.parent is KtThisExpression) return false

        // Can't be independent expressions
        if (element.isSelector || element.parent is KtUserType || element.isOperationReference || element is KtBlockExpression) return false

        // Both KtLambdaExpression and KtFunctionLiteral have the same offset, so we add only one of them -> KtLambdaExpression
        if (element is KtFunctionLiteral) return false

        if (statementsOnly) {
            // We use getQualifiedExpressionForReceiverOrThis because when postfix completion is run on some statement like:
            // foo().try<caret>
            // `element` points to `foo()` call, while we need to select the whole statement with `try` selector
            // to check if it's in a statement position
            if (!KtPsiUtil.isStatement(element.getQualifiedExpressionForReceiverOrThis())) return false
        }
        if (checkCanBeUsedAsValue && !element.canBeUsedAsValue()) return false

        return predicate?.invoke(element, element.safeAnalyze(element.getResolutionFacade(), BodyResolveMode.PARTIAL)) ?: true
    }

    private fun KtExpression.canBeUsedAsValue() =
        !KtPsiUtil.isAssignment(this) &&
                !this.isNamedDeclaration &&
                this !is KtLoopExpression &&
                // if's only with else may be treated as expressions
                !isIfWithoutElse

    private val KtExpression.isIfWithoutElse: Boolean
        get() = (this is KtIfExpression && this.elseKeyword == null)

    override fun getExpressions(context: PsiElement, document: Document, offset: Int): List<PsiElement> {
        val originalFile = context.containingFile.originalFile
        val textRange = context.textRange
        val originalElement = findElementOfClassAtRange(originalFile, textRange.startOffset, textRange.endOffset, context::class.java)
            ?: return emptyList()

        val expressions = originalElement.parentsWithSelf
            .filterIsInstance<KtExpression>()
            .takeWhile { !it.isBlockBodyInDeclaration }

        val boundExpression = expressions.firstOrNull { it.parent.endOffset > offset }
        val boundElementParent = boundExpression?.parent
        val filteredByOffset = expressions.takeWhile { it != boundElementParent }.toMutableList()
        if (boundElementParent is KtDotQualifiedExpression && boundExpression == boundElementParent.receiverExpression) {
            val qualifiedExpressionEnd = boundElementParent.endOffset
            expressions
                .dropWhile { it != boundElementParent }
                .drop(1)
                .takeWhile { it.endOffset == qualifiedExpressionEnd }
                .toCollection(filteredByOffset)
        }

        val result = filteredByOffset.filter(this::filterElement)

        if (isUnitTestMode() && result.size > 1) {
            with(KotlinPostfixTemplateInfo) {
                originalFile.suggestedExpressions = result.map { it.text }
            }
        }

        return result
    }

    override fun hasExpression(context: PsiElement, copyDocument: Document, newOffset: Int): Boolean =
        getExpressions(context, copyDocument, newOffset).isNotEmpty()

    override fun getRenderer() = Function(PsiElement::getText)
}

private val KtExpression.isOperationReference: Boolean
    get() = this.node.elementType == KtNodeTypes.OPERATION_REFERENCE

private val KtElement.isBlockBodyInDeclaration: Boolean
    get() = this is KtBlockExpression && (parent as? KtElement)?.isNamedDeclarationWithBody == true

private val KtElement.isNamedDeclaration: Boolean
    get() = this is KtNamedDeclaration && !isAnonymousFunction

private val KtElement.isNamedDeclarationWithBody: Boolean
    get() = this is KtDeclarationWithBody && !isAnonymousFunction

private val KtDeclaration.isAnonymousFunction: Boolean
    get() = this is KtFunctionLiteral || (this is KtNamedFunction && this.name == null)

private val KtExpression.isSelector: Boolean
    get() = parent is KtQualifiedExpression && (parent as KtQualifiedExpression).selectorExpression == this
