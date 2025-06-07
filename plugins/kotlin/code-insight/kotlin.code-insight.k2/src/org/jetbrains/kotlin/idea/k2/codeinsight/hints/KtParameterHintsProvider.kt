// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.filtering.Matcher
import com.intellij.codeInsight.hints.filtering.MatcherConstructor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.idea.codeInsight.hints.SHOW_COMPILED_PARAMETERS
import org.jetbrains.kotlin.idea.codeInsight.hints.SHOW_EXCLUDED_PARAMETERS
import org.jetbrains.kotlin.idea.codeinsights.impl.base.ArgumentNameCommentInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isExpectedArgumentNameComment
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

class KtParameterHintsProvider : AbstractKtInlayHintsProvider() {
    private val excludeListMatchers: List<Matcher> by lazy {
        setOf(
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

            /* copied from com.intellij.codeInsight.hints.JavaInlayParameterHintsProvider.defaultBlackList */
            // TODO: IJPL-166464 should provide API like InlayParameterHintsProvider#getBlackListDependencyLanguage

            "(begin*, end*)",
            "(start*, end*)",
            "(first*, last*)",
            "(first*, second*)",
            "(from*, to*)",
            "(min*, max*)",
            "(key, value)",
            "(format, arg*)",
            "(message)",
            "(message, error)",

            "*Exception",

            "*.set*(*)",
            "*.add(*)",
            "*.set(*,*)",
            "*.get(*)",
            "*.create(*)",
            "*.getProperty(*)",
            "*.setProperty(*,*)",
            "*.print(*)",
            "*.println(*)",
            "*.append(*)",
            "*.charAt(*)",
            "*.indexOf(*)",
            "*.contains(*)",
            "*.startsWith(*)",
            "*.endsWith(*)",
            "*.equals(*)",
            "*.equal(*)",
            "*.compareTo(*)",
            "*.compare(*,*)",

            "java.lang.Math.*",
            "org.slf4j.Logger.*",

            "*.singleton(*)",
            "*.singletonList(*)",

            "*.Set.of",
            "*.ImmutableList.of",
            "*.ImmutableMultiset.of",
            "*.ImmutableSortedMultiset.of",
            "*.ImmutableSortedSet.of",
            "*.Arrays.asList"

        ).mapNotNull { MatcherConstructor.createMatcher(it) }
    }

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
    @OptIn(KaExperimentalApi::class)
    private fun collectFromParameters(
        callElement: KtCallElement,
        sink: InlayTreeSink
    ) {
        val functionCall = callElement.resolveToCall()?.singleFunctionCallOrNull() ?: return
        val functionSymbol: KaFunctionSymbol = functionCall.symbol
        val valueParameters: List<KaValueParameterSymbol> = functionSymbol.valueParameters

        val excludeListed = functionSymbol.isExcludeListed(excludeListMatchers)

        sink.whenOptionEnabled(SHOW_EXCLUDED_PARAMETERS.name) {
            if (excludeListed) {
                val valueParametersWithNames =
                    calculateValueParametersWithNames(functionSymbol, callElement, valueParameters) ?: return@whenOptionEnabled

                collectFromParameters(functionCall.argumentMapping, valueParametersWithNames, sink)
            }
        }

        if (excludeListed) return

        val valueParametersWithNames = calculateValueParametersWithNames(functionSymbol, callElement, valueParameters) ?: return

        val compiledSource = valueParametersWithNames.any { pair ->
            val psi = pair.first.takeIf { it.origin == KaSymbolOrigin.JAVA_LIBRARY }?.psi ?: return@any false
            // not very nice way to detect true origin of the parameter
            psi == psi.navigationElement
        }

        if (compiledSource) {
            sink.whenOptionEnabled(SHOW_COMPILED_PARAMETERS.name) {
                collectFromParameters(functionCall.argumentMapping, valueParametersWithNames, sink)
            }
        } else {
            collectFromParameters(functionCall.argumentMapping, valueParametersWithNames, sink)
        }
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.calculateValueParametersWithNames(
        functionSymbol: KaFunctionSymbol,
        callElement: KtCallElement,
        valueParameters: List<KaValueParameterSymbol>
    ): List<Pair<KaValueParameterSymbol, Name?>>? {
        val valueParametersWithNames =
            if ((functionSymbol as? KaNamedFunctionSymbol)?.isBuiltinFunctionInvoke == true) {
                val expressionType = callElement.calleeExpression?.expressionType as? KaFunctionType
                    ?: callElement.siblings(false).firstIsInstanceOrNull<KtNameReferenceExpression>()?.expressionType as? KaFunctionType
                    ?: return null

                /**
                 * Context receivers and receivers of extension functions in function references are not included in [KaFunctionType.parameters],
                 * even though they are required as arguments during the invocation.
                 * However, [valueParameters] include all the parameters required for the invocation, including all the receivers.
                 * That's why we have to calculate the number of receiver parameters we should skip from [valueParameters] to properly map them to names from [KaFunctionType.parameters].
                 */
                val receiverNumber = expressionType.contextReceivers.size + if (expressionType.hasReceiver) 1 else 0
                expressionType.parameters.mapIndexed { index, parameter ->
                    val name = parameter.name
                    val parameterSymbol = valueParameters[receiverNumber + index]
                    parameterSymbol to name
                }
            } else {
                valueParameters.map { it to it.name }
            }
        return valueParametersWithNames
    }

    context(KaSession)
    private fun collectFromParameters(
        args: Map<KtExpression, KaVariableSignature<KaValueParameterSymbol>>,
        valueParametersWithNames: List<Pair<KaValueParameterSymbol, Name?>>,
        sink: InlayTreeSink
    ) {
        for ((symbol, name) in valueParametersWithNames) {
            if (name == null) continue

            val arg = args.filter { (_, signature) -> signature.symbol == symbol }.keys.firstOrNull() ?: continue
            val argument = arg.parent as? KtValueArgument

            // do not show inlay hint for lambda parameter out of parenthesis
            if (argument?.parent !is KtValueArgumentList) continue

            // do not put inlay hints for a named argument
            if (argument.isNamed()) {
                // it is possible to place named argument in a wrong position when there is some default value
                // after which you have to name rest arguments and no reason to proceed further
                if (argument.getArgumentName()?.asName != name) break
                continue
            }

            if (argument.isArgumentNamed(symbol)) continue

            name.takeUnless(Name::isSpecial)?.asString()?.let { stringName ->
                val element = arg.getParentOfType<KtValueArgument>(true, KtValueArgumentList::class.java) ?: arg
                sink.addPresentation(InlineInlayPosition(element.startOffset, true), hintFormat = HintFormat.default) {
                    if (symbol.isVararg) text(Typography.ellipsis.toString())
                    text(stringName,
                         symbol.psi?.createSmartPointer()?.let {
                             InlayActionData(
                                 PsiPointerInlayActionPayload(it),
                                 PsiPointerInlayActionNavigationHandler.HANDLER_ID
                             )
                         })
                    text(" =")
                }
            }
        }
    }

    private fun KtValueArgument.isArgumentNamed(symbol: KaValueParameterSymbol): Boolean {
        // avoid cases like "`value =` value"
        val argumentText = this.text
        val symbolName = symbol.name.asString()
        if (argumentText == symbolName) return true

        if (symbolName.length > 1) {
            val name = symbolName.lowercase()
            val lowercase = argumentText.lowercase()
            // avoid cases like "`type = Type(...)`" and "`value =` myValue"
            if (lowercase.startsWith(name) || lowercase.endsWith(name)) return true
        }

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

context(KaSession)
internal fun KaFunctionSymbol.isExcludeListed(excludeListMatchers: List<Matcher>): Boolean {
    val callableFqName = callableId?.asSingleFqName()?.asString() ?: return false
    val parameterNames = valueParameters.map { it.name.asString() }
    return excludeListMatchers.any { it.isMatching(callableFqName, parameterNames) }
}