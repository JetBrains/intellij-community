// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.collections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.number
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.fullyExpandedType
import org.jetbrains.kotlin.analysis.api.components.isClassType
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.buildExpression
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi.qualifiedExpressionVisitor

class ConvertCallChainIntoSequenceInspection : AbstractKotlinInspection() {
    private val defaultCallChainLength = 5
    private var callChainLength = defaultCallChainLength

    // Used for serialization
    @Suppress("unused")
    var callChainLengthText: String = callChainLength.toString()
        get() { return callChainLength.toString() }
        set(value) {
            field = value
            callChainLength = value.toIntOrNull() ?: defaultCallChainLength
        }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        qualifiedExpressionVisitor(fun(expression) {
            val (qualified, firstCall, callChainLength) = expression.findCallChain() ?: return
            val rangeInElement = firstCall.calleeExpression?.textRange?.shiftRight(-qualified.startOffset) ?: return
            val highlightType = if (callChainLength >= this.callChainLength)
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            else
                ProblemHighlightType.INFORMATION

            holder.registerProblemWithoutOfflineInformation(
                qualified,
                KotlinBundle.message("call.chain.on.collection.could.be.converted.into.sequence.to.improve.performance"),
                isOnTheFly,
                highlightType,
                rangeInElement,
                ConvertCallChainIntoSequenceFix()
            )
        })

    override fun getOptionsPane(): OptPane =
        pane(number("callChainLength", KotlinBundle.message("call.chain.length.to.transform"), 1, 100))
}

private class ConvertCallChainIntoSequenceFix : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName(): String = KotlinBundle.message("convert.call.chain.into.sequence.fix.text")

    override fun applyFix(
        project: Project,
        element: PsiElement,
        updater: ModPsiUpdater
    ) {
        val expression = element as? KtQualifiedExpression ?: return
        val calls = collectCallExpression(expression).reversed()
        val firstCall = calls.firstOrNull() ?: return
        val lastCall = calls.lastOrNull() ?: return
        val first = firstCall.getQualifiedExpressionForSelector() ?: firstCall
        val last = lastCall.getQualifiedExpressionForSelector() ?: return
        val endsWithTermination = lastCall.calleeText() in terminations ||
                (lastCall.parent.parent as? KtQualifiedExpression)?.callExpression?.calleeText() in terminations

        val psiFactory = KtPsiFactory(project)
        val dot = buildString {
            if (first is KtQualifiedExpression
                && first.receiverExpression.siblings().filterIsInstance<PsiWhiteSpace>().any { it.textContains('\n') }
            ) append("\n")
            if (first is KtSafeQualifiedExpression) append("?")
            append(".")
        }

        val firstCommentSaver = CommentSaver(first)
        val firstReplaced = first.replaced(
            psiFactory.buildExpression {
                if (first is KtQualifiedExpression) {
                    appendExpression(first.receiverExpression)
                    appendFixedText(dot)
                }
                appendExpression(psiFactory.createExpression("asSequence()"))
                appendFixedText(dot)
                appendExpression(firstCall)
            }
        )
        firstCommentSaver.restore(firstReplaced)

        if (!endsWithTermination) {
            val lastCommentSaver = CommentSaver(last)
            val lastReplaced = last.replace(
                psiFactory.buildExpression {
                    appendExpression(last)
                    appendFixedText(dot)
                    appendExpression(psiFactory.createExpression("toList()"))
                }
            )
            lastCommentSaver.restore(lastReplaced)
        }
    }
}

private data class CallChain(
    val qualified: KtQualifiedExpression,
    val firstCall: KtCallExpression,
    val callChainLength: Int
)

private fun KtQualifiedExpression.findCallChain(): CallChain? {
    if (parent is KtQualifiedExpression) return null

    val calls = collectCallExpression(this).ifEmpty { return null }
    val lastCall = calls.last()
    val firstCall = calls.first()
    val qualified =
        firstCall.getQualifiedExpressionForSelector() ?:
        firstCall.getQualifiedExpressionForReceiver() ?:
        return null
    return CallChain(qualified, lastCall, calls.size)
}

