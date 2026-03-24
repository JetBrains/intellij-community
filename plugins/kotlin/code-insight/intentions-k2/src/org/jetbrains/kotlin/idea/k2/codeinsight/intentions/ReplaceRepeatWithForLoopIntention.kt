// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ForLoopUtils.computeReturnsToReplace
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ForLoopUtils.suggestLoopName
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ForLoopUtils.ReturnsToReplace
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ForLoopUtils.replaceImplicitItReferences
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ForLoopUtils.replaceReturnsWithContinue
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ForLoopUtils.suggestLoopVariableName
import org.jetbrains.kotlin.psi.KtExpression

private val REPEAT_KEYWORD: Name = Name.identifier("repeat")

private val REPEAT_KEYWORD_CALLABLE_IDS: CallableId = CallableId(StandardClassIds.BASE_KOTLIN_PACKAGE, REPEAT_KEYWORD)

internal class ReplaceRepeatWithForLoopIntention :
    KotlinApplicableModCommandAction<KtCallExpression, ReturnsToReplace>(KtCallExpression::class) {

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        val referencedName = element.getCallNameExpression()?.getReferencedName() ?: return false
        if (referencedName != REPEAT_KEYWORD.asString()) return false

        if (element.getQualifiedExpressionForSelector() != null) return false

        if (element.valueArguments.size != 2) return false

        val lambdaArgument = element.getLambdaArgument() ?: return false
        if (lambdaArgument.valueParameters.size > 1) return false
        if (lambdaArgument.bodyExpression == null) return false

        val paramName = lambdaArgument.valueParameters.singleOrNull()?.name
        return paramName != "_"
    }

    override fun KaSession.prepareContext(element: KtCallExpression): ReturnsToReplace? {
        val symbol = element.calleeExpression?.mainReference?.resolveToSymbol() as? KaNamedFunctionSymbol ?: return null
        if (symbol.callableId != REPEAT_KEYWORD_CALLABLE_IDS) return null
        val lambda = element.getLambdaArgument() ?: return null
        return lambda.computeReturnsToReplace()
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtCallExpression,
        elementContext: ReturnsToReplace,
        updater: ModPsiUpdater
    ) {
        val commentSaver = CommentSaver(element)
        val factory = KtPsiFactory(element.project)

        val times = element.getTimesArgument() ?: return
        val lambda = element.getLambdaArgument() ?: return
        val body = lambda.bodyExpression ?: return
        val explicitParam = lambda.valueParameters.singleOrNull()
        // Use an explicit parameter if it exists and is not "it", otherwise suggest a new name
        val loopVarName = if (explicitParam != null && explicitParam.name != StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier) {
            explicitParam
        } else {
            analyze(lambda) { suggestLoopVariableName(lambda, factory) }
        }

        analyze(lambda) {
            replaceImplicitItReferences(lambda, loopVarName, factory)
        }
        val loopLabelName = suggestLoopName(lambda)
        val needLoopLabel = replaceReturnsWithContinue(elementContext, lambda, loopLabelName, factory)

        val loopLabel = if (needLoopLabel) "$loopLabelName@ " else ""
        val loop = factory.createExpressionByPattern(
            "${loopLabel}for($0 in 0..< $1){ $2 }",
            loopVarName,
            times,
            body.allChildren
        )

        val result = element.replace(loop)
        commentSaver.restore(result)
    }

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.repeat.with.for.loop")

    private fun KtCallExpression.getLambdaArgument(): KtLambdaExpression? =
        valueArguments.getOrNull(1)?.getArgumentExpression() as? KtLambdaExpression

    private fun KtCallExpression.getTimesArgument(): KtExpression? =
        valueArguments.getOrNull(0)?.getArgumentExpression()
}
