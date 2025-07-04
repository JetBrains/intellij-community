// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.parameterInfo

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithTabActionSupport
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.components.KaSubtypingErrorTypePolicy
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.name
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.analysis.api.utils.*
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.codeinsights.impl.base.parameterInfo.KotlinParameterInfoBase
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.NULLABILITY_ANNOTATIONS
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.types.Variance
import java.awt.Color
import kotlin.math.min
import kotlin.reflect.KClass

class KotlinHighLevelFunctionParameterInfoHandler :
    KotlinHighLevelParameterInfoWithCallHandlerBase<KtValueArgumentList, KtValueArgument>(
        KtValueArgumentList::class, KtValueArgument::class
    ) {
    override fun getActualParameters(arguments: KtValueArgumentList): Array<KtValueArgument?> = arguments.arguments.toTypedArray()

    override fun getActualParametersRBraceType(): KtSingleValueToken = KtTokens.RPAR

    override fun getArgumentListAllowedParentClasses(): Set<Class<KtCallElement>> = setOf(KtCallElement::class.java)
}

class KotlinHighLevelLambdaParameterInfoHandler :
    KotlinHighLevelParameterInfoWithCallHandlerBase<KtLambdaArgument, KtLambdaArgument>(KtLambdaArgument::class, KtLambdaArgument::class) {

    override fun getActualParameters(lambdaArgument: KtLambdaArgument): Array<KtLambdaArgument> = arrayOf(lambdaArgument)

    override fun getActualParametersRBraceType(): KtSingleValueToken = KtTokens.RBRACE

    override fun getArgumentListAllowedParentClasses(): Set<Class<KtLambdaArgument>> = setOf(KtLambdaArgument::class.java)

    override fun getCurrentArgumentIndex(offset: Int, argumentList: KtLambdaArgument): Int {
        val size = (argumentList.parent as? KtCallElement)?.valueArguments?.size ?: 1
        return size - 1
    }
}

class KotlinHighLevelArrayAccessParameterInfoHandler :
    KotlinHighLevelParameterInfoWithCallHandlerBase<KtContainerNode, KtExpression>(KtContainerNode::class, KtExpression::class) {

    override fun getArgumentListAllowedParentClasses(): Set<Class<KtArrayAccessExpression>> = setOf(KtArrayAccessExpression::class.java)

    override fun getActualParameters(containerNode: KtContainerNode): Array<out KtExpression> =
        containerNode.allChildren.filterIsInstance<KtExpression>().toList().toTypedArray()

    override fun getActualParametersRBraceType(): KtSingleValueToken = KtTokens.RBRACKET
}

