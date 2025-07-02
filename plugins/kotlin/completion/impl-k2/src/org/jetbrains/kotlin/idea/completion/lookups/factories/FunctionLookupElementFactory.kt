// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.lookups.factories

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.endOffset
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.analysis.api.utils.findSamSymbolOrNull
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isPossiblySubTypeOf
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.idea.base.analysis.withRootPrefixIfNeeded
import org.jetbrains.kotlin.idea.completion.acceptOpeningBrace
import org.jetbrains.kotlin.idea.base.serialization.names.KotlinNameSerializer
import org.jetbrains.kotlin.idea.completion.contributors.helpers.insertString
import org.jetbrains.kotlin.idea.completion.contributors.helpers.insertStringAndInvokeCompletion
import org.jetbrains.kotlin.idea.completion.handlers.isCharAt
import org.jetbrains.kotlin.idea.completion.impl.k2.handlers.TrailingLambdaInsertionHandler
import org.jetbrains.kotlin.idea.completion.impl.k2.weighers.TrailingLambdaWeigher.hasTrailingLambda
import org.jetbrains.kotlin.idea.completion.lookups.*
import org.jetbrains.kotlin.idea.completion.lookups.factories.FunctionCallLookupObject.Companion.hasReceiver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.renderer.render

internal object FunctionLookupElementFactory {

    context(KaSession)
    fun createLookup(
        shortName: Name,
        signature: KaFunctionSignature<*>,
        options: CallableInsertionOptions,
        expectedType: KaType? = null,
        aliasName: Name? = null,
    ): LookupElementBuilder {
        val valueParameters = signature.valueParameters

        val symbol = signature.symbol
        val lookupObject = FunctionCallLookupObject(
            shortName = aliasName ?: shortName,
            options = options,
            renderedDeclaration = CompletionShortNamesRenderer.renderFunctionParameters(valueParameters),
            hasReceiver = signature.hasReceiver,
            inputValueArgumentsAreRequired = valueParameters.isNotEmpty(),
            inputTypeArgumentsAreRequired = !FunctionInsertionHelper.functionCanBeCalledWithoutExplicitTypeArguments(symbol, expectedType),
        )

        return createLookupElement(signature, lookupObject, useFqNameInTailText = aliasName != null)
    }

    context(KaSession)
    internal fun getTrailingFunctionSignature(
        signature: KaFunctionSignature<*>,
        checkDefaultValues: Boolean = true,
    ): KaVariableSignature<KaValueParameterSymbol>? {
        val valueParameters = signature.valueParameters
        if (checkDefaultValues &&
            !valueParameters.dropLast(1).all { it.symbol.hasDefaultValue }
        ) return null

        return valueParameters.lastOrNull()
            ?.takeUnless { it.symbol.isVararg }
    }

    context(KaSession)
    internal fun createTrailingFunctionDescriptor(
        trailingFunctionSignature: KaVariableSignature<KaValueParameterSymbol>,
    ): TrailingFunctionDescriptor? {
        return when (val type = trailingFunctionSignature.returnType.lowerBoundIfFlexible()) {
            is KaFunctionType -> TrailingFunctionDescriptor.Function(type)
            is KaUsualClassType -> {
                val functionClassSymbol = type.symbol as? KaNamedClassSymbol
                    ?: return null

                val samConstructor = functionClassSymbol.samConstructor
                    ?: return null

                val samConstructorType = samConstructor.valueParameters
                    .singleOrNull()
                    ?.returnType
                    ?.let { it as? KaFunctionType }
                    ?: return null

                val mappings = samConstructor.typeParameters
                        .zip(type.typeArguments.mapNotNull { it.type })
                        .toMap()

                val functionType = (@OptIn(KaExperimentalApi::class) createSubstitutor(mappings)
                    .substitute(samConstructorType)) as? KaFunctionType
                    ?: return null

                val samSymbol = functionClassSymbol.findSamSymbolOrNull()
                    ?: return null

                TrailingFunctionDescriptor.SamConstructor(functionType, samSymbol)
            }

            else -> null
        }
    }

