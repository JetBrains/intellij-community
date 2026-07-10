// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.parameterInfo

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithTabActionSupport
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaSubtypingErrorTypePolicy
import org.jetbrains.kotlin.analysis.api.renderer.render
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.KaApplicableCallCandidateInfo
import org.jetbrains.kotlin.analysis.api.resolution.KaCallCandidateInfo
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.containingDeclaration
import org.jetbrains.kotlin.analysis.api.symbols.name
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.analysis.api.utils.CallParameterInfoProvider
import org.jetbrains.kotlin.idea.base.analysis.api.utils.CallParameterInfoProvider.isArraySetCall
import org.jetbrains.kotlin.idea.base.analysis.api.utils.collectCallCandidates
import org.jetbrains.kotlin.idea.base.analysis.api.utils.defaultValue
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.codeinsights.impl.base.parameterInfo.KotlinParameterInfoBase
import org.jetbrains.kotlin.idea.util.realName
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.NULLABILITY_ANNOTATIONS
import org.jetbrains.kotlin.name.render
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtVariableDeclaration
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.types.Variance
import java.awt.Color
import kotlin.math.min
import kotlin.reflect.KClass

// TODO: Overall structure is similar to FE 1.0 version (KotlinParameterInfoWithCallHandlerBase). Extract common logic to base class.
abstract class KotlinHighLevelParameterInfoWithCallHandlerBase<TArgumentList : KtElement, TArgument : KtElement>(
    private val argumentListClass: KClass<TArgumentList>
) : ParameterInfoHandlerWithTabActionSupport<TArgumentList, KotlinHighLevelParameterInfoWithCallHandlerBase.CallInfo, TArgument> {

    companion object {
        private val GREEN_BACKGROUND: Color = KotlinParameterInfoBase.GREEN_BACKGROUND

        private val STOP_SEARCH_CLASSES: Set<Class<out KtElement>> = setOf(
            KtNamedFunction::class.java,
            KtVariableDeclaration::class.java,
            KtValueArgumentList::class.java,
            KtLambdaArgument::class.java,
            KtContainerNode::class.java,
            KtTypeArgumentList::class.java
        )

        private const val SINGLE_LINE_PARAMETERS_COUNT = 3

        @OptIn(KaExperimentalApi::class)
        private val typeRenderer = KaTypeRendererForSource.WITH_SHORT_NAMES
    }

    override fun getActualParameterDelimiterType(): KtSingleValueToken = KtTokens.COMMA

    override fun getArgListStopSearchClasses(): Set<Class<out KtElement>> = STOP_SEARCH_CLASSES

    override fun getArgumentListClass(): Class<TArgumentList> = argumentListClass.java

    override fun showParameterInfo(element: TArgumentList, context: CreateParameterInfoContext) {
        context.showHint(element, element.textRange.startOffset, this)
    }

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): TArgumentList? {
        val file = context.file as? KtFile ?: return null

        val argumentList = findElementForParameterInfo(file, context.offset) ?: return null

        // findElementForParameterInfo() is only called once before the UI is shown. updateParameterInfo() is called once before the UI
        // is shown, and is also called every time the cursor moves or the call arguments change (i.e., user types something) while the
        // UI is shown, hence the need to resolve the call again in updateParameterInfo() (e.g., argument mappings can change).
        //
        // However, the candidates in findElementForParameterInfo() and updateParameterInfo() are the exact same because the name of the
        // function call CANNOT change while the UI is shown. Because of how ParameterInfoHandler works, we have to store items in
        // `context.itemsToShow` array which becomes `context.objectsToView` array in updateParameterInfo(). The size of this array
        // cannot be changed, but individual elements can be replaced (the array is backed by a plain Java array).
        // TODO: Filter shadowed candidates. See use of ShadowedDeclarationsFilter in KotlinFunctionParameterInfoHandler.kt.
        val currentArgumentIndex = getCurrentArgumentIndex(context.offset, argumentList)
        val callInfos = createCallInfos(argumentList, currentArgumentIndex)
        context.itemsToShow = callInfos.toTypedArray()
        return argumentList
    }

    fun findElementForParameterInfo(file: KtFile, offset: Int): TArgumentList? {
        val token = file.findElementAt(offset) ?: return null
        return PsiTreeUtil.getParentOfType(token, argumentListClass.java, true, *STOP_SEARCH_CLASSES.toTypedArray())
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): TArgumentList? {
        val element = context.file.findElementAt(context.offset) ?: return null
        return PsiTreeUtil.getParentOfType(element, argumentListClass.java)
    }

    override fun updateParameterInfo(argumentList: TArgumentList, context: UpdateParameterInfoContext) {
        if (context.parameterOwner !== argumentList) {
            context.removeHint()
        }
        val currentArgumentIndex = getCurrentArgumentIndex(context.offset, argumentList)
        context.setCurrentParameter(currentArgumentIndex)

        val callInfos = createCallInfos(argumentList, currentArgumentIndex)
        for (index in 0 until min (context.objectsToView.size, callInfos.size)) {
            // Number of candidates somehow changed while UI is shown, which should NOT be possible. Bail out to be safe.
            context.objectsToView[index] = callInfos[index]
        }

        for (index in context.objectsToView.indices) {
            context.objectsToView[index] as? CallInfo ?: continue

            if (index >= callInfos.size) {
                // Number of candidates somehow changed while UI is shown, which should NOT be possible. Bail out to be safe.
                return
            }
            context.objectsToView[index] = callInfos[index]
        }
    }

    fun createCallInfos(argumentList: TArgumentList, currentArgumentIndex: Int): List<CallInfo> {
        val callElement = argumentList.parent as? KtElement ?: return emptyList()
        return analyze(callElement) {
            if (callElement !is KtCallElement && callElement !is KtArrayAccessExpression) return@analyze emptyList()

            val arguments: List<KtExpression?> = CallParameterInfoProvider.getArgumentOrIndexExpressions(callElement)
            val valueArguments: List<KtValueArgument> = (callElement as? KtCallElement)?.valueArgumentList?.arguments.orEmpty()

            val callCandidateInfos = collectCallCandidates(callElement)
            val hasMultipleApplicableBestCandidates =
                callCandidateInfos.count { it is KaApplicableCallCandidateInfo && it.isInBestCandidates } > 1

            callCandidateInfos.map {
                createCallInfo(
                    it,
                    callElement,
                    currentArgumentIndex,
                    arguments,
                    valueArguments,
                    callCandidateInfos.size == 1,
                    hasMultipleApplicableBestCandidates
                )
            }.toList()
        }
    }

    open fun getCurrentArgumentIndex(offset: Int, argumentList: TArgumentList): Int {
        return argumentList.allChildren
            .takeWhile { it.startOffset < offset }
            .count { it.node.elementType == KtTokens.COMMA }
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.createCallInfo(
        callCandidateInfo: KaCallCandidateInfo,
        callElement: KtElement,
        currentArgumentIndex: Int,
        arguments: List<KtExpression?>,
        valueArguments: List<KtValueArgument>,
        isSingleCandidateInfo: Boolean,
        hasMultipleApplicableBestCandidates: Boolean
    ): CallInfo {
        val functionCall = callCandidateInfo.candidate as KaFunctionCall<*>
        val candidateSignature = functionCall.signature
        val argumentMapping = functionCall.valueArgumentMapping
        val isApplicableBestCandidate = callCandidateInfo is KaApplicableCallCandidateInfo && callCandidateInfo.isInBestCandidates

        // For array set calls, we only want the index arguments in brackets, which are all except the last (the value to set).
        val isArraySetCall = isArraySetCall(callElement, candidateSignature)
        val valueParameters = candidateSignature.valueParameters.let { if (isArraySetCall) it.dropLast(1) else it }

        // TODO: When resolvedCall is KtFunctionalTypeVariableCall, the candidate is FunctionN.invoke() and parameter names are "p1", "p2", etc.
        // We need to get the type of the target variable, and retrieve the parameter names from the type (KaFunctionType).
        // The names need to be added to KaFunctionType (currently only types are there) and populated in KtSymbolByFirBuilder.TypeBuilder.

        val parameterToIndexMap = valueParameters.withIndex().associate { (index, parameter) -> parameter to index }

        val valueParameterTextList = valueParameters.map { renderParameter(it) }

        val argumentToParameterIndexMap = CallParameterInfoProvider.mapArgumentsToParameterIndices(
            callElement,
            candidateSignature,
            argumentMapping
        )

        val contextParameters = if (callElement.languageVersionSettings.supportsFeature(LanguageFeature.ExplicitContextArguments))
            candidateSignature.contextParameters
        else
            emptyList()

        val unnamedContextParamLocalIndices = contextParameters.indices
            .filter { contextParameters[it].symbol.name.let { name -> name.isSpecial || name.asString() == "_" } }
            .toSet()

        val contextParameterToSignatureIndexMap = contextParameters.withIndex().associate { (i, sig) -> sig to i }
        val contextArgumentToParameterIndexMap: Map<KtExpression, Int> = functionCall.contextArgumentMapping
            .mapNotNull { (argument, param) ->
                contextParameterToSignatureIndexMap[param]?.let { argument to (valueParameters.size + it) }
            }.toMap()

        // Context parameters are rendered as a separate `context(...)` group after the value parameters.
        val contextParameterTextList = contextParameters.map { renderContextParameter(it) }
        val combinedArgumentToParameterIndexMap = argumentToParameterIndexMap + contextArgumentToParameterIndexMap

        // Determine the parameter to be highlighted.
        val highlightParameterIndex = calculateHighlightParameterIndex(
            arguments,
            currentArgumentIndex,
            combinedArgumentToParameterIndexMap,
            argumentMapping,
            parameterToIndexMap
        )

        val hasTypeMismatchBeforeCurrent = CallParameterInfoProvider.hasTypeMismatchBeforeCurrent(
            callElement,
            argumentMapping,
            currentArgumentIndex,

            // We only want to display the parameter info as "disabled" if the type mismatch can *definitely* be proved. If an
            // argument has a type error instead, the parameter info may still be applicable and even help the user fix the error.
            subtypingErrorTypePolicy = KaSubtypingErrorTypePolicy.LENIENT,
        )

        // We want to highlight the candidate green if it is the only best/final candidate selected and is applicable.
        // However, if there is only one candidate available, we want to highlight it green regardless of its applicability.
        val shouldHighlightGreen =
            (isApplicableBestCandidate && !hasMultipleApplicableBestCandidates) || isSingleCandidateInfo

        val firstArgumentInNamedMode = when (callElement) {
            is KtCallElement -> CallParameterInfoProvider.firstArgumentInNamedMode(
                callElement,
                candidateSignature,
                argumentMapping,
                callElement.languageVersionSettings
            )

            else -> null
        }

        val supportsTrailingCommas = callElement.languageVersionSettings.supportsFeature(LanguageFeature.TrailingCommas)
        val representation = CallStringRepresentation.createCallStringRepresentation(candidateSignature)
        return CallInfo(
            candidateSignature.symbol.psi,
            valueArguments,
            firstArgumentInNamedMode,
            arguments,
            argumentToParameterIndexMap,
            valueParameterTextList,
            contextArgumentToParameterIndexMap,
            contextParameterTextList,
            unnamedContextParamLocalIndices,
            shouldHighlightGreen,
            hasTypeMismatchBeforeCurrent,
            highlightParameterIndex,
            isDeprecated = candidateSignature.symbol.deprecation != null,
            representation,
            supportsTrailingCommas,
        )
    }

    @OptIn(KaExperimentalApi::class)
    context(session: KaSession)
    private fun renderParameter(
        parameter: KaVariableSignature<KaValueParameterSymbol>,
    ): String {
        return buildString {
            val annotationFqNames =
                parameter.symbol.annotations
                    .mapNotNull { it.classId?.asSingleFqName() }
                    .filter { it !in NULLABILITY_ANNOTATIONS }
            annotationFqNames.forEach { append("@${it.shortName().asString()} ") }

            if (parameter.symbol.isVararg) {
                append("vararg ")
            }

            parameter.realName?.let {
                append(it)
                append(": ")
            }

            val returnType = parameter.returnType.takeUnless { it is KaErrorType } ?: parameter.symbol.returnType
            append(returnType.render(typeRenderer, Variance.INVARIANT))

            parameter.symbol.defaultValue?.let { defaultValue ->
                append(" = ")
                val expressionValue = KotlinParameterInfoBase.getDefaultValueStringRepresentation(defaultValue)
                if (defaultValue is KtNameReferenceExpression) {
                    val referencedName = defaultValue.getReferencedName()
                    if (expressionValue.isConstValue) {
                        append(referencedName)
                        append(" = ")
                    }
                }
                append(expressionValue.text)
            }
        }
    }

    @OptIn(KaExperimentalApi::class)
    context(_: KaSession)
    private fun renderContextParameter(parameter: KaVariableSignature<KaParameterSymbol>): String {
        val returnType = parameter.returnType.takeUnless { it is KaErrorType } ?: parameter.symbol.returnType
        return buildString {
            val name = parameter.symbol.name.takeIf { !it.isSpecial }?.render() ?: "_"
            append(name)
            append(": ")
            append(returnType.render(typeRenderer, position = Variance.INVARIANT))
        }
    }

    private fun calculateHighlightParameterIndex(
        arguments: List<KtExpression?>,
        currentArgumentIndex: Int,
        argumentToParameterIndexMap: Map<KtExpression, Int>,
        argumentMapping: Map<KtExpression, KaVariableSignature<KaValueParameterSymbol>>,
        parameterToIndex: Map<KaVariableSignature<KaValueParameterSymbol>, Int>
    ): Int? {
        val currentArgument = arguments.getOrNull(currentArgumentIndex)
        if (currentArgument != null) {
            return argumentToParameterIndexMap[currentArgument]
        }
        // If last argument is for a vararg parameter, then the argument about to be entered at the cursor should also be
        // for that same vararg parameter.
        if (arguments.isEmpty() || currentArgumentIndex != arguments.size) {
            return null
        }

        val parameter = arguments.lastOrNull()?.let { argumentMapping[it] } ?: return null
        return parameterToIndex.takeIf { parameter.symbol.isVararg }?.get(parameter)
    }

    override fun updateUI(itemToShow: CallInfo, context: ParameterInfoUIContext) {
        if (!updateUIOrFail(itemToShow, context)) {
            context.isUIComponentEnabled = false
            return
        }
    }

    /**
     * This function sets up the presentation for a candidate's parameters, via the call to `setupUIComponentPresentation()` at the end.
     *
     * The logic for parameter order in the text is as follows:
     * 1. In general, the parameters are listed in the order they are listed in the candidate.
     * 2. However, the call may have a named argument that's NOT in its own position. In this case, you will have all parameters
     *    corresponding to named arguments first (in their order in the call), followed by any unused parameters in the order they
     *    are listed in the candidate. See NamedParameter*.kt tests.
     * 3. Substituted types and default values should be rendered. That is, types from signature should be used instead of types from
     *    symbols.
     *
     * The logic for surrounding parameters in brackets (e.g., "[x: Int], ...") is as follows:
     * 1. When an argument is named, the parameter for that argument is surrounded in brackets.
     * 2. When the named argument is NOT in its own position, then its corresponding parameter and all parameters after that are
     *    surrounded in brackets (i.e., `namedMode = true` in the code below). This is because all arguments must be named after it.
     *    See NamedParameter4.kt test.
     * 3. When the named argument IS in its own position, and LanguageFeature.MixedNamedArgumentsInTheirOwnPosition is DISABLED, then
     *    `namedMode = true` as described above. See MixedNamedArguments2.kt test.
     * 4. `isDisabledBeforeHighlight = true` is used to separate used parameters (before the highlight) from unused parameters, if there
     *    are any unused parameters.
     *
     * The logic for highlighting a parameter (i.e., by setting `highlight(Start|End)Offset`) is as follows:
     * 1. A parameter is highlighted when the cursor is on the corresponding argument in the candidate's mapping.
     * 2. If the argument on the cursor does NOT map to a parameter (e.g., on N-th argument but there are < N parameters),
     *    then NO parameter is highlighted.
     * 3. If the cursor is after a trailing comma with no argument, then we assume the user is about to enter an argument in that
     *    position. If there are N commas in the call arguments, AND if there are > N parameters, then the parameter in the
     *    N+1 position is highlighted, (e.g., the 3rd parameter for `foo(Int, Int, Int)` is highlighted in `foo(1, 2, <cursor>)`).
     *    If there are <= N parameters, then NO parameter is highlighted (e.g., `foo(1, 2, 3, <cursor>)`).
     *
     * `setupUIComponentPresentation()` is called with `disabled = true` when any of the following are true:
     * 1. If the argument on the cursor does NOT map to a parameter (e.g., on N-th argument but there are < N parameters),
     * 2. If any of the arguments before the cursor do NOT match the type of the corresponding parameter (ignoring arguments with type
     *    errors, e.g., unresolved) or do NOT map to a parameter (e.g., named argument with an unknown name).
     * 3. If the cursor is after a trailing comma with no argument, AND LanguageFeature.TrailingCommas is DISABLED, AND there are
     *    already enough arguments in the call. (We assume the user is about to enter an argument in that position.)
     *
     * `setupUIComponentPresentation()` is called with `backgroundColor = GREEN_BACKGROUND` if the call resolves to this candidate,
     * even if there are type mismatches. Otherwise (e.g., the candidate is an overload with the same name), a default color is used.
     *
     * `setupUIComponentPresentation()` is called with `strikeout = true` if the candidate is annotated with `@Deprecated`.
     */
    private fun updateUIOrFail(itemToShow: CallInfo, context: ParameterInfoUIContext): Boolean {

        if (context.parameterOwner == null || !context.parameterOwner.isValid) return false
        if (!argumentListClass.java.isInstance(context.parameterOwner)) return false

        val uiModel = itemToShow.toUiModel(context.currentParameterIndex) ?: return false
        val backgroundColor = if (itemToShow.shouldHighlightGreen) GREEN_BACKGROUND else context.defaultParameterColor
        val signature = uiModel.signatureModel
        val text = buildString {
            signature.parts.forEach { piece -> append(piece.text) }
            val useMultilineParameters = Registry.`is`("kotlin.multiline.function.parameters.info", false)
            if (signature.parts.isNotEmpty() && useMultilineParameters && signature.parts.size > SINGLE_LINE_PARAMETERS_COUNT) {
                var isFirstParameter = true
                signature.parts.forEachIndexed { i, piece ->
                    if (piece !is SignaturePart.Parameter) return@forEachIndexed
                    if (!isFirstParameter) {
                        val offset = signature.getRange(i).first
                        replace(offset - 1, offset, "\n")
                    }
                    isFirstParameter = false
                }
            }
        }
        val highlightedRange = run {
            val indexedPart = signature.parts.withIndex()
                .firstOrNull { (it.value as? SignaturePart.Parameter)?.isHighlighted == true }
                ?: return@run Pair(-1, -1)
            val part = indexedPart.value as SignaturePart.Parameter
            val rangeStart = signature.getRange(indexedPart.index).first
            Pair(rangeStart + part.innerHighlightStart, rangeStart + part.innerHighlightEnd)
        }
        context.setupUIComponentPresentation(
            text,
            highlightedRange.first,
            highlightedRange.second,
            uiModel.isDisabled,
            /*strikeout=*/ itemToShow.isDeprecated,
            uiModel.isDisabledBeforeHighlight,
            backgroundColor
        )

        return true
    }

    sealed interface SignaturePart {
        val text: String

        data class Text(override val text: String) : SignaturePart
        data class Parameter(
            override val text: String,
            val isHighlighted: Boolean,
            val innerHighlightStart: Int = 0,
            val innerHighlightEnd: Int = text.length,
        ) : SignaturePart
    }

    data class UiModel(
        val signatureModel: SignatureModel,
        val isDisabled: Boolean,
        val isDisabledBeforeHighlight: Boolean,
    )

    data class SignatureModel(
        val parts: List<SignaturePart>,
    ) {
        val text: String get() = parts.joinToString("") { it.text }

        private val ranges by lazy {
            var offset = 0
            val result = mutableListOf<Pair<Int, Int>>()
            for (piece in parts) {
                result += Pair(offset, offset + piece.text.length)
                offset += piece.text.length
            }
            result
        }

        fun getRange(index: Int): Pair<Int, Int> = ranges[index]
    }

    internal data class ParameterInfoState(
        var wasParameterHighlighted: Boolean = false,
        var isDisabledBeforeHighlight: Boolean = false,
        var hasUnmappedArgument: Boolean = false,
        var hasUnmappedArgumentBeforeCurrent: Boolean = false,
        var lastMappedArgumentIndex: Int = -1,
        var namedMode: Boolean = false,
        var argumentIndex: Int = 0,
        val signatureParts: MutableList<SignaturePart> = mutableListOf(),
        val usedParameterIndices: MutableSet<Int> = mutableSetOf(),
        val usedContextParameterIndices: MutableList<Int> = mutableListOf(),
    )

    data class CallInfo(
        val target: PsiElement?,
        val valueArguments: List<KtValueArgument>,
        val firstArgumentInNamedMode: KtValueArgument?,
        val arguments: List<KtExpression?>,
        val valueArgumentToParameterIndexMap: Map<KtExpression, Int>,
        val valueParameterTextList: List<String>,
        val contextArgumentToParameterIndexMap: Map<KtExpression, Int>,
        val contextParameterTextList: List<String>,
        val unnamedContextParamLocalIndices: Set<Int>,
        val shouldHighlightGreen: Boolean,
        val hasTypeMismatchBeforeCurrent: Boolean,
        val highlightParameterIndex: Int?,
        val isDeprecated: Boolean,
        val representation: CallStringRepresentation,
        val supportsTrailingCommas: Boolean,
    ) {
        internal fun appendValueParameter(
            parameterIndex: Int,
            shouldHighlight: Boolean = false,
            isNamed: Boolean = false,
            markUsedUnusedParameterBorder: Boolean = false,
            parameterInfoState: ParameterInfoState,
        ) {
            val surroundInBrackets = isNamed || parameterInfoState.namedMode
            val parameterText = buildString {
                if (surroundInBrackets) append("[")
                append(valueParameterTextList[parameterIndex])
                if (surroundInBrackets) append("]")
            }
            updateParameterInfoState(parameterText, shouldHighlight, markUsedUnusedParameterBorder, parameterInfoState)
        }

        internal fun appendContextParameter(
            parameterIndex: Int,
            isFirstContextParameter: Boolean,
            isLastContextParameter: Boolean,
            shouldHighlight: Boolean = false,
            markUsedUnusedParameterBorder: Boolean = false,
            parameterInfoState: ParameterInfoState,
        ) {
            val prefix = if (isFirstContextParameter) "context(" else ""
            val paramName = contextParameterTextList[parameterIndex]
            val suffix = if (isLastContextParameter) ")" else ""
            val parameterText = prefix + paramName + suffix
            updateParameterInfoState(
                parameterText, shouldHighlight, markUsedUnusedParameterBorder, parameterInfoState,
                innerHighlightStart = prefix.length,
                innerHighlightEnd = prefix.length + paramName.length,
            )
        }

        private fun updateParameterInfoState(
            parameterText: String,
            shouldHighlight: Boolean,
            markUsedUnusedParameterBorder: Boolean,
            parameterInfoState: ParameterInfoState,
            innerHighlightStart: Int = 0,
            innerHighlightEnd: Int = parameterText.length,
        ) = with(parameterInfoState) {
            argumentIndex++
            if (signatureParts.isNotEmpty()) {
                signatureParts.add(SignaturePart.Text(","))
                if (markUsedUnusedParameterBorder) {
                    // This is used to "disable" the used parameters when in "named mode" and there are more unused parameters.
                    // See NamedParameter3.kt test. Disabling them gives a visual cue that they are already used.
                    signatureParts.add(SignaturePart.Parameter("", isHighlighted = true))
                    isDisabledBeforeHighlight = true
                    wasParameterHighlighted = true
                }
                signatureParts.add(SignaturePart.Text(" "))
            }
            if (shouldHighlight) {
                wasParameterHighlighted = true
            }
            signatureParts.add(SignaturePart.Parameter(parameterText, shouldHighlight, innerHighlightStart, innerHighlightEnd))
        }

        fun toUiModel(
            currentParameterIndex: Int,
            appendNoParametersMessage: Boolean = true,
        ): UiModel? {
            val currentArgumentIndex = min(currentParameterIndex, arguments.size)
            if (currentArgumentIndex < 0) return null
            val parameterInfoState = ParameterInfoState()

            if (valueArguments.isNotEmpty()) {
                for (valueArgument in valueArguments) {
                    val argumentExpression = valueArgument.getArgumentExpression()
                    if (argumentExpression in contextArgumentToParameterIndexMap) {
                        processArgument(
                            currentArgumentIndex,
                            contextArgumentToParameterIndexMap[argumentExpression],
                            parameterInfoState,
                        ) { absoluteParameterIndex ->
                            parameterInfoState.usedContextParameterIndices += absoluteParameterIndex
                            parameterInfoState.argumentIndex++
                        }
                    } else {
                        if (valueArgument == firstArgumentInNamedMode) {
                            parameterInfoState.namedMode = true
                        }
                        processArgument(
                            currentArgumentIndex,
                            valueArgumentToParameterIndexMap[argumentExpression],
                            parameterInfoState,
                        ) { parameterIndex ->
                            appendValueParameter(
                                parameterIndex,
                                shouldHighlight = parameterIndex == highlightParameterIndex,
                                valueArgument.isNamed(),
                                parameterInfoState = parameterInfoState
                            )
                        }
                    }
                }

                appendValueParameters(parameterInfoState)
                appendContextParameters(parameterInfoState)
            } else {
                // This is for array get/set calls which don't have KtValueArguments.
                for (argument in arguments) {
                    processArgument(
                        currentArgumentIndex,
                        valueArgumentToParameterIndexMap[argument],
                        parameterInfoState
                    ) { parameterIndex ->
                        appendValueParameter(
                            parameterIndex,
                            shouldHighlight = parameterIndex == highlightParameterIndex,
                            parameterInfoState = parameterInfoState
                        )
                    }
                }
                appendValueParameters(parameterInfoState)
                appendContextParameters(parameterInfoState)
            }

            if (appendNoParametersMessage && parameterInfoState.signatureParts.isEmpty()) {
                parameterInfoState.signatureParts.add(SignaturePart.Text(CodeInsightBundle.message("parameter.info.no.parameters")))
            }

            // Disabled when there are too many arguments.
            val namedContextParamCount = contextParameterTextList.size - unnamedContextParamLocalIndices.size
            val allParametersUsed = parameterInfoState.usedParameterIndices.size == valueParameterTextList.size + namedContextParamCount
            val afterTrailingComma = arguments.isNotEmpty() && currentArgumentIndex == arguments.size
            val isInPositionToEnterArgument = !supportsTrailingCommas && afterTrailingComma
            val isAfterMappedArgs = currentArgumentIndex > parameterInfoState.lastMappedArgumentIndex
            val tooManyArgs =
                allParametersUsed && (isInPositionToEnterArgument || parameterInfoState.hasUnmappedArgument) && (isAfterMappedArgs || parameterInfoState.namedMode)

            val isDisabled = tooManyArgs || hasTypeMismatchBeforeCurrent || parameterInfoState.hasUnmappedArgumentBeforeCurrent

            return UiModel(
                SignatureModel(parameterInfoState.signatureParts),
                isDisabled,
                parameterInfoState.isDisabledBeforeHighlight,
            )
        }

        private fun appendValueParameters(parameterInfoState: ParameterInfoState) {
            for (parameterIndex in valueParameterTextList.indices) {
                if (parameterIndex in parameterInfoState.usedParameterIndices) continue
                appendValueParameter(
                    parameterIndex,
                    shouldHighlight = !parameterInfoState.namedMode && !parameterInfoState.wasParameterHighlighted,
                    markUsedUnusedParameterBorder = parameterInfoState.namedMode && !parameterInfoState.wasParameterHighlighted,
                    parameterInfoState = parameterInfoState
                )
            }
        }

        private fun appendContextParameters(
            parameterInfoState: ParameterInfoState,
        ) {
            if (contextParameterTextList.isEmpty()) return

            val orderedAbsoluteIndices = buildList {
                addAll(parameterInfoState.usedContextParameterIndices)
                for (localIndex in contextParameterTextList.indices) {
                    val absoluteIndex = valueParameterTextList.size + localIndex
                    if (absoluteIndex !in parameterInfoState.usedParameterIndices) {
                        add(absoluteIndex)
                    }
                }
            }

            if (orderedAbsoluteIndices.isEmpty()) return

            orderedAbsoluteIndices.forEachIndexed { index, absoluteIndex ->
                val indexInContextParametersList = absoluteIndex - valueParameterTextList.size
                val isAlreadyProvided = absoluteIndex in parameterInfoState.usedContextParameterIndices
                val isUnnamed = indexInContextParametersList in unnamedContextParamLocalIndices
                appendContextParameter(
                    parameterIndex = indexInContextParametersList,
                    isFirstContextParameter = index == 0,
                    isLastContextParameter = index == orderedAbsoluteIndices.lastIndex,
                    shouldHighlight = absoluteIndex == highlightParameterIndex ||
                            (!isAlreadyProvided && !isUnnamed && highlightParameterIndex == null && !parameterInfoState.wasParameterHighlighted),
                    markUsedUnusedParameterBorder = index == 0 && parameterInfoState.namedMode && !parameterInfoState.wasParameterHighlighted,
                    parameterInfoState,
                )
            }
        }

        private fun processArgument(
            currentArgumentIndex: Int,
            parameterIndex: Int?,
            parameterInfoState: ParameterInfoState,
            onMapped: (parameterIndex: Int) -> Unit,
        ) {
            if (parameterIndex == null) {
                parameterInfoState.hasUnmappedArgument = true
                if (parameterInfoState.argumentIndex < currentArgumentIndex) {
                    parameterInfoState.hasUnmappedArgumentBeforeCurrent = true
                }
                parameterInfoState.argumentIndex++
                return
            }
            parameterInfoState.lastMappedArgumentIndex = parameterInfoState.argumentIndex
            if (!parameterInfoState.usedParameterIndices.add(parameterIndex)) return
            onMapped(parameterIndex)
        }
    }

    data class CallStringRepresentation(
        val beforeParameters: String,
        val afterParameters: String,
    ) {
        companion object {
            @OptIn(KaExperimentalApi::class)
            context(_: KaSession)
            fun createCallStringRepresentation(candidateSignature: KaFunctionSignature<KaFunctionSymbol>): CallStringRepresentation {
                val beforeParameters = buildString {
                    candidateSignature.receiverType?.let {
                        append(it.render(typeRenderer, position = Variance.IN_VARIANCE))
                        append(".")
                    }
                    val name = when (val symbol = candidateSignature.symbol) {
                        is KaConstructorSymbol -> (symbol.containingDeclaration as? KaClassSymbol)?.name
                        else -> symbol.name
                    }
                    append(name?.render())
                }
                val afterParameters = buildString {
                    when (candidateSignature.symbol) {
                        is KaConstructorSymbol -> {}
                        else -> {
                            append(": ")
                            append(candidateSignature.returnType.render(typeRenderer, position = Variance.OUT_VARIANCE))
                        }
                    }
                }
                return CallStringRepresentation(beforeParameters, afterParameters)
            }
        }
    }

}
