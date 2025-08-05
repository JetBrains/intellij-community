// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.filtering.Matcher
import com.intellij.codeInsight.hints.filtering.MatcherConstructor
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaAnonymousFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.idea.codeInsight.hints.SHOW_IMPLICIT_RECEIVERS_AND_PARAMS
import org.jetbrains.kotlin.idea.codeInsight.hints.SHOW_RETURN_EXPRESSIONS
import org.jetbrains.kotlin.idea.codeInsight.hints.isFollowedByNewLine
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class KtLambdasHintsProvider : AbstractKtInlayHintsProvider() {

    private val excludeListMatchers: List<Matcher> =
        listOf(
            /* Gradle DSL especially annoying hints */
            "org.gradle.kotlin.dsl.KotlinBuildScript.*(*)",
            "org.gradle.kotlin.dsl.*(*)",
        ).mapNotNull { MatcherConstructor.createMatcher(it) }

    override fun collectFromElement(
        element: PsiElement,
        sink: InlayTreeSink
    ) {
        collectFromLambdaReturnExpression(element, sink)
        collectFromLambdaImplicitParameterReceiver(element, sink)
    }

    @OptIn(ExperimentalContracts::class)
    private fun isLambdaReturnExpression(e: PsiElement): Boolean {
        contract {
            returns(true) implies (e is KtExpression)
        }
        return e is KtExpression && e !is KtFunctionLiteral && !e.isNameReferenceInCall() && e.isLambdaReturnValueHintsApplicable()
    }

    fun collectFromLambdaReturnExpression(
        element: PsiElement,
        sink: InlayTreeSink
    ) {
        if (!isLambdaReturnExpression(element)) return

        val functionLiteral = element.getStrictParentOfType<KtFunctionLiteral>() ?: return
        val lambdaExpression = functionLiteral.getStrictParentOfType<KtLambdaExpression>() ?: return
        val lambdaName = lambdaExpression.getNameOfFunctionThatTakesLambda() ?: "lambda"

        sink.whenOptionEnabled(SHOW_RETURN_EXPRESSIONS.name) {
            val isUsedAsResultOfLambda = analyze(lambdaExpression) {
                @OptIn(KaExperimentalApi::class)
                element.isUsedAsResultOfLambda
            }
            if (!isUsedAsResultOfLambda) return@whenOptionEnabled

            sink.addPresentation(InlineInlayPosition(element.endOffset, true), hintFormat = HintFormat.default) {
                text("^")
                text(lambdaName,
                     InlayActionData(
                         PsiPointerInlayActionPayload(pointer = lambdaExpression.createSmartPointer()),
                         PsiPointerInlayActionNavigationHandler.HANDLER_ID
                     )
                )
            }
        }
    }

    private fun KtLambdaExpression.getNameOfFunctionThatTakesLambda(): String? {
        val lambda = this
        val callExpression = this.getStrictParentOfType<KtCallExpression>() ?: return null
        return if (callExpression.lambdaArguments.any { it.getLambdaExpression() == lambda }) {
            val parent = lambda.parent
            if (parent is KtLabeledExpression) {
                parent.getLabelName()
            } else {
                (callExpression.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
            }
        } else null
    }

    private fun isLambdaImplicitParameterReceiver(e: PsiElement): Boolean =
        e is KtFunctionLiteral && e.parent is KtLambdaExpression && (e.parent as KtLambdaExpression).leftCurlyBrace.isFollowedByNewLine()

    fun collectFromLambdaImplicitParameterReceiver(
        element: PsiElement,
        sink: InlayTreeSink
    ) {
        if (!isLambdaImplicitParameterReceiver(element)) return

        val functionLiteral = element as? KtFunctionLiteral ?: return
        val lambdaExpression = functionLiteral.parent as? KtLambdaExpression ?: return

        val lbrace = lambdaExpression.leftCurlyBrace
        if (!lbrace.isFollowedByNewLine()) return

        sink.whenOptionEnabled(SHOW_IMPLICIT_RECEIVERS_AND_PARAMS.name) {
            analyze(functionLiteral) {
                val anonymousFunctionSymbol = functionLiteral.symbol
                printContextParameters(lambdaExpression, anonymousFunctionSymbol, sink)
                printReceiverParameter(lambdaExpression, anonymousFunctionSymbol, sink)
                printImplicitIt(lambdaExpression, anonymousFunctionSymbol, sink)
            }
        }
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.printContextParameters(
        lambdaExpression: KtLambdaExpression,
        anonymousFunctionSymbol: KaAnonymousFunctionSymbol,
        sink: InlayTreeSink,
    ) {
        anonymousFunctionSymbol.contextParameters.ifNotEmpty {
            val contextParameters = this
            sink.addPresentation(
                position = InlineInlayPosition(lambdaExpression.leftCurlyBrace.textRange.endOffset, true),
                hintFormat = HintFormat.default,
            ) {
                text("context(")
                for (contextParameter in contextParameters.dropLast(1)) {
                    printKtType(contextParameter.returnType)
                    text(", ")
                }
                printKtType(contextParameters.last().returnType)
                text(")")
            }
        }
    }

    private fun KaSession.printReceiverParameter(
        lambdaExpression: KtLambdaExpression,
        anonymousFunctionSymbol: KaAnonymousFunctionSymbol,
        sink: InlayTreeSink,
    ) {
        anonymousFunctionSymbol.receiverParameter?.let { receiverSymbol ->
            val skipped = lambdaExpression.functionLiteral.getParentOfType<KtCallExpression>(false, KtBlockExpression::class.java)
                ?.let { callExpression ->
                    val functionCall = callExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return@let true
                    val functionSymbol = functionCall.symbol
                    functionSymbol.isExcludeListed(excludeListMatchers)
                }

            if (skipped != true) {
                sink.addPresentation(
                    position = InlineInlayPosition(lambdaExpression.leftCurlyBrace.textRange.endOffset, true),
                    hintFormat = HintFormat.default,
                ) {
                    text("this: ")
                    printKtType(receiverSymbol.returnType)
                }
            }
        }
    }

    private fun KaSession.printImplicitIt(
        lambdaExpression: KtLambdaExpression,
        anonymousFunctionSymbol: KaAnonymousFunctionSymbol,
        sink: InlayTreeSink,
    ) {
        anonymousFunctionSymbol.valueParameters.singleOrNull()?.let { singleParameterSymbol ->
            val type = singleParameterSymbol.takeIf { it.isImplicitLambdaParameter }
                ?.returnType?.takeUnless { it.isUnitType } ?: return@let
            sink.addPresentation(
                position = InlineInlayPosition(lambdaExpression.leftCurlyBrace.textRange.endOffset, true),
                hintFormat = HintFormat.default
            ) {
                text("it: ")
                printKtType(type)
            }
        }
    }
}

context(_: KaSession)
internal fun KaFunctionSymbol.isExcludeListed(excludeListMatchers: List<Matcher>): Boolean {
    val callableFqName = callableId?.asSingleFqName()?.asString() ?: return false
    val parameterNames = valueParameters.map { it.name.asString() }
    return excludeListMatchers.any { it.isMatching(callableFqName, parameterNames) }
}