    @OptIn(KaExperimentalApi::class)
    context(KaSession)
    @ApiStatus.Experimental
    fun createLookupWithTrailingLambda(
        shortName: Name,
        signature: KaFunctionSignature<*>,
        options: CallableInsertionOptions,
        aliasName: Name?,
    ): LookupElementBuilder? {
        val trailingFunctionSignature = getTrailingFunctionSignature(signature)
            ?: return null

        val trailingFunctionType = createTrailingFunctionDescriptor(trailingFunctionSignature)
            ?.functionType
            ?: return null

        val lookupObject = FunctionCallLookupObject(
            shortName = aliasName ?: shortName,
            options = options,
            renderedDeclaration = CompletionShortNamesRenderer.renderTrailingFunction(trailingFunctionSignature, trailingFunctionType),
            hasReceiver = signature.hasReceiver,
            inputTrailingLambdaIsRequired = true,
        )
        createLookupElement(signature, lookupObject, useFqNameInTailText = aliasName != null).apply {
            hasTrailingLambda = true
            if (trailingFunctionType.parameters.size > 1) {
                TrailingLambdaInsertionHandler.create(trailingFunctionType)?.let {
                    return withInsertHandler(it)
                }
            }
            return this
        }
    }

    context(KaSession)
    private fun createLookupElement(
        signature: KaFunctionSignature<*>,
        lookupObject: FunctionCallLookupObject,
        useFqNameInTailText: Boolean = false,
    ): LookupElementBuilder = LookupElementBuilder.create(
        /* lookupObject = */ lookupObject,
        /* lookupString = */ lookupObject.shortName.asString(),
    ).appendTailText(lookupObject.renderedDeclaration, true)
        .appendTailText(TailTextProvider.getTailText(signature, useFqName = useFqNameInTailText), true)
        .let { withCallableSignatureInfo(signature, it) }
        .also { it.acceptOpeningBrace = true }
        .let { builder ->
            val options = lookupObject.options
            updateLookupElementBuilder(options, builder, options.insertionStrategy)
        }

    private fun updateLookupElementBuilder(
        options: CallableInsertionOptions,
        builder: LookupElementBuilder,
        insertionStrategy: CallableInsertionStrategy = options.insertionStrategy
    ): LookupElementBuilder {
        return when (insertionStrategy) {
            CallableInsertionStrategy.AsCall -> builder.withInsertHandler(FunctionInsertionHandler)
            CallableInsertionStrategy.AsIdentifier -> builder.withInsertHandler(CallableIdentifierInsertionHandler())
            is CallableInsertionStrategy.InfixCallableInsertionStrategy -> builder.withInsertHandler(AsIdentifierCustomInsertionHandler)

            is CallableInsertionStrategy.WithCallArgs -> {
                val argString = insertionStrategy.args.joinToString(", ", prefix = "(", postfix = ")")
                builder.withInsertHandler(
                    WithCallArgsInsertionHandler(argString)
                ).withTailText(argString, false)
            }

            is CallableInsertionStrategy.WithSuperDisambiguation -> {
                val resultBuilder = updateLookupElementBuilder(options, builder, insertionStrategy.subStrategy)
                updateLookupElementBuilderToInsertTypeQualifierOnSuper(resultBuilder, insertionStrategy)
            }
        }
    }
}

@Serializable
internal object AsIdentifierCustomInsertionHandler : CallableIdentifierInsertionHandler() {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        super.handleInsert(context, item)
        if (context.completionChar == ' ') {
            context.setAddCompletionChar(false)
        }

        context.insertStringAndInvokeCompletion(" ")
    }
}

@Serializable
internal data class WithCallArgsInsertionHandler(
    val argString: String,
): QuotedNamesAwareInsertionHandler() {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        super.handleInsert(context, item)
        context.insertString(argString)
    }
}

