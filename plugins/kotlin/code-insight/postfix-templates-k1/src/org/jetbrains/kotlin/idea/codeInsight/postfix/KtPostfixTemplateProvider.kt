// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.*
import com.intellij.ide.plugins.isKotlinPluginK1Mode
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.intentions.negate
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceVariable.K1IntroduceVariableHandler
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.types.KotlinType

// K1 PostfixTemplateProvider
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

internal object KtPostfixTemplatePsiInfo : PostfixTemplatePsiInfo() {
    override fun createExpression(context: PsiElement, prefix: String, suffix: String): KtExpression =
        KtPsiFactory(context.project).createExpression(prefix + context.text + suffix)

    override fun getNegatedExpression(element: PsiElement): KtExpression = (element as KtExpression).negate()
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

@ApiStatus.Internal
fun convertToTypePredicate(
    typePredicate: ((KtExpression, KaType, KaSession) -> Boolean)? = null
): ((KtExpression, KaSession) -> Boolean)? =
    typePredicate?.let { predicate ->
        f@ { expression, session ->
            try {
                with(session) {
                    expression.expressionType?.let { predicate.invoke(expression, it, session) } ?: false
                }
            } catch (e: Exception) {
                if (e is ControlFlowException) throw e

                // K1 Repl fails due to inconsistency in module info specification
                if (isKotlinPluginK1Mode() && expression.containingKtFile.isScript()) return@f false

                throw e
            }
        }
    }

@ApiStatus.Internal
fun createPostfixExpressionSelector(
    checkCanBeUsedAsValue: Boolean = true,
    statementsOnly: Boolean = false,
    typePredicate: ((KtExpression, KaType, KaSession) -> Boolean)? = null
): PostfixTemplateExpressionSelector {

    return createExpressionSelectorWithComplexFilter(
        checkCanBeUsedAsValue,
        statementsOnly,
        predicate = convertToTypePredicate(typePredicate)
    )
}

internal fun createExpressionSelectorWithComplexFilter(
    // Do not suggest expressions like 'val a = 1'/'for ...'
    checkCanBeUsedAsValue: Boolean = true,
    statementsOnly: Boolean = false,
    expressionPredicate: ((KtExpression)-> Boolean)? = null,
    predicate: ((KtExpression, KaSession) -> Boolean)? = null
): PostfixTemplateExpressionSelector =
    KtExpressionPostfixTemplateSelector(checkCanBeUsedAsValue, statementsOnly, expressionPredicate, predicate)

private class KtExpressionPostfixTemplateSelector(
    checkCanBeUsedAsValue: Boolean,
    statementsOnly: Boolean,
    expressionPredicate: ((KtExpression)-> Boolean)?,
    predicate: ((KtExpression, KaSession) -> Boolean)?
) : AbstractKtExpressionPostfixTemplateSelector<KaSession>(checkCanBeUsedAsValue, statementsOnly, expressionPredicate, predicate) {

    @OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
    override fun applyPredicate(element: KtExpression, predicate: ((KtExpression, KaSession) -> Boolean)?): Boolean? {
        return allowAnalysisOnEdt {
            allowAnalysisFromWriteAction {
                analyze(element) {
                    predicate?.invoke(element, this)
                }
            }
        }
    }
}

internal fun createBooleanExpressionSelector() =
    createPostfixExpressionSelector(typePredicate = createBooleanTypePredicate())

internal fun createNullableExpressionSelector() =
    createPostfixExpressionSelector { _: KtExpression, type, session ->
        with(session) {
            type.isNullable
        }
    }

internal fun createBooleanTypePredicate() = { _: KtExpression, type: KaType, session: KaSession ->
        with(session) {
            type.isBooleanType
        }
    }
