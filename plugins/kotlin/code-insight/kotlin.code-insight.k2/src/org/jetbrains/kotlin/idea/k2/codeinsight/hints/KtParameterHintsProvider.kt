// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.hints.ExcludeListDialog
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.ParameterNameHintsSuppressor
import com.intellij.codeInsight.hints.declarative.CollapseState
import com.intellij.codeInsight.hints.declarative.CollapsiblePresentationTreeBuilder
import com.intellij.codeInsight.hints.declarative.HintColorKind
import com.intellij.codeInsight.hints.declarative.HintFontSize
import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.InlayActionPayload
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayPayload
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionNavigationHandler
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionPayload
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.codeInsight.hints.parameters.AbstractDeclarativeParameterHintsCustomSettingsProvider
import com.intellij.codeInsight.hints.parameters.ParameterHintsExcludeListConfigProvider
import com.intellij.codeInsight.hints.parameters.ParameterHintsExcludeListService
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.NlsActions
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.endOffset
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaContextParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.contextParameters
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.codeInsight.hints.SHOW_COMPILED_PARAMETERS
import org.jetbrains.kotlin.idea.codeInsight.hints.SHOW_CONTEXT_PARAMETERS
import org.jetbrains.kotlin.idea.codeInsight.hints.SHOW_EXCLUDED_PARAMETERS
import org.jetbrains.kotlin.idea.codeinsights.impl.base.ArgumentNameCommentInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isExpectedArgumentNameComment
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse

class KtParameterHintsProvider : AbstractKtInlayHintsProvider() {
    override fun collectFromElement(
        element: PsiElement,
        sink: InlayTreeSink
    ) {
        val callElement = element as? KtCallElement ?: return
        analyze(callElement) {
            collectFromParameters(callElement, sink)
        }
    }

