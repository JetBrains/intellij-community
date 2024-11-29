// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.filtering.Matcher
import com.intellij.codeInsight.hints.filtering.MatcherConstructor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.idea.codeinsights.impl.base.ArgumentNameCommentInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isExpectedArgumentNameComment
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtExpression
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
            "org.gradle.api.Project.property(propertyName)",
            "org.gradle.api.Project.hasProperty(propertyName)",
            "org.gradle.api.Project.findProperty(propertyName)",
            "org.gradle.api.Project.file(path)",
            "org.gradle.api.Project.uri(path)",
            "jvmArgs(arguments)",
            "org.gradle.kotlin.dsl.DependencyHandlerScope.*(notation)",
            "org.gradle.kotlin.dsl.PluginDependenciesSpecScope.*(*)",
            "org.gradle.kotlin.dsl.*(dependencyNotation)",
            "org.gradle.api.tasks.util.*include(includes)",
            "org.gradle.api.tasks.util.*exclude(excludes)",
            "org.gradle.kotlin.dsl.kotlin(module)",
            "org.gradle.kotlin.dsl.kotlin(module,version)",
            "org.gradle.kotlin.dsl.project(path,configuration)",
            "org.gradle.api.provider.Property.set(value)",
            "org.gradle.api.plugins.ObjectConfigurationAction.plugin(pluginId)",
        ).mapNotNull { MatcherConstructor.createMatcher(it) }

    override fun collectFromElement(
        element: PsiElement,
        sink: InlayTreeSink
    ) {
        val valueArgumentList = element as? KtValueArgumentList ?: return
        val callElement = valueArgumentList.parent as? KtCallElement ?: return
        analyze(valueArgumentList) {
            collectFromParameters(callElement, sink)
        }
    }

    context(KaSession)
    private fun collectFromParameters(
        callElement: KtCallElement,
        sink: InlayTreeSink
    ) {
        val functionCall = callElement.resolveToCall()?.singleFunctionCallOrNull() ?: return
        val functionSymbol: KaFunctionSymbol = functionCall.symbol
        val valueParameters: List<KaValueParameterSymbol> = functionSymbol.valueParameters

        val blackListed = functionSymbol.isBlackListed(valueParameters)
        // TODO: IDEA-347315 has to be fixed
        //sink.whenOptionEnabled(SHOW_BLACKLISTED_PARAMETERS.name) {
        //    if (blackListed) {
        //        functionSymbol.collectFromParameters(valueParameters, arguments, sink)
        //    }
        //}

        if (!blackListed) {
            // TODO: KTIJ-30439 it should respect parameter names when KT-65846 is fixed
            if ((functionSymbol as? KaNamedFunctionSymbol)?.isBuiltinFunctionInvoke != true) {
                collectFromParameters(functionCall.argumentMapping, valueParameters, sink)
            }
        }
    }

    context(KaSession)
    private fun KaFunctionSymbol.isBlackListed(valueParameters: List<KaValueParameterSymbol>): Boolean {
        val blackListed = callableId?.let {
            val callableId = it.asSingleFqName().toString()
            val parameterNames = valueParameters.map { it.name.asString() }
            blackListMatchers.any { it.isMatching(callableId, parameterNames) }
        }
        return blackListed == true
    }

    context(KaSession)
    private fun collectFromParameters(
        args: Map<KtExpression, KaVariableSignature<KaValueParameterSymbol>>,
        valueParameters: List<KaValueParameterSymbol>,
        sink: InlayTreeSink
    ) {
        for (symbol in valueParameters) {
            val symbolName = symbol.name
            val name: Name = symbolName
            val arg = args.filter { (_, signature) -> signature.symbol == symbol }.keys.firstOrNull() ?: continue
            val argument = arg.parent as? KtValueArgument ?: continue

            // do not put inlay hints for a named argument
            if (argument.isNamed()) {
                // it is possible to place named argument in a wrong position when there is some default value
                // after which you have to name rest arguments and no reason to proceed further
                if (argument.getArgumentName()?.asName != name) break
                continue
            }

            if (argument.isArgumentNamed(symbol)) continue

            name.takeUnless(Name::isSpecial)?.asString()?.let { stringName ->
                sink.addPresentation(InlineInlayPosition(arg.startOffset, true), hintFormat = HintFormat.default) {
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

    private fun KtValueArgument.isArgumentNamed(symbol: KaValueParameterSymbol): Boolean {
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