private fun collectCallExpression(expression: KtQualifiedExpression): List<KtCallExpression> {
    val calls = mutableListOf<KtCallExpression>()

    fun KaSession.collect(qualified: KtQualifiedExpression) {
        val call = qualified.callExpression ?: return
        calls.add(call)
        val receiver = qualified.receiverExpression
        if (receiver is KtCallExpression) {
            val partiallyAppliedSymbol =
                receiver.resolveToCall()?.singleCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol
            val implicitReceiverValue =
                (partiallyAppliedSymbol?.extensionReceiver ?: partiallyAppliedSymbol?.dispatchReceiver) as? KaImplicitReceiverValue
            val hasImplicitReceiver = implicitReceiverValue != null
            if (hasImplicitReceiver) {
                calls.add(receiver)
                return
            }

        }
        if (receiver is KtQualifiedExpression) collect(receiver)
    }

    return analyze(expression) {
        collect(expression)

        if (calls.size < 2) return emptyList()

        val targetCalls = calls
            .asSequence()
            .map { call ->
                val partiallySymbol = transformationAndTerminations[call.calleeText()]?.let { fqName ->
                    val partiallySymbol = call.resolveToCall()?.singleCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol
                    val symbolCallableId = partiallySymbol?.symbol?.callableId?.asSingleFqName()
                    partiallySymbol?.takeIf { symbolCallableId == fqName }
                }
                call to partiallySymbol
            }
            .dropWhile { (call, partiallySymbol) ->
                if (partiallySymbol == null) return@dropWhile true
                val receiver = partiallySymbol.extensionReceiver ?: partiallySymbol.dispatchReceiver
                val receiverType = receiver?.type ?: return@dropWhile true
                val returnType = partiallySymbol.symbol.returnType
                !(returnType.isIterable || receiverType.isIterable) || call.isRedundantTermination(receiverType)
            }
            .takeWhile { (call, partiallySymbol) ->
                partiallySymbol != null && !call.hasReturn()
            }
            .toList()
            .dropLastWhile { (call, partiallySymbol) ->
                call.calleeText() in terminations && !partiallySymbol?.symbol?.returnType.isIterable
            }

        if (targetCalls.size < 2) {
            emptyList()
        } else {
            targetCalls.map { (call, _) -> call }
        }
    }
}

context(_: KaSession)
private fun KtCallExpression.isRedundantTermination(receiverType: KaType?): Boolean {
    if (receiverType == null || getQualifiedExpressionForSelector()?.parent is KtQualifiedExpression) return false
    return when (calleeExpression?.text) {
        "toList" -> receiverType.isList
        "toSet" -> receiverType.isSet
        else -> false
    }
}

private fun KtCallExpression.hasReturn(): Boolean = valueArguments.any { arg ->
    arg.anyDescendantOfType<KtReturnExpression> { it.labelQualifier == null }
}

private fun KtCallExpression.calleeText(): String? = calleeExpression?.text

context(_: KaSession)
val KaType?.isIterable: Boolean
    @ApiStatus.Internal
    get() = this is KaClassType && (classId == StandardClassIds.Iterable || isSubtypeOf(StandardClassIds.Iterable))

context(_: KaSession)
val KaType?.isList: Boolean
    @ApiStatus.Internal
    get() = this?.fullyExpandedType?.isClassType(StandardClassIds.List) ?: false

context(_: KaSession)
val KaType?.isSet: Boolean
    @ApiStatus.Internal
        get() = this?.fullyExpandedType?.isClassType(StandardClassIds.Set) ?: false

private val terminations: Set<String> =
    StandardKotlinNames.Collections.terminations.mapTo(HashSet()) { it.shortName().asString() }

private val transformationAndTerminations: Map<String, FqName> =
    (StandardKotlinNames.Collections.transformations + StandardKotlinNames.Collections.terminations).associateBy { it.shortName().asString() }