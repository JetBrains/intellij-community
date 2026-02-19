// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.NotPostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelector
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateWithExpressionSelector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceVariable.K1IntroduceVariableHandler
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.types.KotlinType

// K1 PostfixTemplateProvider
@K1Deprecation
class KtPostfixTemplateProvider : PostfixTemplateProvider {
    private val templateSet: Set<PostfixTemplateWithExpressionSelector> by lazy {
        @Suppress("SpellCheckingInspection")
        setOf(
            KtNotPostfixTemplate(this),
            KtIfExpressionPostfixTemplate(this),
            KtElseExpressionPostfixTemplate(this),
            KtNotNullPostfixTemplate("notnull", this),
            KtNotNullPostfixTemplate("nn", this),
            KtIsNullPostfixTemplate(this),
            KtWhenExpressionPostfixTemplate(this),
            KtTryPostfixTemplate(this),
            KtIntroduceVariablePostfixTemplate("val", this),
            KtIntroduceVariablePostfixTemplate("var", this),
            KtForEachPostfixTemplate("for", this),
            KtForEachPostfixTemplate("iter", this),
            KtForReversedPostfixTemplate("forr", this),
            KtForWithIndexPostfixTemplate("fori", this),
            KtForLoopNumbersPostfixTemplate("fori", this),
            KtForLoopReverseNumbersPostfixTemplate("forr", this),
            KtAssertPostfixTemplate(this),
            KtParenthesizedPostfixTemplate(this),
            KtSoutPostfixTemplate(this),
            KtReturnPostfixTemplate(this),
            KtWhilePostfixTemplate(this),
            KtWrapWithListOfPostfixTemplate(this),
            KtWrapWithSetOfPostfixTemplate(this),
            KtWrapWithArrayOfPostfixTemplate(this),
            KtWrapWithSequenceOfPostfixTemplate(this),
            KtSpreadPostfixTemplate(this),
            KtArgumentPostfixTemplate(this),
            KtWithPostfixTemplate(this),
        )
    }

    override fun getTemplates(): Set<PostfixTemplateWithExpressionSelector> = templateSet

    override fun isTerminalSymbol(currentChar: Char): Boolean = currentChar == '.' || currentChar == '!'

    override fun afterExpand(file: PsiFile, editor: Editor) {
    }

    override fun preCheck(copyFile: PsiFile, realEditor: Editor, currentOffset: Int): PsiFile = copyFile

    override fun preExpand(file: PsiFile, editor: Editor) {
    }
}

@OptIn(KaContextParameterApi::class)
private class KtNotPostfixTemplate(provider: PostfixTemplateProvider) : NotPostfixTemplate(
    KtPostfixTemplatePsiInfo,
    createBooleanExpressionSelector(),
    provider
)

private class KtIntroduceVariablePostfixTemplate(
    val kind: String,
    provider: PostfixTemplateProvider
) : PostfixTemplateWithExpressionSelector(kind, kind, "$kind name = expression", createPostfixExpressionSelector(), provider) {
    override fun expandForChooseExpression(expression: PsiElement, editor: Editor) {
        K1IntroduceVariableHandler.collectCandidateTargetContainersAndDoRefactoring(
            expression.project, editor, expression as KtExpression,
            isVar = kind == "var",
            occurrencesToReplace = null,
            onNonInteractiveFinish = null
        )
    }
}

@Deprecated("use createPostfixExpressionSelector() instead", ReplaceWith("createPostfixExpressionSelector()"))
internal fun createExpressionSelector(
    checkCanBeUsedAsValue: Boolean = true,
    statementsOnly: Boolean = false,
    typePredicate: ((KotlinType) -> Boolean)? = null
): PostfixTemplateExpressionSelector {
    val predicate: ((KtExpression, BindingContext) -> Boolean)? =
        typePredicate?.let { predicate ->
            { expression, bindingContext ->
                expression.getType(bindingContext)?.let(predicate) ?: false
            }
        }
    return K1ExpressionPostfixTemplateSelector(checkCanBeUsedAsValue, statementsOnly, expressionPredicate = null, predicate = predicate)
}
