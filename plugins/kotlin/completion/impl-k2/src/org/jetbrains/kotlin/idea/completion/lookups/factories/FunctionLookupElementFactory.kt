// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.lookups.factories

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.endOffset
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isPossiblySubTypeOf
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.idea.base.analysis.withRootPrefixIfNeeded
import org.jetbrains.kotlin.idea.completion.acceptOpeningBrace
import org.jetbrains.kotlin.idea.completion.contributors.helpers.insertString
import org.jetbrains.kotlin.idea.completion.handlers.isCharAt
import org.jetbrains.kotlin.idea.completion.implCommon.stringTemplates.build
import org.jetbrains.kotlin.idea.completion.lookups.*
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
    ): LookupElementBuilder {
        val valueParameters = signature.valueParameters

        val lookupObject = FunctionCallLookupObject(
            shortName = shortName,
            options = options,
            renderedDeclaration = CompletionShortNamesRenderer.renderFunctionParameters(valueParameters),
            inputValueArgumentsAreRequired = valueParameters.isNotEmpty(),
            inputTypeArgumentsAreRequired = !FunctionInsertionHelper.functionCanBeCalledWithoutExplicitTypeArguments(
                symbol = signature.symbol,
                expectedType = expectedType
            ),
        )

        return createLookupElement(signature, lookupObject)
    }

    context(KaSession)
    @ApiStatus.Experimental
    @OptIn(KaExperimentalApi::class)
    fun createLookupWithTrailingLambda(
        shortName: Name,
        signature: KaFunctionSignature<*>,
        options: CallableInsertionOptions,
    ): LookupElementBuilder? {
        val valueParameters = signature.valueParameters

        val trailingFunctionSignature = valueParameters.lastOrNull()
            ?.takeUnless { it.symbol.hasDefaultValue }
            ?: return null

        val trailingFunctionDescriptor = when (val type = trailingFunctionSignature.returnType.lowerBoundIfFlexible()) {
            is KaFunctionType -> TrailingFunctionDescriptor(type)
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

                // TODO this is a workaround
                // FIX pass KaCallableMemberCall.typeArgumentsMapping from the resolve result
                val functionType = type.typeArguments
                    .mapNotNull { it.type }
                    .zip(samConstructor.typeParameters)
                    .associate { (parameterSymbol, typeProjection) ->
                        typeProjection to parameterSymbol
                    }
                    .let { createSubstitutor(it) }
                    .substitute(samConstructorType) as? KaFunctionType
                    ?: return null

                val samSymbol = functionClassSymbol.samSymbol
                    ?: return null

                TrailingFunctionDescriptor(
                    functionType = functionType,
                    suggestedParameterNames = samSymbol.valueParameters.map { it.name },
                )
            }

            else -> return null
        }

        val trailingFunctionTemplate = TemplateManager.getInstance(useSiteModule.project)
            .createTemplate("", "")
            .build(
                parametersCount = valueParameters.size - 1,
                trailingFunctionType = trailingFunctionDescriptor.functionType,
                suggestedParameterNames = trailingFunctionDescriptor.suggestedParameterNames,
            )

        val lookupObject = FunctionCallLookupObject(
            shortName = shortName,
            options = options,
            renderedDeclaration = CompletionShortNamesRenderer.renderFunctionParameters(
                parameters = valueParameters,
                trailingFunctionType = trailingFunctionDescriptor.functionType,
            ),
            trailingLambdaTemplate = trailingFunctionTemplate,
        )

        return createLookupElement(signature, lookupObject)
    }

    context(KaSession)
    private fun createLookupElement(
        signature: KaFunctionSignature<*>,
        lookupObject: FunctionCallLookupObject,
    ): LookupElementBuilder = LookupElementBuilder.create(
        /* lookupObject = */ lookupObject,
        /* lookupString = */ lookupObject.shortName.asString(),
    ).appendTailText(lookupObject.renderedDeclaration, true)
        .appendTailText(TailTextProvider.getTailText(signature), true)
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
            is CallableInsertionStrategy.AsIdentifierCustom -> builder.withInsertHandler(object : QuotedNamesAwareInsertionHandler() {
                override fun handleInsert(context: InsertionContext, item: LookupElement) {
                    super.handleInsert(context, item)
                    insertionStrategy.insertionHandlerAction(context)
                }
            })

            is CallableInsertionStrategy.WithCallArgs -> {
                val argString = insertionStrategy.args.joinToString(", ", prefix = "(", postfix = ")")
                builder.withInsertHandler(
                    object : QuotedNamesAwareInsertionHandler() {
                        override fun handleInsert(context: InsertionContext, item: LookupElement) {
                            super.handleInsert(context, item)
                            context.insertString(argString)
                        }
                    }
                ).withTailText(argString, false)
            }

            is CallableInsertionStrategy.WithSuperDisambiguation -> {
                val resultBuilder = updateLookupElementBuilder(options, builder, insertionStrategy.subStrategy)
                updateLookupElementBuilderToInsertTypeQualifierOnSuper(resultBuilder, insertionStrategy)
            }
        }
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

