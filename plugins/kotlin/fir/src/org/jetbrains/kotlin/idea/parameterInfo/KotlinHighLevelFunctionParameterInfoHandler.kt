// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.parameterInfo

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithTabActionSupport
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyse
import org.jetbrains.kotlin.analysis.api.annotations.annotations
import org.jetbrains.kotlin.analysis.api.components.KtTypeRendererOptions
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtVariableLikeSignature
import org.jetbrains.kotlin.analysis.api.types.KtClassErrorType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.NULLABILITY_ANNOTATIONS
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.utils.checkWithAttachment
import java.awt.Color
import kotlin.reflect.KClass

class KotlinHighLevelFunctionParameterInfoHandler :
    KotlinHighLevelParameterInfoWithCallHandlerBase<KtValueArgumentList, KtValueArgument>(
        KtValueArgumentList::class, KtValueArgument::class
    ) {
    override fun getActualParameters(arguments: KtValueArgumentList) = arguments.arguments.toTypedArray()

    override fun getActualParametersRBraceType(): KtSingleValueToken = KtTokens.RPAR

    override fun getArgumentListAllowedParentClasses() = setOf(KtCallElement::class.java)
}

class KotlinHighLevelLambdaParameterInfoHandler :
    KotlinHighLevelParameterInfoWithCallHandlerBase<KtLambdaArgument, KtLambdaArgument>(KtLambdaArgument::class, KtLambdaArgument::class) {

    override fun getActualParameters(lambdaArgument: KtLambdaArgument) = arrayOf(lambdaArgument)

    override fun getActualParametersRBraceType(): KtSingleValueToken = KtTokens.RBRACE

    override fun getArgumentListAllowedParentClasses() = setOf(KtLambdaArgument::class.java)

    override fun getCurrentArgumentIndex(context: UpdateParameterInfoContext, argumentList: KtLambdaArgument): Int {
        val size = (argumentList.parent as? KtCallElement)?.valueArguments?.size ?: 1
        return size - 1
    }
}