// TODO: Overall structure is similar to FE 1.0 version (KotlinParameterInfoWithCallHandlerBase). Extract common logic to base class.
abstract class KotlinHighLevelParameterInfoWithCallHandlerBase<TArgumentList : KtElement, TArgument : KtElement>(
    private val argumentListClass: KClass<TArgumentList>,
    private val argumentClass: KClass<TArgument>
) : ParameterInfoHandlerWithTabActionSupport<TArgumentList, KotlinHighLevelParameterInfoWithCallHandlerBase.CandidateInfo, TArgument> {

    companion object {
        @JvmField
        val GREEN_BACKGROUND: Color = JBColor(Color(231, 254, 234), Gray._100)

        val STOP_SEARCH_CLASSES: Set<Class<out KtElement>> = setOf(
            KtNamedFunction::class.java,
            KtVariableDeclaration::class.java,
            KtValueArgumentList::class.java,
            KtLambdaArgument::class.java,
            KtContainerNode::class.java,
            KtTypeArgumentList::class.java
        )

        private const val SINGLE_LINE_PARAMETERS_COUNT = 3

        private val ANNOTATION_TARGET_TYPE = CallableId(StandardClassIds.AnnotationTarget, Name.identifier(AnnotationTarget.TYPE.name))
        private val ANNOTATION_TARGET_VALUE_PARAMETER = CallableId(StandardClassIds.AnnotationTarget, Name.identifier(AnnotationTarget.VALUE_PARAMETER.name))

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

        val callElement = argumentList.parent as? KtElement ?: return null
        return analyze(callElement) {
            // findElementForParameterInfo() is only called once before the UI is shown. updateParameterInfo() is called once before the UI
            // is shown, and is also called every time the cursor moves or the call arguments change (i.e., user types something) while the
            // UI is shown, hence the need to resolve the call again in updateParameterInfo() (e.g., argument mappings can change).
            //
            // However, the candidates in findElementForParameterInfo() and updateParameterInfo() are the exact same because the name of the
            // function call CANNOT change while the UI is shown. Because of how ParameterInfoHandler works, we have to store _something_ in
            // `context.itemsToShow` array which becomes `context.objectsToView` array in updateParameterInfo(). Unfortunately
            // `objectsToView` is read-only so we can't change the size of the array. So we have to store an array here of the correct size,
            // which does mean we have to resolve here to know the number of candidates.
            val candidatesWithMapping = collectCallCandidates(callElement)

            // TODO: Filter shadowed candidates. See use of ShadowedDeclarationsFilter in KotlinFunctionParameterInfoHandler.kt.
            context.itemsToShow = Array(candidatesWithMapping.size) { CandidateInfo() }

            argumentList
        }
    }

    fun findElementForParameterInfo(file: KtFile, offset: Int): TArgumentList? {
        val token = file.findElementAt(offset) ?: return null
        return PsiTreeUtil.getParentOfType(token, argumentListClass.java, true, *STOP_SEARCH_CLASSES.toTypedArray())
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): TArgumentList? {
        val element = context.file.findElementAt(context.offset) ?: return null
        return PsiTreeUtil.getParentOfType(element, argumentListClass.java)
    }

    @OptIn(KaExperimentalApi::class)
    override fun updateParameterInfo(argumentList: TArgumentList, context: UpdateParameterInfoContext) {
        if (context.parameterOwner !== argumentList) {
            context.removeHint()
        }
        val currentArgumentIndex = getCurrentArgumentIndex(context.offset, argumentList)
        context.setCurrentParameter(currentArgumentIndex)

        val callInfos = createCallInfos(argumentList, currentArgumentIndex) ?: return
        for ((index, objectToView) in context.objectsToView.withIndex()) {
            val candidateInfo = objectToView as? CandidateInfo ?: continue

            if (index >= callInfos.size) {
                // Number of candidates somehow changed while UI is shown, which should NOT be possible. Bail out to be safe.
                return
            }
            candidateInfo.callInfo = callInfos[index]
        }
    }

    @OptIn(KaExperimentalApi::class)
    fun createCallInfos(argumentList: TArgumentList, currentArgumentIndex: Int): List<CallInfo>? {
        val callElement = argumentList.parent as? KtElement ?: return null
        val result = mutableListOf<CallInfo>()
        analyze(callElement) {
            if (callElement !is KtCallElement && callElement !is KtArrayAccessExpression) return@analyze

            val arguments = CallParameterInfoProvider.getArgumentOrIndexExpressions(callElement)
            val valueArguments = (callElement as? KtCallElement)?.valueArgumentList?.arguments

            val candidates = collectCallCandidates(callElement)
            val hasMultipleApplicableBestCandidates = candidates.count { candidate -> candidate.withMapping.isApplicableBestCandidate } > 1
            for ((index, objectToView) in candidates.withIndex()) {
                val (candidateSignature, argumentMapping, isApplicableBestCandidate) = candidates[index].withMapping

                // For array set calls, we only want the index arguments in brackets, which are all except the last (the value to set).
                val isArraySetCall = CallParameterInfoProvider.isArraySetCall(callElement, candidateSignature)
                val valueParameters = candidateSignature.valueParameters.let { if (isArraySetCall) it.dropLast(1) else it }

                // TODO: When resolvedCall is KtFunctionalTypeVariableCall, the candidate is FunctionN.invoke() and parameter names are "p1", "p2", etc.
                // We need to get the type of the target variable, and retrieve the parameter names from the type (KaFunctionType).
                // The names need to be added to KaFunctionType (currently only types are there) and populated in KtSymbolByFirBuilder.TypeBuilder.

                val parameterToIndex = buildMap {
                    valueParameters.forEachIndexed { index, parameter -> put(parameter, index) }
                }

                val parameterIndexToText = buildMap {
                    valueParameters.forEachIndexed { index, parameter ->
                        // TODO: Add hasSynthesizedParameterNames to HL API.
                        // See resolveValueParameters() in core/descriptors.jvm/src/org/jetbrains/kotlin/load/java/lazy/descriptors/LazyJavaScope.kt
                        val hasSynthesizedParameterNames = false
                        val parameterText = renderParameter(parameter, includeName = !hasSynthesizedParameterNames)
                        put(index, parameterText)
                    }
                }

                val argumentToParameterIndex = CallParameterInfoProvider.mapArgumentsToParameterIndices(
                    callElement,
                    candidateSignature,
                    argumentMapping
                )

                // Determine the parameter to be highlighted.
                val highlightParameterIndex = calculateHighlightParameterIndex(
                    arguments,
                    currentArgumentIndex,
                    argumentToParameterIndex,
                    argumentMapping,
                    parameterToIndex
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
                val shouldHighlightGreen = (isApplicableBestCandidate && !hasMultipleApplicableBestCandidates) || candidates.size == 1

                val firstArgumentInNamedMode = when (callElement) {
                    is KtCallElement -> CallParameterInfoProvider.firstArgumentInNamedMode(
                        callElement,
                        candidateSignature,
                        argumentMapping,
                        callElement.languageVersionSettings
                    )

                    else -> null
                }

                val representation = CallStringRepresentation(
                    buildString {
                        candidateSignature.receiverType?.let {
                            append(it.render(typeRenderer, position = Variance.IN_VARIANCE))
                            append(".")
                        }
                        val name = when(val symbol = candidateSignature.symbol) {
                            is KaConstructorSymbol -> (symbol.containingDeclaration as? KaClassSymbol)?.name
                            else -> symbol.name
                        }
                        append(name?.render())
                    },
                    buildString {
                        when(val symbol = candidateSignature.symbol) {
                            is KaConstructorSymbol ->{}
                            else -> {
                                append(": ")
                                append(candidateSignature.returnType.render(typeRenderer, position = Variance.OUT_VARIANCE))
                            }
                        }

                    }
                )

                result += CallInfo(
                    candidateSignature.symbol.psi,
                    callElement,
                    valueArguments,
                    firstArgumentInNamedMode,
                    arguments,
                    argumentToParameterIndex,
                    valueParameters.size,
                    parameterIndexToText,
                    shouldHighlightGreen,
                    hasTypeMismatchBeforeCurrent,
                    highlightParameterIndex,
                    isDeprecated = candidateSignature.symbol.deprecationStatus != null,
                    representation,
                )
            }
        }
        return result
    }

    open fun getCurrentArgumentIndex(offset: Int, argumentList: TArgumentList): Int {
        return argumentList.allChildren
            .takeWhile { it.startOffset < offset }
            .count { it.node.elementType == KtTokens.COMMA }
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun renderParameter(
        parameter: KaVariableSignature<KaValueParameterSymbol>,
        includeName: Boolean
    ): String {
        return buildString {
            val annotationFqNames =
                parameter.symbol.annotations
                    .filter {
                        // For primary constructor parameters, the annotation use site must be "param" or unspecified.
                        (it.useSiteTarget == null || it.useSiteTarget == AnnotationUseSiteTarget.CONSTRUCTOR_PARAMETER) &&
                                !it.isAnnotatedWithTypeUseOnly()
                    }
                    .mapNotNull { it.classId?.asSingleFqName() }
                    .filter { it !in NULLABILITY_ANNOTATIONS }
            annotationFqNames.forEach { append("@${it.shortName().asString()} ") }

            if (parameter.symbol.isVararg) {
                append("vararg ")
            }

            if (includeName) {
                append(parameter.name)
                append(": ")
            }

            val returnType = parameter.returnType.takeUnless { it is KaErrorType } ?: parameter.symbol.returnType
            append(returnType.render(typeRenderer, position = Variance.INVARIANT))

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

    context(KaSession)
    private fun KaAnnotation.isAnnotatedWithTypeUseOnly(): Boolean =
        (constructorSymbol?.containingSymbol as? KaClassSymbol)
            ?.hasApplicableAllowedTarget {
                it.isApplicableTargetSet(ANNOTATION_TARGET_TYPE) &&
                        !it.isApplicableTargetSet(ANNOTATION_TARGET_VALUE_PARAMETER)
            } ?: false

    private fun calculateHighlightParameterIndex(
        arguments: List<KtExpression?>,
        currentArgumentIndex: Int,
        argumentToParameterIndex: Map<KtExpression, Int>,
        argumentMapping: Map<KtExpression, KaVariableSignature<KaValueParameterSymbol>>,
        parameterToIndex: Map<KaVariableSignature<KaValueParameterSymbol>, Int>
    ): Int? {
        val afterTrailingComma = arguments.isNotEmpty() && currentArgumentIndex == arguments.size
        val highlightParameterIndex = when {
            currentArgumentIndex < arguments.size -> argumentToParameterIndex[arguments[currentArgumentIndex]]
            afterTrailingComma -> {
                // If last argument is for a vararg parameter, then the argument about to be entered at the cursor should also be
                // for that same vararg parameter.
                val parameterForLastArgument = argumentMapping[arguments.last()]
                if (parameterForLastArgument?.symbol?.isVararg == true) {
                    parameterToIndex[parameterForLastArgument]
                } else {
                    null
                }
            }
            else -> null
        }
        return highlightParameterIndex
    }

    override fun updateUI(itemToShow: CandidateInfo, context: ParameterInfoUIContext) {
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
    private fun updateUIOrFail(itemToShow: CandidateInfo, context: ParameterInfoUIContext): Boolean {

        if (context.parameterOwner == null || !context.parameterOwner.isValid) return false
        if (!argumentListClass.java.isInstance(context.parameterOwner)) return false

        val callInfo = itemToShow.callInfo ?: return false
        val uiModel = callInfo.toUiModel(context.currentParameterIndex) ?: return false
        val backgroundColor = if (callInfo.shouldHighlightGreen) GREEN_BACKGROUND else context.defaultParameterColor
        val signature = uiModel.signatureModel
        val text = buildString {
            signature.parts.forEach { piece -> append(piece.text) }
            val useMultilineParameters = Registry.`is`("kotlin.multiline.function.parameters.info", false)
            if (useMultilineParameters && signature.parts.size > SINGLE_LINE_PARAMETERS_COUNT) {
                signature.parts.forEachIndexed { i, piece ->
                    if (piece is SignaturePart.Parameter) {
                        val offset = signature.getRange(i).first
                        replace(offset - 1, offset, "\n")
                    }
                }
            }
        }
        val highlightedRange  =run {
            val index= signature.parts.withIndex()
                .firstOrNull { (it.value as? SignaturePart.Parameter)?.isHighlighted == true }?.index
                ?: return@run Pair(-1, -1)
            signature.getRange(index)
        }
        context.setupUIComponentPresentation(
            text,
            highlightedRange.first,
            highlightedRange.second,
            uiModel.isDisabled,
            /*strikeout=*/ callInfo.isDeprecated,
            uiModel.isDisabledBeforeHighlight,
            backgroundColor
        )

        return true
    }

    sealed interface SignaturePart {
        val text: String

        data class Text(override val text: String) : SignaturePart
        data class Parameter(override val text: String, val isHighlighted: Boolean) : SignaturePart
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

        fun getRange(index: Int): Pair<Int, Int> {
            return ranges[index]
        }
    }

    fun CallInfo.toUiModel(
        currentParameterIndex: Int,
        appendNoParametersMessage: Boolean = true,
    ): UiModel? {
        val currentArgumentIndex = min(currentParameterIndex, arguments.size)
        if (currentArgumentIndex < 0) return null
        var wasParameterHighlighted = false
        var isDisabledBeforeHighlight = false
        var hasUnmappedArgument = false
        var hasUnmappedArgumentBeforeCurrent = false
        var lastMappedArgumentIndex = -1
        var namedMode = false
        val usedParameterIndices = HashSet<Int>()

        val signatureParts = mutableListOf<SignaturePart>()

        var argumentIndex = 0

        fun appendParameter(
            parameterIndex: Int,
            shouldHighlight: Boolean = false,
            isNamed: Boolean = false,
            markUsedUnusedParameterBorder: Boolean = false
        ) {
            argumentIndex++

            if (signatureParts.isNotEmpty()) {
                signatureParts.add(SignaturePart.Text(","))
                if (markUsedUnusedParameterBorder) {
                    // This is used to "disable" the used parameters, when in "named mode" and there are more unused parameters.
                    // See NamedParameter3.kt test. Disabling them gives a visual cue that they are already used.
                    signatureParts.add(SignaturePart.Parameter("", isHighlighted = true))
                    isDisabledBeforeHighlight = true
                    wasParameterHighlighted = true
                }
                signatureParts.add(SignaturePart.Text(" "))
            }

            val surroundInBrackets = isNamed || namedMode
            val parameterText = buildString {
                if (surroundInBrackets) {
                    append("[")
                }
                append(parameterIndexToText[parameterIndex])
                if (surroundInBrackets) {
                    append("]")
                }
            }
            if (shouldHighlight) {
                wasParameterHighlighted = true
            }
            signatureParts.add(SignaturePart.Parameter(parameterText, shouldHighlight))
        }

        if (valueArguments != null) {
            for (valueArgument in valueArguments) {
                val parameterIndex = argumentToParameterIndex[valueArgument.getArgumentExpression()]
                if (valueArgument == firstArgumentInNamedMode) {
                    namedMode = true
                }

                if (parameterIndex == null) {
                    hasUnmappedArgument = true
                    if (argumentIndex < currentArgumentIndex) {
                        hasUnmappedArgumentBeforeCurrent = true
                    }
                    argumentIndex++
                    continue
                }
                lastMappedArgumentIndex = argumentIndex
                if (!usedParameterIndices.add(parameterIndex)) continue

                val shouldHighlight = parameterIndex == highlightParameterIndex
                appendParameter(parameterIndex, shouldHighlight, valueArgument.isNamed())
            }
        } else {
            // This is for array get/set calls which don't have KtValueArguments.
            for (argument in arguments) {
                val parameterIndex = argumentToParameterIndex[argument]
                if (parameterIndex == null) {
                    hasUnmappedArgument = true
                    if (argumentIndex < currentArgumentIndex) {
                        hasUnmappedArgumentBeforeCurrent = true
                    }
                    argumentIndex++
                    continue
                }
                lastMappedArgumentIndex = argumentIndex
                if (!usedParameterIndices.add(parameterIndex)) continue

                val shouldHighlight = parameterIndex == highlightParameterIndex
                appendParameter(parameterIndex, shouldHighlight)
            }
        }

        for (parameterIndex in 0 until valueParameterCount) {
            if (parameterIndex !in usedParameterIndices) {
                // Highlight the first unused parameter if it is in the correct position
                val shouldHighlight = !namedMode && !wasParameterHighlighted
                appendParameter(
                    parameterIndex,
                    shouldHighlight,
                    markUsedUnusedParameterBorder = namedMode && !wasParameterHighlighted
                )
            }
        }

        if (appendNoParametersMessage && signatureParts.isEmpty()) {
            signatureParts.add(SignaturePart.Text(CodeInsightBundle.message("parameter.info.no.parameters")))
        }


        // Disabled when there are too many arguments.
        val allParametersUsed = usedParameterIndices.size == valueParameterCount
        val supportsTrailingCommas = callElement.languageVersionSettings.supportsFeature(LanguageFeature.TrailingCommas)
        val afterTrailingComma = arguments.isNotEmpty() && currentArgumentIndex == arguments.size
        val isInPositionToEnterArgument = !supportsTrailingCommas && afterTrailingComma
        val isAfterMappedArgs = currentArgumentIndex > lastMappedArgumentIndex
        val tooManyArgs = allParametersUsed && (isInPositionToEnterArgument || hasUnmappedArgument) && (isAfterMappedArgs || namedMode)

        val isDisabled = tooManyArgs || hasTypeMismatchBeforeCurrent || hasUnmappedArgumentBeforeCurrent

        return UiModel(
            SignatureModel(signatureParts),
            isDisabled,
            isDisabledBeforeHighlight,
        )
    }
    

    data class CallInfo(
        val target: PsiElement?,
        val callElement: KtElement,
        val valueArguments: List<KtValueArgument>?,
        val firstArgumentInNamedMode: KtValueArgument?,
        val arguments: List<KtExpression?>,
        val argumentToParameterIndex: Map<KtExpression, Int>,
        val valueParameterCount: Int,
        val parameterIndexToText: Map<Int, String>,
        val shouldHighlightGreen: Boolean,
        val hasTypeMismatchBeforeCurrent: Boolean,
        val highlightParameterIndex: Int?,
        val isDeprecated: Boolean,
        val representation: CallStringRepresentation,
    )

    data class CallStringRepresentation(
        val beforeParameters: String,
        val afterParameters: String,
    )

    data class CandidateInfo(
        var callInfo: CallInfo? = null  // Populated in updateParameterInfo()
    )
}
