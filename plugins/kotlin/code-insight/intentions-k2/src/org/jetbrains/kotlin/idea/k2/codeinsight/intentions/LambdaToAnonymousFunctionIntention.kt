// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaAnonymousFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.idea.base.psi.moveInsideParenthesesAndReplaceWith
import org.jetbrains.kotlin.idea.base.psi.shouldLambdaParameterBeNamed
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.NamedArgumentUtils
import org.jetbrains.kotlin.idea.k2.refactoring.util.LambdaToAnonymousFunctionUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import org.jetbrains.kotlin.utils.exceptions.withPsiEntry

internal class LambdaToAnonymousFunctionIntention :
    KotlinApplicableModCommandAction<KtLambdaExpression, LambdaToAnonymousFunctionIntention.LambdaToFunctionContext>(KtLambdaExpression::class) {

    data class LambdaToFunctionContext(
        val signature: String,
        val lambdaArgumentName: Name?,
    )

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("convert.lambda.expression.to.anonymous.function")

    override fun getPresentation(
        context: ActionContext,
        element: KtLambdaExpression,
    ): Presentation = Presentation.of(
        KotlinBundle.message("convert.to.anonymous.function"),
    )

    override fun KaSession.prepareContext(element: KtLambdaExpression): LambdaToFunctionContext? {
        val declarationSymbol = element.functionLiteral.symbol as? KaAnonymousFunctionSymbol ?: return null
        if (declarationSymbol.valueParameters.any { it.returnType is KaErrorType }) return null

        // anonymous suspend functions are forbidden in Kotlin
        if ((element.functionLiteral.expressionType as? KaFunctionType)?.isSuspend == true) return null

        val signature = LambdaToAnonymousFunctionUtil.prepareFunctionText(element) ?: return null
        val parent = element.functionLiteral.parent
        val lambdaArgumentName = if (parent is KtLambdaArgument && shouldLambdaParameterBeNamed(parent)) {
            NamedArgumentUtils.getStableNameFor(parent)
        } else null
        return LambdaToFunctionContext(signature, lambdaArgumentName)
    }

    override fun getApplicableRanges(element: KtLambdaExpression): List<TextRange> {
        val literal = element.functionLiteral
        val lastElement = literal.arrow ?: literal.lBrace
        return listOf(TextRange(0, lastElement.textRangeInParent.endOffset))
    }

    override fun isApplicableByPsi(element: KtLambdaExpression): Boolean {
        if (element.functionLiteral.valueParameters.any { it.destructuringDeclaration != null}) return false
        val argument = element.getStrictParentOfType<KtValueArgument>()
        val call = argument?.getStrictParentOfType<KtCallElement>()
        return call?.getStrictParentOfType<KtFunction>()?.hasModifier(KtTokens.INLINE_KEYWORD) != true
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtLambdaExpression,
        elementContext: LambdaToFunctionContext,
        updater: ModPsiUpdater,
    ) {
        val resultingFunction = LambdaToAnonymousFunctionUtil.convertLambdaToFunction(element, elementContext.signature) ?: return

        var parent = resultingFunction.parent
        if (parent is KtLabeledExpression) {
            parent = parent.replace(resultingFunction).parent
        }

        val argument = parent as? KtLambdaArgument ?: return

        val replacement = argument.getArgumentExpression()
            ?: errorWithAttachment("no argument expression for $argument") {
                withPsiEntry("lambdaExpression", argument)
            }
        argument.moveInsideParenthesesAndReplaceWith(replacement, elementContext.lambdaArgumentName)
    }
}