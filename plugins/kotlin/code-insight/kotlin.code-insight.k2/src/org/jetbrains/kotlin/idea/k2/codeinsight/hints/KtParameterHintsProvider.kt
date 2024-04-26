// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints

import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionNavigationHandler
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionPayload
import com.intellij.codeInsight.hints.filtering.Matcher
import com.intellij.codeInsight.hints.filtering.MatcherConstructor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.idea.codeinsights.impl.base.ArgumentNameCommentInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isExpectedArgumentNameComment
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class KtParameterHintsProvider : AbstractKtInlayHintsProvider() {
    private val blackListMatchers: List<Matcher> =
        listOf(
            "*listOf", "*setOf", "*arrayOf", "*ListOf", "*SetOf", "*ArrayOf", "*assert*(*)", "*mapOf", "*MapOf",
            "kotlin.require*(*)", "kotlin.check*(*)", "*contains*(value)", "*containsKey(key)", "kotlin.lazyOf(value)",
            "*SequenceBuilder.resume(value)", "*SequenceBuilder.yield(value)",

            /* Gradle DSL especially annoying hints */
            "org.gradle.api.Project.hasProperty(propertyName)",
            "org.gradle.api.Project.findProperty(propertyName)",
            "org.gradle.api.Project.file(path)",
            "org.gradle.api.Project.uri(path)",
            "jvmArgs(arguments)",
            "org.gradle.kotlin.dsl.DependencyHandlerScope.*(notation)",
            "org.gradle.kotlin.dsl.*(dependencyNotation)",
            "org.gradle.kotlin.dsl.kotlin(module)",
            "org.gradle.kotlin.dsl.kotlin(module,version)",
            "org.gradle.kotlin.dsl.project(path,configuration)"
        ).mapNotNull { MatcherConstructor.createMatcher(it) }

    override fun collectFromElement(
        element: PsiElement,
        sink: InlayTreeSink
    ) {
        val valueArgumentList = element as? KtValueArgumentList ?: return
        val callElement = valueArgumentList.parent as? KtCallElement ?: return
        analyze(valueArgumentList) {
            collectFromParameters(valueArgumentList, callElement, sink)
        }
    }

    context(KtAnalysisSession)
    private fun collectFromParameters(
        valueArgumentList: KtValueArgumentList,
        callElement: KtCallElement,
        sink: InlayTreeSink
    ) {
        val arguments = valueArgumentList.arguments

        val functionCall = callElement.resolveCall()?.singleFunctionCallOrNull() ?: return
        val functionSymbol: KtFunctionLikeSymbol = functionCall.symbol
        val valueParameters: List<KtValueParameterSymbol> = functionSymbol.valueParameters

        val blackListed = functionSymbol.isBlackListed(valueParameters)
        // TODO: IDEA-347315 has to be fixed
        //sink.whenOptionEnabled(SHOW_BLACKLISTED_PARAMETERS.name) {
        //    if (blackListed) {
        //        functionSymbol.collectFromParameters(valueParameters, arguments, sink)
        //    }
        //}

        if (!blackListed) {
            functionSymbol.collectFromParameters(valueParameters, arguments, sink)
        }
    }

    context(KtAnalysisSession)
    private fun KtFunctionLikeSymbol.isBlackListed(valueParameters: List<KtValueParameterSymbol>): Boolean {
        val blackListed = callableIdIfNonLocal?.let {
            val callableId = it.asSingleFqName().toString()
            val parameterNames = valueParameters.map { it.name.asString() }
            blackListMatchers.any { it.isMatching(callableId, parameterNames) }
        }
        return blackListed == true
    }

    context(KtAnalysisSession)
    private fun KtFunctionLikeSymbol.collectFromParameters(
        valueParameters: List<KtValueParameterSymbol>,
        arguments: MutableList<KtValueArgument>,
        sink: InlayTreeSink
    ) {
        for ((index, symbol) in valueParameters.withIndex()) {
            if (index >= arguments.size) break
            val argument = arguments[index]
            val symbolName = symbol.name
            val name: Name = symbolName
            // do not put inlay hints for a named argument
            if (argument.isNamed()) {
                // it is possible to place named argument in a wrong position when there is some default value
                // after which you have to name rest arguments and no reason to proceed further
                if (argument.getArgumentName()?.asName != name) break
                continue
            }

            if (argument.isArgumentNamed(symbol)) continue

            name.takeUnless(Name::isSpecial)?.asString()?.let { stringName ->
                sink.addPresentation(InlineInlayPosition(argument.startOffset, true), hasBackground = true) {
                    if (symbol.isVararg) text(Typography.ellipsis.toString())
                    text(stringName,
                         symbol.psi?.createSmartPointer()?.let {
                             InlayActionData(
                                 PsiPointerInlayActionPayload(it),
                                 PsiPointerInlayActionNavigationHandler.HANDLER_ID
                             )
                         })
                    text(":")
                }
            }
        }
    }

    private fun KtValueArgument.isArgumentNamed(symbol: KtValueParameterSymbol): Boolean {
        // avoid cases like "`value:` value"
        if (this.text == symbol.name.asString()) return true

        // avoid cases like "/* value = */ value"
        var sibling: PsiElement? = this.prevSibling
        while (sibling != null) {
            when(sibling) {
                is PsiComment -> {
                    val argumentNameCommentInfo = ArgumentNameCommentInfo(symbol)
                    return sibling.isExpectedArgumentNameComment(argumentNameCommentInfo)
                }
                !is PsiWhiteSpace -> break
            }
            sibling = sibling.prevSibling
        }

        return false
    }
}