    context(session: KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun collectFromParameters(
        callElement: KtCallElement,
        sink: InlayTreeSink
    ) {
        val functionCall: KaFunctionCall<*> = callElement.resolveToCall()?.singleFunctionCallOrNull() ?: return
        val functionSymbol: KaFunctionSymbol = functionCall.symbol
        val valueParameters: List<KaValueParameterSymbol> = functionSymbol.valueParameters

        val excludeListed: Boolean
        val contextMenuPayloads: List<InlayPayload>?
        val callableFqName = functionSymbol.callableId?.asSingleFqName()?.asString()
            ?: (functionSymbol as? KaConstructorSymbol)?.containingClassId?.asSingleFqName()?.asString()
        if (callableFqName != null) {
            val parameterNames = valueParameters.map { it.name.asString() }
            excludeListed = isExcludeListed(callableFqName, parameterNames)
            contextMenuPayloads = excludeListed.ifFalse {
                createAddToExcludeListActionPayloads(
                    callableFqName, callableFqName + "(" + parameterNames.joinToString(", ") + ")", KotlinLanguage.INSTANCE
                )
            }
        } else {
            excludeListed = false
            contextMenuPayloads = null
        }

        sink.whenOptionEnabled(SHOW_EXCLUDED_PARAMETERS.name) {
            if (excludeListed) {
                val valueParametersWithNames =
                    session.calculateValueParametersWithNames(functionSymbol, callElement, valueParameters) ?: return@whenOptionEnabled

                collectFromParameters(callElement, functionCall, valueParametersWithNames, contextMenuPayloads, sink)
            }
        }

        if (excludeListed) return

        val valueParametersWithNames = session.calculateValueParametersWithNames(functionSymbol, callElement, valueParameters) ?: return

        val compiledSource = valueParametersWithNames.any { pair ->
            val psi = pair.first.takeIf { it.origin == KaSymbolOrigin.JAVA_LIBRARY }?.psi ?: return@any false
            // not very nice way to detect true origin of the parameter
            psi == psi.navigationElement
        }

        if (compiledSource) {
            sink.whenOptionEnabled(SHOW_COMPILED_PARAMETERS.name) {
                collectFromParameters(callElement, functionCall, valueParametersWithNames, contextMenuPayloads, sink)
            }
        } else {
            collectFromParameters(callElement, functionCall, valueParametersWithNames, contextMenuPayloads, sink)
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

    @OptIn(KaExperimentalApi::class)
    context(_: KaSession)
    private fun collectFromParameters(
        callElement: KtCallElement,
        functionCall: KaFunctionCall<*>,
        valueParametersWithNames: List<Pair<KaValueParameterSymbol, Name?>>,
        contextMenuPayloads: List<InlayPayload>?,
        sink: InlayTreeSink
    ) {
        val functionSymbol: KaFunctionSymbol = functionCall.symbol
        val contextParameters = functionSymbol.contextParameters
        val contextArguments: List<KaReceiverValue> = functionCall.contextArguments

        val contextParameterPairs =
            contextParameters.zip(contextArguments).filter { !it.first.name.isSpecial }

        sink.whenOptionEnabled(SHOW_CONTEXT_PARAMETERS.name) {
            collectContextParameters(callElement, sink, contextMenuPayloads, contextParameterPairs, valueParametersWithNames)
        }

        val args: Map<KtExpression, KaVariableSignature<KaValueParameterSymbol>> = functionCall.argumentMapping
        val referencedName = (callElement.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
        for (indexedValue in valueParametersWithNames.withIndex()) {
            val (symbol, name) = indexedValue.value
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

            if (argument.isArgumentNamed(symbol)) {
                continue
            }

            // do not show parameter name when it is similar to caller name, e.g. `Call("call =" )`
            if (indexedValue.index == 0
                && referencedName != null
                && isSimilarName(symbol.name.asString(), referencedName)) {
                continue
            }

            name.takeUnless(Name::isSpecial)?.asString()?.let { stringName ->
                val element = arg.getParentOfType<KtValueArgument>(true, KtValueArgumentList::class.java) ?: arg
                if (ParameterNameHintsSuppressor.isSuppressedFor(element.containingKtFile, InlayInfo("", element.startOffset))) continue
                sink.addPresentation(
                    InlineInlayPosition(element.startOffset, true),
                    payloads = contextMenuPayloads,
                    hintFormat = PARAMETER_HINT_FORMAT
                ) {
                    if (symbol.isVararg) text(Typography.ellipsis.toString())
                    text(stringName, symbol.asNavigatablePsiLoad())
                    text(" =")
                }
            }
        }
    }

    @OptIn(KaExperimentalApi::class)
    context(_: KaSession)
    private fun collectContextParameters(
        callElement: KtCallElement,
        sink: InlayTreeSink,
        contextMenuPayloads: List<InlayPayload>?,
        contextParameterPairs: List<Pair<KaContextParameterSymbol, KaReceiverValue>>,
        valueParametersWithNames: List<Pair<KaValueParameterSymbol, Name?>>
    ) {
        if (contextParameterPairs.isEmpty()) return

        val valueArgumentList = callElement.valueArgumentList

        val offset = valueArgumentList?.endOffset?.let { it - 1 }
            ?: callElement.lambdaArguments.firstOrNull()?.startOffset
            ?: callElement.endOffset

        val lambdaOnly = valueArgumentList == null

        sink.addPresentation(
            InlineInlayPosition(offset, true),
            payloads = contextMenuPayloads,
            hintFormat = CONTEXT_HINT_FORMAT
        ) {
            collapsibleList(
                state = if (isUnitTestMode()) CollapseState.Expanded else CollapseState.Collapsed,

                expandedState = {
                    if (lambdaOnly){
                        text("(")
                    } else {
                        addParametersSeparator(valueParametersWithNames)
                    }

                    for ((index, pair) in contextParameterPairs.withIndex()) {
                        val (parameterSymbol, receiverValue) = pair
                        if (index > 0) text(" , ")
                        addContextParameter(callElement, parameterSymbol, receiverValue)
                    }

                    // a unicode char <<
                    toggleButton { text(" \u00AB ") }
                    if (lambdaOnly){
                        text(")")
                    }
                },

                collapsedState = {
                    toggleButton { text(Typography.ellipsis.toString()) }
                })
        }
    }

    private fun CollapsiblePresentationTreeBuilder.addParametersSeparator(
        valueParametersWithNames: List<Pair<KaValueParameterSymbol, Name?>>
    ) {
        if (valueParametersWithNames.isNotEmpty()) text(", ")
    }

    @OptIn(KaExperimentalApi::class)
    context(_: KaSession)
    private fun CollapsiblePresentationTreeBuilder.addContextParameter(
        originalCallElement: KtCallElement,
        parameterSymbol: KaContextParameterSymbol,
        receiverValue: KaReceiverValue
    ) {
        val value = receiverValue as? KaImplicitReceiverValue ?: return
        val valueSymbol = value.symbol
        val name = parameterSymbol.name

        text(name.asString(), parameterSymbol.asNavigatablePsiLoad())
        text(" = ")

        val symbolPsi = when (val psi = valueSymbol.psi) {
            is KtParameter -> psi.name
            is KtFunctionLiteral -> {
                val receiverParameterSymbol = valueSymbol as? KaReceiverParameterSymbol ?: return
                val callExpression = receiverParameterSymbol.psi?.getParentOfType<KtCallExpression>(false, KtBlockExpression::class.java)
                val capturedFunctionLiteral = originalCallElement.getParentOfType<KtFunctionLiteral>(false, KtNamedDeclaration::class.java)
                val referenceName =
                    if (callExpression != null && capturedFunctionLiteral != psi) {
                        (callExpression.parent as? KtLabeledExpression)?.getLabelName()
                            ?: (callExpression.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
                    } else {
                        null
                    }
                val owningCallableSymbol = receiverParameterSymbol.owningCallableSymbol

                owningCallableSymbol.receiverParameter?.let {
                    buildString {
                        append("this")
                        referenceName?.let { append("@$it") }
                    }
                } ?: return
            }
            else -> null
        } ?: return

        val targetPsi = when(valueSymbol) {
            is KaReceiverParameterSymbol -> valueSymbol.owningCallableSymbol.psi
            else -> valueSymbol.psi
        }

        text(symbolPsi, targetPsi?.asNavigatablePsiLoad())
    }

    private fun KtValueArgument.isArgumentNamed(symbol: KaValueParameterSymbol): Boolean {
        // avoid cases like "`value =` value"
        val argumentText = this.text
        val symbolName = symbol.name.asString()
        if (argumentText == symbolName) return true

        if (isSimilarName(symbolName, argumentText)) return true

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

    private fun isSimilarName(parameterName: String, otherName: String?): Boolean =
        if (otherName != null && parameterName.length > 1) {
            val lowercase = otherName.lowercase()
            val name = parameterName.lowercase()
            // avoid cases like "`type = Type(...)`" and "`value =` myValue"
            lowercase.startsWith(name) || lowercase.endsWith(name)
        } else {
            false
        }

    private fun KaSymbol.asNavigatablePsiLoad(): InlayActionData? =
        psi?.asNavigatablePsiLoad()

    private fun PsiElement.asNavigatablePsiLoad(): InlayActionData =
        InlayActionData(
            PsiPointerInlayActionPayload(createSmartPointer()),
            PsiPointerInlayActionNavigationHandler.HANDLER_ID
        )

}

private val CONTEXT_HINT_FORMAT = HintFormat.default
    .withFontSize(HintFontSize.ABitSmallerThanInEditor)
    .withColorKind(HintColorKind.Parameter)

private val PARAMETER_HINT_FORMAT = HintFormat.default
    .withColorKind(HintColorKind.Parameter)

context(_: KaSession)
internal fun isExcludeListed(callableFqName: String, parameterNames: List<String>): Boolean {
    return ParameterHintsExcludeListService.getInstance().isExcluded(
        callableFqName,
        parameterNames,
        KotlinLanguage.INSTANCE
    )
}

class KtParameterHintsExcludeListConfigProvider : ParameterHintsExcludeListConfigProvider {
    override fun getDefaultExcludeList(): Set<String> = setOf(
        "*listOf", "*setOf", "*arrayOf", "*ListOf", "*SetOf", "*ArrayOf", "*assert*(*)", "*mapOf", "*MapOf",
        "kotlin.require*(*)", "kotlin.check*(*)", "*contains*(value)", "*containsKey(key)", "kotlin.lazyOf(value)",
        "*SequenceBuilder.resume(value)", "*SequenceBuilder.yield(value)", "kotlin.Triple",

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
        // TODO '*' wildcard is only supported at the start or end of the method name
        // "org.gradle.api.tasks.util.*include(includes)",
        // "org.gradle.api.tasks.util.*exclude(excludes)",
        "org.gradle.kotlin.dsl.kotlin(module)",
        "org.gradle.kotlin.dsl.kotlin(module,version)",
        "org.gradle.kotlin.dsl.project(path,configuration)",
        "org.gradle.api.provider.Property.set(value)",
        "org.gradle.api.plugins.ObjectConfigurationAction.plugin(pluginId)",
    )

    override fun getExcludeListDependencyLanguage(): Language = JavaLanguage.INSTANCE
}

class KtParameterHintsCustomSettingsProvider : AbstractDeclarativeParameterHintsCustomSettingsProvider()

private const val METHOD_NAME: String = "addToExcludeList.name"
private const val PATTERN_TO_ADD: String = "addToExcludeList.pattern"
private const val LANG_ID: String = "addToExcludeList.lang"

fun createAddToExcludeListActionPayloads(methodName: String, patternToAdd: String, language: Language?): List<InlayPayload> = buildList {
    add(InlayPayload(METHOD_NAME, StringInlayActionPayload(methodName)))
    add(InlayPayload(PATTERN_TO_ADD, StringInlayActionPayload(patternToAdd)))
    if (language != null) {
        add(InlayPayload(LANG_ID, StringInlayActionPayload(language.id)))
    }
}

class KtAddToExcludeListAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val methodName = e.getInlayPayloads()?.getStringPayload(METHOD_NAME)

        if (methodName == null) {
            e.presentation.isEnabledAndVisible = false
        } else {
            e.presentation.isEnabledAndVisible = true
            e.presentation.text = getDisableHintText(methodName)
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val payloads = e.getInlayPayloads() ?: return
        val patternToAdd = payloads.getStringPayload(PATTERN_TO_ADD)?: return
        val lang = payloads.getStringPayload(LANG_ID)?.let { Language.findLanguageByID(it) } ?: return
        val config = ParameterHintsExcludeListService.getInstance().getConfig(lang) ?: return
        ExcludeListDialog(config, patternToAdd).show()
    }


    @NlsActions.ActionText
    private fun getDisableHintText(methodName: String): String =
        CodeInsightBundle.message("inlay.hints.show.settings", methodName)
}

private fun AnActionEvent.getInlayPayloads(): Map<String, InlayActionPayload>? =
    getData(InlayHintsProvider.INLAY_PAYLOADS)

private fun Map<String, InlayActionPayload>.getStringPayload(key: String): String? =
    (get(key) as? StringInlayActionPayload)?.text