object FunctionInsertionHelper {
    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    fun functionCanBeCalledWithoutExplicitTypeArguments(
        symbol: KaFunctionSymbol,
        expectedType: KaType?
    ): Boolean {
        if (symbol.typeParameters.isEmpty()) return true

        val typeParametersToInfer = symbol.typeParameters.toSet()
        val potentiallyInferredTypeParameters = mutableSetOf<KaTypeParameterSymbol>()

        /**
         * Collects type arguments of [type] (or type itself in case of [KaTypeParameterType]), which are probably will be inferred.
         *
         * @param onlyCollectReturnTypeOfFunctionalType if true, then only the return type of functional type is considered inferred.
         * For example, in the following case:
         * ```
         * fun <T1, T2> T1.foo(handler: (T2) -> Boolean) {}
         *
         * fun f() {
         *     "".foo<String> { <caret> }
         * }
         * ```
         * we can't rely on the inference from `handler`, because lambda input types may not be declared explicitly.
         */
        fun collectPotentiallyInferredTypes(type: KaType, onlyCollectReturnTypeOfFunctionalType: Boolean) {
            when (type) {
                is KaTypeParameterType -> {
                    val typeParameterSymbol = type.symbol
                    if (typeParameterSymbol !in typeParametersToInfer || typeParameterSymbol in potentiallyInferredTypeParameters) return

                    potentiallyInferredTypeParameters.add(type.symbol)
                    // Add type parameters possibly inferred by type arguments of parameter's upper-bound
                    // e.g. <T, C: Iterable<T>>, so T is inferred from C
                    type.symbol.upperBounds
                        .filterIsInstance<KaClassType>()
                        .filter { it.typeArguments.isNotEmpty() }
                        .forEach { collectPotentiallyInferredTypes(it, onlyCollectReturnTypeOfFunctionalType = false) }
                }

                is KaFunctionType -> {
                    val typesToProcess = if (onlyCollectReturnTypeOfFunctionalType) {
                        // do not rely on inference from input of functional type - use only return type of functional type
                        listOf(type.returnType)
                    } else {
                        listOfNotNull(type.receiverType) + type.returnType + type.parameterTypes
                    }
                    typesToProcess.forEach { collectPotentiallyInferredTypes(it, onlyCollectReturnTypeOfFunctionalType) }
                }

                is KaUsualClassType -> {
                    val typeArguments = type.typeArguments.mapNotNull { it.type }
                    typeArguments.forEach { collectPotentiallyInferredTypes(it, onlyCollectReturnTypeOfFunctionalType) }
                }

                else -> {}
            }
        }


        symbol.receiverParameter?.returnType?.let { collectPotentiallyInferredTypes(it, onlyCollectReturnTypeOfFunctionalType = true) }
        symbol.valueParameters.forEach { collectPotentiallyInferredTypes(it.returnType, onlyCollectReturnTypeOfFunctionalType = true) }

        // check that there is an expected type and the return value of the function can potentially match it
        if (expectedType != null && symbol.returnType isPossiblySubTypeOf expectedType) {
            collectPotentiallyInferredTypes(symbol.returnType, onlyCollectReturnTypeOfFunctionalType = false)
        }

        return potentiallyInferredTypeParameters.containsAll(symbol.typeParameters)
    }
}

@Serializable
internal data class FunctionCallLookupObject(
    @Serializable(with = KotlinNameSerializer::class) override val shortName: Name,
    override val options: CallableInsertionOptions,
    override val renderedDeclaration: String,
    val hasReceiver: Boolean, // todo find a better solution
    val inputValueArgumentsAreRequired: Boolean = false,
    val inputTypeArgumentsAreRequired: Boolean = false,
    val inputTrailingLambdaIsRequired: Boolean = false,
) : KotlinCallableLookupObject() {

    companion object {

        val KaCallableSignature<*>.hasReceiver: Boolean
            get() = receiverType != null
                    || callableId?.classId != null
    }
}

@Serializable
internal object FunctionInsertionHandler : QuotedNamesAwareInsertionHandler() {

