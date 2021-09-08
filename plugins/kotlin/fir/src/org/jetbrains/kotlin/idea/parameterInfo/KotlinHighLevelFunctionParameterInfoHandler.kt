// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.parameterInfo

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithTabActionSupport
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.frontend.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.frontend.api.analyse
import org.jetbrains.kotlin.idea.frontend.api.components.KtTypeRendererOptions
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.pointers.KtSymbolPointer
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
            // TODO: FE 1.0 plugin collects all candidates (i.e., all overloads), even if arguments do not match. Not just resolved call.
            // See Call.resolveCandidates() in core/src/org/jetbrains/kotlin/idea/core/Utils.kt. Note `replaceCollectAllCandidates(true)`.

            val resolvedCall = when (callElement) {
                is KtCallElement -> callElement.resolveCall()
                is KtArrayAccessExpression -> callElement.resolveCall()
                else -> return null
            }

            val candidates = resolvedCall?.targetFunction?.candidates ?: return null
            context.itemsToShow = candidates.map { CandidateInfo(it.createPointer(), it.deprecationStatus != null) }.toTypedArray()

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
            val (resolvedCall, valueArguments, arguments) = when (callElement) {
                is KtCallElement -> {
                    val valueArguments = callElement.valueArgumentList?.arguments
                    Triple(callElement.resolveCall(), valueArguments, valueArguments?.map { it.getArgumentExpression() } ?: listOf())
                }
                is KtArrayAccessExpression -> Triple(callElement.resolveCall(), null, callElement.indexExpressions)
                else -> return@analyse
            }
            val candidates = resolvedCall?.targetFunction?.candidates ?: return@analyse

            for (objectToView in context.objectsToView) {
                val candidateInfo = objectToView as? CandidateInfo ?: continue
                // Find candidate matching the one in CandidateInfo
                // TODO: restoreSymbol does not work for static members (see Nullability.kt test),
                // functional values (see FunctionalValue*.kt tests), and Java functions (see useJava*FromLib.kt tests).
                // HL API gets a different symbol because a new ScopeSession() is used, and therefore a new enhanced FirFunction is created.
                val candidateToMatch = candidateInfo.candidate.restoreSymbol() ?: return@analyse
                val candidate = candidates.firstOrNull { it == candidateToMatch } ?: return@analyse

                // For array set calls, we only want the index arguments in brackets, which are all except the last (the value to set).
                val isArraySetCall = candidate.callableIdIfNonLocal?.let {
                    val isSet = it.callableName == OperatorNameConventions.SET
                    isSet && callElement is KtArrayAccessExpression
                } ?: false
                // TODO: Get substituted value parameters. Not currently available in HL API. See SubstituteFromArguments*.kt tests
                val valueParameters = candidate.valueParameters.let { if (isArraySetCall) it.dropLast(1) else it }
                val setValueParameter = if (isArraySetCall) candidate.valueParameters.last() else null

                // TODO: When resolvedCall is KtFunctionalTypeVariableCall, the candidate is FunctionN.invoke() and parameter names are "p1", "p2", etc.
                // We need to get the type of the target variable, and retrieve the parameter names from the type (KtFunctionalType).
                // The names need to be added to KtFunctionalType (currently only types are there) and populated in KtSymbolByFirBuilder.TypeBuilder.

                val parameterToIndex = buildMap<KtValueParameterSymbol, Int> {
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

                // TODO: The argument mapping should also be per-candidate once we have all candidates available.
                val argumentMapping = resolvedCall.argumentMapping
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
                val isCallResolvedToCandidate = candidates.size == 1

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

    private fun KtAnalysisSession.renderParameter(parameter: KtValueParameterSymbol, includeName: Boolean): String {
        return buildString {
            val annotationFqNames =
                parameter.annotations
                    .filter {
                        // For primary constructor parameters, the annotation use site must be "param" or unspecified.
                        it.useSiteTarget == null || it.useSiteTarget == AnnotationUseSiteTarget.CONSTRUCTOR_PARAMETER
                    }
                    .mapNotNull { it.classId?.asSingleFqName() }
                    .filter { it !in NULLABILITY_ANNOTATIONS }
            annotationFqNames.forEach { append("@${it.shortName().asString()} ") }

            if (parameter.isVararg) {
                append("vararg ")
            }

            if (includeName) {
                append(parameter.name)
                append(": ")
            }

            append(parameter.annotatedType.type.render(KtTypeRendererOptions.SHORT_NAMES))

            if (parameter.hasDefaultValue) {
                // TODO: append(" = " + defaultValue).
                // HL API currently doesn't give actual default value.
            }
        }
    }

    private fun calculateHighlightParameterIndex(
        arguments: List<KtExpression>,
        currentArgumentIndex: Int,
        argumentToParameterIndex: LinkedHashMap<KtExpression, Int>,
        argumentMapping: LinkedHashMap<KtExpression, KtValueParameterSymbol>,
        parameterToIndex: Map<KtValueParameterSymbol, Int>
    ): Int? {
        val afterTrailingComma = arguments.isNotEmpty() && currentArgumentIndex == arguments.size
        val highlightParameterIndex = when {
            currentArgumentIndex < arguments.size -> argumentToParameterIndex[arguments[currentArgumentIndex]]
            afterTrailingComma -> {
                // If last argument is for a vararg parameter, then the argument about to be entered at the cursor should also be
                // for that same vararg parameter.
                val parameterForLastArgument = argumentMapping[arguments.last()]
                if (parameterForLastArgument?.isVararg == true) {
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
        argumentMapping: LinkedHashMap<KtExpression, KtValueParameterSymbol>,
        setValueParameter: KtValueParameterSymbol?
    ): Boolean {
        for ((index, argument) in arguments.withIndex()) {
            if (index >= currentArgumentIndex) break
            val parameterForArgument = argumentMapping[argument] ?: continue
            if (parameterForArgument == setValueParameter) continue

            val argumentType = argument.getKtType() ?: error("Argument should have a KtType")
            val parameterType = parameterForArgument.annotatedType.type
            if (argumentType.isNotSubTypeOf(parameterType)) {
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
     * 3. Substituted types and default values should be rendered.
     *
     * The logic for surrounding parameters in brackets (e.g., "[x: Int], ...") is as follows:
     * 1. When an argument is named, the parameter for that argument is surrounded in brackets.
     * 2. When the named argument is NOT in its own position, then its corresponding parameter and all parameters after that are
     *    surrounded in brackets (i.e., `namedMode = true` in the code below). This is because all arguments must be named after it.
     *    See NamedParameter4.kt test.
     * 3. When the named argument IS in its own position, and LanguageFeature.MixedNamedArgumentsInTheirOwnPosition is DISABLED, then
     *    `namedMode = true` as described above. See MixedNamedArguments2.kt test.
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
                            // TODO: This matches FE 1.0 plugin behavior, but consider removing "disable before highlight".
                            // It's odd that we disable the used parameters, even though they might match. See NamedParameter3.kt test:
                            // `y = false` matches, and we disable it even though the next argument could match too (e.g., `x = `).

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
                        if (parameterIndex == null) {
                            hasUnmappedArgument = true
                            if (argumentIndex < currentArgumentIndex) {
                                hasUnmappedArgumentBeforeCurrent = true
                            }
                            argumentIndex++
                            continue
                        }
                        if (!usedParameterIndices.add(parameterIndex)) continue

                        if (valueArgument.isNamed() &&
                            !(supportsMixedNamedArgumentsInTheirOwnPosition && parameterIndex == argumentIndex)
                        ) {
                            // "Named mode" (all arguments should be named) begins when there is a named argument NOT in their own position
                            // or named arguments in their own position is not supported.
                            namedMode = true
                        }

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
                        if (argumentIndex != parameterIndex) {
                            namedMode = true
                        }
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
            val strikeout = itemToShow.isDeprecated

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
                strikeout,
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
    )

    data class CandidateInfo(
        val candidate: KtSymbolPointer<KtFunctionLikeSymbol>,
        val isDeprecated: Boolean,
        var callInfo: CallInfo? = null  // Populated in updateParameterInfo()
    )
}