internal data class FunctionCallLookupObject(
    override val shortName: Name,
    override val options: CallableInsertionOptions,
    override val renderedDeclaration: String,
    val inputValueArgumentsAreRequired: Boolean = false,
    val inputTypeArgumentsAreRequired: Boolean = false,
    val trailingLambdaTemplate: Template? = null,
) : KotlinCallableLookupObject()

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
        val trailingLambdaTemplate = lookupObject.trailingLambdaTemplate
        val insertLambda = !preferParentheses && trailingLambdaTemplate != null
        val (openingBracket, closingBracket) = if (insertLambda) '{' to '}' else '(' to ')'

        if (isReplaceCompletion) {
            val offset1 = chars.skipSpaces(offset)
            if (offset1 < chars.length) {
                if (chars[offset1] == '<') {
                    val token = context.file.findElementAt(offset1)!!
                    if (token.node.elementType == KtTokens.LT) {
                        val parent = token.parent
                        /* if type argument list is on multiple lines this is more likely wrong parsing*/
                        if (parent is KtTypeArgumentList && parent.getText().indexOf('\n') < 0) {
                            offset = parent.endOffset
                            insertTypeArguments = false
                        }
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
            if (insertLambda) {
                if (completionChar == ' ' || completionChar == '{') {
                    context.setAddCompletionChar(false)
                }

                TemplateManager.getInstance(project)
                    .startTemplate(
                        /* editor = */ editor,
                        /* template = */ trailingLambdaTemplate!!,
                        /* inSeparateCommand = */ false,
                        /* predefinedVarValues = */ null,
                        /* listener = */ null,
                    )
            } else {
                if (isSmartEnterCompletion) {
                    document.insertString(offset, "(")
                } else {
                    document.insertString(offset, "()")
                }
            }
            context.commitDocument()

            if (!insertLambda) {
                openingBracketOffset = document.charsSequence.indexOfSkippingSpace(openingBracket, offset)!!
                closeBracketOffset = document.charsSequence.indexOfSkippingSpace(closingBracket, openingBracketOffset + 1)
            }
        }

        if (!insertTypeArguments) {
            if (shouldPlaceCaretInBrackets(completionChar, lookupObject) || closeBracketOffset == null) {
                if (!insertLambda) {
                    caretModel.moveToOffset(openingBracketOffset!! + 1)
                } else {
                    // do nothing, everything is handled by the template itself
                }

                AutoPopupController.getInstance(project).autoPopupParameterInfo(editor, offsetElement)
            } else {
                caretModel.moveToOffset(closeBracketOffset + 1)
            }
        }
    }

    private fun shouldPlaceCaretInBrackets(completionChar: Char, lookupObject: FunctionCallLookupObject): Boolean {
        if (completionChar == ',' || completionChar == '.' || completionChar == '=') return false
        if (completionChar == '(') return true
        return lookupObject.inputValueArgumentsAreRequired
                || lookupObject.trailingLambdaTemplate != null
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
                addImportIfRequired(targetFile, importStrategy.nameToImport)
            }
        }
    }
}

private data class TrailingFunctionDescriptor(
    val functionType: KaFunctionType,
    val suggestedParameterNames: List<Name?> = functionType.parameterTypes.map { it.extractParameterName() },
)

context(KaSession)
private val KaNamedClassSymbol.samSymbol: KaNamedFunctionSymbol?
    get() = declaredMemberScope.callables
        .filterIsInstance<KaNamedFunctionSymbol>()
        .filter { it.modality == KaSymbolModality.ABSTRACT }
        .singleOrNull()

/**
 * @see org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature.getValueFromParameterNameAnnotation
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