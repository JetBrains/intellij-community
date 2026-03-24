// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.evaluate
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtBreakExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.idea.codeinsight.utils.findRelevantLoopForExpression
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ForLoopUtils.computeContinuesToReplace
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ForLoopUtils.replaceContinuesWithReturn
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ForLoopUtils.ContinuesToReplace
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.PsiChildRange

private val REPEAT_KEYWORD: Name = Name.identifier("repeat")
private val RANGE_UNTIL_KEYWORD: Name = Name.identifier("rangeUntil")
private val VALID_RANGE_CALLABLE_IDS: Set<CallableId> = setOf(
    // Int.rangeUntil, Long.rangeUntil, etc. (the ..< operator)
    CallableId(StandardClassIds.Int, RANGE_UNTIL_KEYWORD),
    CallableId(StandardClassIds.Long, RANGE_UNTIL_KEYWORD),
    CallableId(StandardClassIds.Short, RANGE_UNTIL_KEYWORD),
    CallableId(StandardClassIds.Byte, RANGE_UNTIL_KEYWORD),
    CallableId(StandardClassIds.Char, RANGE_UNTIL_KEYWORD),
    CallableId(StandardClassIds.BASE_KOTLIN_PACKAGE.child(Name.identifier("ranges")), Name.identifier("until")),
)
private val IMPLICIT_LAMBDA: String = StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier

internal class ReplaceForLoopWithRepeatIntention :
    KotlinApplicableModCommandAction<KtForExpression, ReplaceForLoopWithRepeatIntention.Context>(KtForExpression::class) {

    data class Context(
        val times: String,
        val continuesToReplace: ContinuesToReplace,
        val loopParameterName: String?
    )

    override fun isApplicableByPsi(element: KtForExpression): Boolean {
        val loopRange = element.loopRange as? KtBinaryExpression ?: return false
        val body = element.body ?: return false

        if (loopRange.left?.text != "0") return false

        return !body.collectDescendantsOfType<KtBreakExpression>().any { findRelevantLoopForExpression(it) == element }
    }

    override fun KaSession.prepareContext(element: KtForExpression): Context? {
        val loopRange = element.loopRange as? KtBinaryExpression ?: return null

        if (!loopRange.isValidRangeByAnalysis()) return null

        val times = loopRange.right?.text ?: return null
        val continuesToReplace = element.computeContinuesToReplace()

        val loopParameterName = element.loopParameter?.name.takeIf { isParameterUsedInBody(element) }

        return Context(times, continuesToReplace, loopParameterName)
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtForExpression,
        elementContext: Context,
        updater: ModPsiUpdater
    ) {
        val commentSaver = CommentSaver(element)
        val body = element.body ?: return
        val factory = KtPsiFactory(element.project)

        val needLabel = replaceContinuesWithReturn(elementContext.continuesToReplace, REPEAT_KEYWORD.asString(), factory)
        val labelPrefix = if (needLabel) "${REPEAT_KEYWORD}@ " else ""


        val paramName = elementContext.loopParameterName
        val paramPart = if (paramName != null && paramName != IMPLICIT_LAMBDA) "$paramName -> " else ""

        val repeat = when (body) {
            is KtBlockExpression -> {
                val bodyContent = PsiChildRange(body.statements.firstOrNull(), body.statements.lastOrNull())
                factory.createExpressionByPattern(
                    "${labelPrefix}repeat($0) { $paramPart\n$1\n}",
                    elementContext.times,
                    bodyContent
                )
            }
            else -> {
                factory.createExpressionByPattern(
                    "${labelPrefix}repeat($0) { $paramPart$1 }",
                    elementContext.times,
                    body
                )
            }
        }

        val result = element.replace(repeat)
        commentSaver.restore(result)
    }

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.for.loop.with.repeat")

    private fun isParameterUsedInBody(forExpression: KtForExpression): Boolean {
        val loopParameter = forExpression.loopParameter ?: return false
        val body = forExpression.body ?: return false

        return body.collectDescendantsOfType<KtNameReferenceExpression>().any {
            it.mainReference.resolve() == loopParameter
        }
    }

    context(_: KaSession)
    private fun KtBinaryExpression.isValidRangeByAnalysis(): Boolean {
        val left = left ?: return false
        val leftValue = left.evaluate()?.value
        if (leftValue != 0 && leftValue != 0L) return false

        val call = resolveToCall()?.singleFunctionCallOrNull() ?: return false
        val callableId = call.symbol.callableId

        return callableId in VALID_RANGE_CALLABLE_IDS
    }

}
