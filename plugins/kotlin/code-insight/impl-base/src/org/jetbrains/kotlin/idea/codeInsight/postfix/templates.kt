// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelector
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatePsiInfo
import com.intellij.codeInsight.template.postfix.templates.SurroundPostfixTemplateBase
import com.intellij.ide.plugins.isKotlinPluginK1Mode
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.expression.KotlinWithIfExpressionSurrounder
import org.jetbrains.kotlin.idea.codeinsight.utils.negate
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

@ApiStatus.Internal
class KtIfExpressionPostfixTemplate(provider: PostfixTemplateProvider) : SurroundPostfixTemplateBase(
    "if", "if (expr)",
    KtPostfixTemplatePsiInfo, createBooleanExpressionSelector(), provider
) {
    override fun getSurrounder(): KotlinWithIfExpressionSurrounder = KotlinWithIfExpressionSurrounder(withElse = false)
}

@ApiStatus.Internal
class KtElseExpressionPostfixTemplate(provider: PostfixTemplateProvider) : SurroundPostfixTemplateBase(
    "else", "if (!expr)",
    KtPostfixTemplatePsiInfo, createBooleanExpressionSelector(), provider
) {
    override fun getSurrounder(): KotlinWithIfExpressionSurrounder = KotlinWithIfExpressionSurrounder(withElse = false)
    override fun getWrappedExpression(expression: PsiElement?): KtExpression = (expression as KtExpression).negate()
}


@ApiStatus.Internal
object KtPostfixTemplatePsiInfo : PostfixTemplatePsiInfo() {
    override fun createExpression(context: PsiElement, prefix: String, suffix: String): KtExpression =
        KtPsiFactory(context.project).createExpression(prefix + context.text + suffix)

    override fun getNegatedExpression(element: PsiElement): KtExpression = (element as KtExpression).negate(true) {
        analyze(it) {
            it.expressionType?.isBooleanType == true
        }
    }
}

@ApiStatus.Internal
fun createBooleanExpressionSelector(): PostfixTemplateExpressionSelector =
    createPostfixExpressionSelector(typePredicate = createBooleanTypePredicate())

@ApiStatus.Internal
fun createBooleanTypePredicate(): (KtExpression, KaType, KaSession) -> Boolean = { _: KtExpression, type: KaType, session: KaSession ->
    with(session) {
        type.isBooleanType
    }
}

@ApiStatus.Internal
fun createNullableExpressionSelector(): PostfixTemplateExpressionSelector =
    createPostfixExpressionSelector { _: KtExpression, type, session ->
        with(session) {
            type.isNullable
        }
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

@ApiStatus.Internal
fun createExpressionSelectorWithComplexFilter(
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