class KotlinHighLevelArrayAccessParameterInfoHandler :
    KotlinHighLevelParameterInfoWithCallHandlerBase<KtContainerNode, KtExpression>(KtContainerNode::class, KtExpression::class) {

    override fun getArgumentListAllowedParentClasses() = setOf(KtArrayAccessExpression::class.java)

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
    }

    override fun getActualParameterDelimiterType(): KtSingleValueToken = KtTokens.COMMA

    override fun getArgListStopSearchClasses(): Set<Class<out KtElement>> = STOP_SEARCH_CLASSES

    override fun getArgumentListClass() = argumentListClass.java

    override fun showParameterInfo(element: TArgumentList, context: CreateParameterInfoContext) {
        context.showHint(element, element.textRange.startOffset, this)
    }

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): TArgumentList? {
        val file = context.file as? KtFile ?: return null

        val token = file.findElementAt(context.offset) ?: return null
        val argumentList = PsiTreeUtil.getParentOfType(token, argumentListClass.java, true, *STOP_SEARCH_CLASSES.toTypedArray())
            ?: return null

        val callElement = argumentList.parent as? KtElement ?: return null
        return analyse(callElement) {
            // findElementForParameterInfo() is only called once before the UI is shown. updateParameterInfo() is called once before the UI
            // is shown, and is also called every time the cursor moves or the call arguments change (i.e., user types something) while the
            // UI is shown, hence the need to resolve the call again in updateParameterInfo() (e.g., argument mappings can change).
            //
            // However, the candidates in findElementForParameterInfo() and updateParameterInfo() are the exact same because the name of the
            // function call CANNOT change while the UI is shown. Because of how ParameterInfoHandler works, we have to store _something_ in
            // `context.itemsToShow` array which becomes `context.objectsToView` array in updateParameterInfo(). Unfortunately
            // `objectsToView` is read-only so we can't change the size of the array. So we have to store an array here of the correct size,
            // which does mean we have to resolve here to know the number of candidates.
            val candidatesWithMapping = resolveCallCandidates(callElement)
            context.itemsToShow = Array(candidatesWithMapping.size) { CandidateInfo() }

            argumentList
        }
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): TArgumentList? {
        val element = context.file.findElementAt(context.offset) ?: return null
        return PsiTreeUtil.getParentOfType(element, argumentListClass.java)
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun updateParameterInfo(argumentList: TArgumentList, context: UpdateParameterInfoContext) {
        if (context.parameterOwner !== argumentList) {
            context.removeHint()
        }
        val currentArgumentIndex = getCurrentArgumentIndex(context, argumentList)
        context.setCurrentParameter(currentArgumentIndex)

        val callElement = argumentList.parent as? KtElement ?: return
        analyse(callElement) {
            val (valueArguments, arguments) = when (callElement) {
                is KtCallElement -> {
                    val valueArguments = callElement.valueArgumentList?.arguments
                    Pair(valueArguments, valueArguments?.map { it.getArgumentExpression() } ?: listOf())
                }
                is KtArrayAccessExpression -> Pair(null, callElement.indexExpressions)
                else -> return@analyse
            }
            val candidatesWithMapping = resolveCallCandidates(callElement)

            for ((index, objectToView) in context.objectsToView.withIndex()) {
                val candidateInfo = objectToView as? CandidateInfo ?: continue

                if (index >= candidatesWithMapping.size) {
                    // Number of candidates somehow changed while UI is shown, which should NOT be possible. Bail out to be safe.
                    return
                }
                val (candidateSignature, argumentMapping) = candidatesWithMapping[index]

                // For array set calls, we only want the index arguments in brackets, which are all except the last (the value to set).
                val isArraySetCall = candidateSignature.symbol.callableIdIfNonLocal?.let {
                    val isSet = it.callableName == OperatorNameConventions.SET
                    isSet && callElement is KtArrayAccessExpression
                } ?: false
                val valueParameters = candidateSignature.valueParameters.let { if (isArraySetCall) it.dropLast(1) else it }
                val setValueParameter = if (isArraySetCall) candidateSignature.valueParameters.last() else null

                // TODO: When resolvedCall is KtFunctionalTypeVariableCall, the candidate is FunctionN.invoke() and parameter names are "p1", "p2", etc.
                // We need to get the type of the target variable, and retrieve the parameter names from the type (KtFunctionalType).
                // The names need to be added to KtFunctionalType (currently only types are there) and populated in KtSymbolByFirBuilder.TypeBuilder.

                val parameterToIndex = buildMap<KtVariableLikeSignature<KtValueParameterSymbol>, Int> {
                    valueParameters.forEachIndexed { index, parameter -> put(parameter, index) }
                }

                val parameterIndexToText = buildMap<Int, String> {
                    valueParameters.forEachIndexed { index, parameter ->
                        // TODO: Add hasSynthesizedParameterNames to HL API.
                        // See resolveValueParameters() in core/descriptors.jvm/src/org/jetbrains/kotlin/load/java/lazy/descriptors/LazyJavaScope.kt
                        val hasSynthesizedParameterNames = false
                        val parameterText = renderParameter(parameter, includeName = !hasSynthesizedParameterNames)
                        put(index, parameterText)
                    }
                }

                val argumentToParameterIndex = LinkedHashMap<KtExpression, Int>(argumentMapping.size).apply {
                    for ((argumentExpression, parameterForArgument) in argumentMapping) {
                        if (parameterForArgument == setValueParameter) continue
                        put(argumentExpression, parameterToIndex.getValue(parameterForArgument))
                    }
                }

                // Determine the parameter to be highlighted.
                val highlightParameterIndex = calculateHighlightParameterIndex(
                    arguments,
                    currentArgumentIndex,
                    argumentToParameterIndex,
                    argumentMapping,
                    parameterToIndex
                )

                val hasTypeMismatchBeforeCurrent = calculateHasTypeMismatchBeforeCurrent(
                    arguments,
                    currentArgumentIndex,
                    argumentMapping,
                    setValueParameter
                )

                // TODO: This should be changed when there are multiple candidates available; need to know which one the call is resolved to
                val isCallResolvedToCandidate = candidatesWithMapping.size == 1

                candidateInfo.callInfo = CallInfo(
                    callElement,
                    valueArguments,
                    arguments,
                    argumentToParameterIndex,
                    valueParameters.size,
                    parameterIndexToText,
                    isCallResolvedToCandidate,
                    hasTypeMismatchBeforeCurrent,
                    highlightParameterIndex,
                    candidateSignature.symbol.deprecationStatus != null,
                )
            }
        }
    }

    protected open fun getCurrentArgumentIndex(context: UpdateParameterInfoContext, argumentList: TArgumentList): Int {
        val offset = context.offset
        return argumentList.allChildren
            .takeWhile { it.startOffset < offset }
            .count { it.node.elementType == KtTokens.COMMA }
    }

    private fun KtAnalysisSession.renderParameter(
        parameter: KtVariableLikeSignature<KtValueParameterSymbol>,
        includeName: Boolean
    ): String {
        return buildString {
            val annotationFqNames =
                parameter.symbol.annotations
                    .filter {
                        // For primary constructor parameters, the annotation use site must be "param" or unspecified.
                        it.useSiteTarget == null || it.useSiteTarget == AnnotationUseSiteTarget.CONSTRUCTOR_PARAMETER
                    }
                    .mapNotNull { it.classId?.asSingleFqName() }
                    .filter { it !in NULLABILITY_ANNOTATIONS }
            annotationFqNames.forEach { append("@${it.shortName().asString()} ") }

            if (parameter.symbol.isVararg) {
                append("vararg ")
            }

            if (includeName) {
                append(parameter.symbol.name)
                append(": ")
            }

            val returnType = parameter.returnType.takeUnless { it is KtClassErrorType } ?: parameter.symbol.returnType
            append(returnType.render(KtTypeRendererOptions.SHORT_NAMES))

            if (parameter.symbol.hasDefaultValue) {
                // TODO: append(" = " + defaultValue).
                // HL API currently doesn't give actual default value.
            }
        }
    }

    private fun calculateHighlightParameterIndex(
        arguments: List<KtExpression>,
        currentArgumentIndex: Int,
        argumentToParameterIndex: LinkedHashMap<KtExpression, Int>,
        argumentMapping: LinkedHashMap<KtExpression, KtVariableLikeSignature<KtValueParameterSymbol>>,
        parameterToIndex: Map<KtVariableLikeSignature<KtValueParameterSymbol>, Int>
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

    private fun KtAnalysisSession.calculateHasTypeMismatchBeforeCurrent(
        arguments: List<KtExpression>,
        currentArgumentIndex: Int,
        argumentMapping: LinkedHashMap<KtExpression, KtVariableLikeSignature<KtValueParameterSymbol>>,
        setValueParameter: KtVariableLikeSignature<KtValueParameterSymbol>?
    ): Boolean {
        for ((index, argument) in arguments.withIndex()) {
            if (index >= currentArgumentIndex) break
            val parameterForArgument = argumentMapping[argument] ?: continue
            if (parameterForArgument == setValueParameter) continue

            val argumentType = argument.getKtType() ?: error("Argument should have a KtType")
            if (argumentType.isNotSubTypeOf(parameterForArgument.returnType)) {
                return true
            }
        }
        return false
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

        val currentArgumentIndex = context.currentParameterIndex
        if (currentArgumentIndex < 0) return false

        val callInfo = itemToShow.callInfo ?: return false
        with(callInfo) {
            val supportsMixedNamedArgumentsInTheirOwnPosition =
                callElement.languageVersionSettings.supportsFeature(LanguageFeature.MixedNamedArgumentsInTheirOwnPosition)

            // TODO: This matches FE 1.0 plugin behavior. Consider just returning false here
            checkWithAttachment(
                arguments.size >= currentArgumentIndex,
                lazyMessage = {
                    "currentArgumentIndex: $currentArgumentIndex has to be not more than number of arguments ${arguments.size}"
                },
                attachments = {
                    it.withAttachment("file.kt", callElement.containingFile.text)
                    it.withAttachment("info.txt", itemToShow)
                }
            )

            var highlightStartOffset = -1
            var highlightEndOffset = -1
            var isDisabledBeforeHighlight = false
            var hasUnmappedArgument = false
            var hasUnmappedArgumentBeforeCurrent = false
            val usedParameterIndices = HashSet<Int>()
            val text = buildString {
                var namedMode = false
                var argumentIndex = 0

                fun appendParameter(
                    parameterIndex: Int,
                    shouldHighlight: Boolean = false,
                    isNamed: Boolean = false,
                    markUsedUnusedParameterBorder: Boolean = false
                ) {
                    argumentIndex++

                    if (length > 0) {
                        append(", ")
                        if (markUsedUnusedParameterBorder) {
                            // This is used to "disable" the used parameters, when in "named mode" and there are more unused parameters.
                            // See NamedParameter3.kt test. Disabling them gives a visual cue that they are already used.

                            // Highlight the space after the comma; highlighted text needs to be at least one character long
                            highlightStartOffset = length - 1
                            highlightEndOffset = length
                            isDisabledBeforeHighlight = true
                        }
                    }

                    if (shouldHighlight) {
                        highlightStartOffset = length
                    }

                    val surroundInBrackets = isNamed || namedMode
                    if (surroundInBrackets) {
                        append("[")
                    }
                    append(parameterIndexToText[parameterIndex])
                    if (surroundInBrackets) {
                        append("]")
                    }

                    if (shouldHighlight) {
                        highlightEndOffset = length
                    }
                }

                if (valueArguments != null) {
                    for (valueArgument in valueArguments) {
                        val parameterIndex = argumentToParameterIndex[valueArgument.getArgumentExpression()]
                        if (valueArgument.isNamed() &&
                            !(supportsMixedNamedArgumentsInTheirOwnPosition && parameterIndex != null && parameterIndex == argumentIndex)
                        ) {
                            // "Named mode" (all arguments should be named) begins when there is a named argument NOT in their own position
                            // or a named argument is unmapped (i.e., non-existent name),
                            // or named arguments in their own position is not supported.
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
                            if (argumentIndex <= currentArgumentIndex) {
                                hasUnmappedArgumentBeforeCurrent = true
                            }
                            argumentIndex++
                            continue
                        }
                        if (!usedParameterIndices.add(parameterIndex)) continue

                        val shouldHighlight = parameterIndex == highlightParameterIndex
                        appendParameter(parameterIndex, shouldHighlight)
                    }
                }

                for (parameterIndex in 0 until valueParameterCount) {
                    if (parameterIndex !in usedParameterIndices) {
                        // Highlight the first unused parameter if it is in the correct position
                        val shouldHighlight = !namedMode && highlightStartOffset == -1
                        appendParameter(
                            parameterIndex,
                            shouldHighlight,
                            markUsedUnusedParameterBorder = namedMode && highlightStartOffset == -1
                        )
                    }
                }

                if (length == 0) {
                    append(CodeInsightBundle.message("parameter.info.no.parameters"))
                }
            }

            val backgroundColor = if (isCallResolvedToCandidate) GREEN_BACKGROUND else context.defaultParameterColor

            // Disabled when there are too many arguments.
            val allParametersUsed = usedParameterIndices.size == valueParameterCount
            val supportsTrailingCommas = callElement.languageVersionSettings.supportsFeature(LanguageFeature.TrailingCommas)
            val afterTrailingComma = arguments.isNotEmpty() && currentArgumentIndex == arguments.size
            val isInPositionToEnterArgument = !supportsTrailingCommas && afterTrailingComma
            val tooManyArgs = allParametersUsed && (isInPositionToEnterArgument || hasUnmappedArgument)

            val isDisabled = tooManyArgs || hasTypeMismatchBeforeCurrent || hasUnmappedArgumentBeforeCurrent

            context.setupUIComponentPresentation(
                text,
                highlightStartOffset,
                highlightEndOffset,
                isDisabled,
                /*strikeout=*/ isDeprecated,
                isDisabledBeforeHighlight,
                backgroundColor
            )
        }

        return true
    }

    data class CallInfo(
        val callElement: KtElement,
        val valueArguments: List<KtValueArgument>?,
        val arguments: List<KtExpression>,
        val argumentToParameterIndex: LinkedHashMap<KtExpression, Int>,
        val valueParameterCount: Int,
        val parameterIndexToText: Map<Int, String>,
        val isCallResolvedToCandidate: Boolean,
        val hasTypeMismatchBeforeCurrent: Boolean,
        val highlightParameterIndex: Int?,
        val isDeprecated: Boolean,
    )

    data class CandidateInfo(
        var callInfo: CallInfo? = null  // Populated in updateParameterInfo()
    )
}