    private fun addArguments(context: InsertionContext, offsetElement: PsiElement, lookupObject: FunctionCallLookupObject) {
        val completionChar = context.completionChar
        if (completionChar == '(') { //TODO: more correct behavior related to braces type
            context.setAddCompletionChar(false)
        }

        var offset = context.tailOffset
        val document = context.document
        val editor = context.editor
        val project = context.project
        val chars = document.charsSequence

        val isSmartEnterCompletion = completionChar == Lookup.COMPLETE_STATEMENT_SELECT_CHAR
        val isReplaceCompletion = completionChar == Lookup.REPLACE_SELECT_CHAR
        val isNormalCompletion = completionChar == Lookup.NORMAL_SELECT_CHAR

        var insertTypeArguments = lookupObject.inputTypeArgumentsAreRequired &&
                (isNormalCompletion || isReplaceCompletion || isSmartEnterCompletion)

        val preferParentheses = completionChar == '(' || isReplaceCompletion && chars.isCharAt(offset, '(')
        val insertLambda = !preferParentheses && lookupObject.inputTrailingLambdaIsRequired
        val (openingBracket, closingBracket) = if (insertLambda) '{' to '}' else '(' to ')'

        val offset1 = chars.skipSpaces(offset)
        var skipParentheses = false
        if (offset1 < chars.length) {
            if (chars[offset1] == '<') {
                val token = context.file.findElementAt(offset1)!!
                if (token.node.elementType == KtTokens.LT) {
                    val parent = token.parent
                    /* if type argument list is on multiple lines this is more likely wrong parsing*/
                    if (parent is KtTypeArgumentList && parent.getText().indexOf('\n') < 0) {
                        if (isReplaceCompletion) {
                            offset = parent.endOffset
                        } else {
                            offset = offset1
                            skipParentheses = true
                        }
                        insertTypeArguments = false
                    }
                }
            }
        }

        val caretModel = editor.caretModel
        if (insertTypeArguments) {
            document.insertString(offset, "<>")
            caretModel.moveToOffset(offset + 1)
            offset += 2
        }

        var openingBracketOffset = chars.indexOfSkippingSpace(openingBracket, offset)
        var closeBracketOffset = openingBracketOffset?.let { chars.indexOfSkippingSpace(closingBracket, it + 1) }
        var inBracketsShift = 0

        if (openingBracketOffset == null) {
            val text = if (insertLambda) {
                if (completionChar == ' ' || completionChar == '{') {
                    context.setAddCompletionChar(false)
                }

                inBracketsShift = 1
                " {  }"
            } else if (!skipParentheses) {
                if (isSmartEnterCompletion) "("
                else "()"
            } else ""
            document.insertString(offset, text)
            context.commitDocument()

            openingBracketOffset = document.charsSequence.indexOfSkippingSpace(openingBracket, offset)
            closeBracketOffset = openingBracketOffset?.let {
                document.charsSequence.indexOfSkippingSpace(closingBracket, openingBracketOffset + 1)
            }
        }

        if (!insertTypeArguments) {
            if (shouldPlaceCaretInBrackets(completionChar, lookupObject) || closeBracketOffset == null) {
                val additionalOffset = if (insertLambda) 2 else 1
                if (openingBracketOffset != null) {
                    caretModel.moveToOffset(openingBracketOffset + additionalOffset)
                }

                context.laterRunnable = if (insertLambda) Runnable {
                    CodeCompletionHandlerBase(
                        /* completionType = */ CompletionType.BASIC,
                        /* invokedExplicitly = */ false,
                        /* autopopup = */ true,
                        /* synchronous = */ false,
                    ).invokeCompletion(project, editor)
                } else Runnable {
                    AutoPopupController.getInstance(project)
                        .autoPopupParameterInfo(editor, offsetElement)
                }
            } else {
                caretModel.moveToOffset(closeBracketOffset + 1)
            }
        }
    }

    private fun shouldPlaceCaretInBrackets(completionChar: Char, lookupObject: FunctionCallLookupObject): Boolean {
        if (completionChar == ',' || completionChar == '.' || completionChar == '=') return false
        if (completionChar == '(') return true
        return lookupObject.inputValueArgumentsAreRequired
                || lookupObject.inputTrailingLambdaIsRequired
    }

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val targetFile = context.file as? KtFile ?: return
        val lookupObject = item.`object` as FunctionCallLookupObject

        super.handleInsert(context, item)

        val startOffset = context.startOffset
        val element = context.file.findElementAt(startOffset) ?: return

        val importStrategy = lookupObject.options.importingStrategy
        if (importStrategy is ImportStrategy.InsertFqNameAndShorten) {
            context.document.replaceString(
                context.startOffset,
                context.tailOffset,
                importStrategy.fqName.withRootPrefixIfNeeded().render()
            )
            context.commitDocument()

            addArguments(context, element, lookupObject)
            context.commitDocument()

            shortenReferencesInRange(targetFile, TextRange(context.startOffset, context.tailOffset))
        } else {
            addArguments(context, element, lookupObject)
            context.commitDocument()

            if (importStrategy is ImportStrategy.AddImport) {
                addImportIfRequired(context, importStrategy.nameToImport)
            }
        }
    }
}

internal sealed interface TrailingFunctionDescriptor {

    val functionType: KaFunctionType

    fun suggestParameterNameAt(index: Int): Name?

    data class Function(
        override val functionType: KaFunctionType,
    ) : TrailingFunctionDescriptor {

        override fun suggestParameterNameAt(index: Int): Name? =
            functionType.parameterTypes
                .getOrNull(index)
                ?.extractParameterName()
    }

    data class SamConstructor(
        override val functionType: KaFunctionType,
        val samSymbol: KaNamedFunctionSymbol,
    ) : TrailingFunctionDescriptor {

        override fun suggestParameterNameAt(index: Int): Name? =
            samSymbol.valueParameters
                .getOrNull(index)
                ?.name
    }
}

/**
 * @see KaVariableSignature.getValueFromParameterNameAnnotation
 */
private fun KaType.extractParameterName(): Name? =
    annotations.find { it.classId?.asSingleFqName() == StandardNames.FqNames.parameterName }
        ?.arguments
        ?.singleOrNull()
        ?.expression
        ?.let { it as? KaAnnotationValue.ConstantValue }
        ?.value
        ?.let { it as? KaConstantValue.StringValue }
        ?.value
        ?.let { Name.identifierIfValid(it) }