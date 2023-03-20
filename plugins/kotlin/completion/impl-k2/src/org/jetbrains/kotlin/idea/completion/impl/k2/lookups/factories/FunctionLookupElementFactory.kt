// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.lookups.factories

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.endOffset
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.types.KtSubstitutor
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.idea.base.analysis.withRootPrefixIfNeeded
import org.jetbrains.kotlin.idea.completion.KotlinCompletionCharFilter
import org.jetbrains.kotlin.idea.completion.contributors.helpers.insertSymbol
import org.jetbrains.kotlin.idea.completion.handlers.isCharAt
import org.jetbrains.kotlin.idea.completion.lookups.*
import org.jetbrains.kotlin.idea.completion.lookups.CompletionShortNamesRenderer.renderFunctionParameters
import org.jetbrains.kotlin.idea.completion.lookups.TailTextProvider.getTailText
import org.jetbrains.kotlin.idea.completion.lookups.TailTextProvider.insertLambdaBraces
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.renderer.render

internal class FunctionLookupElementFactory {
    fun KtAnalysisSession.createLookup(
        name: Name,
        symbol: KtFunctionLikeSymbol,
        options: CallableInsertionOptions,
        substitutor: KtSubstitutor,
        expectedType: KtType? = null,
    ): LookupElementBuilder {
        val insertEmptyLambda = insertLambdaBraces(symbol)
        val lookupObject = FunctionCallLookupObject(
          name,
          options,
          renderFunctionParameters(symbol, substitutor),
          inputValueArgumentsAreRequired = symbol.valueParameters.isNotEmpty(),
          inputTypeArgumentsAreRequired = !functionCanBeCalledWithoutExplicitTypeArguments(symbol, expectedType),
          insertEmptyLambda,
        )

        val builder = LookupElementBuilder.create(lookupObject, name.asString())
            .withTailText(getTailText(symbol, substitutor))
            .let { withSymbolInfo(symbol, it, substitutor) }
            .also { it.putUserData(KotlinCompletionCharFilter.ACCEPT_OPENING_BRACE, Unit) }
        return updateLookupElementBuilder(options, builder)
    }

    private fun updateLookupElementBuilder(
        options: CallableInsertionOptions,
        builder: LookupElementBuilder,
        insertionStrategy: CallableInsertionStrategy = options.insertionStrategy
    ): LookupElementBuilder {
        return when (insertionStrategy) {
            CallableInsertionStrategy.AsCall -> builder.withInsertHandler(FunctionInsertionHandler)
            CallableInsertionStrategy.AsIdentifier -> builder.withInsertHandler(QuotedNamesAwareInsertionHandler())
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
                            context.insertSymbol(argString)
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

    private fun KtAnalysisSession.functionCanBeCalledWithoutExplicitTypeArguments(
      symbol: KtFunctionLikeSymbol,
      expectedType: KtType?
    ): Boolean {
        if (symbol.typeParameters.isEmpty()) return true

        val typeParametersToInfer = symbol.typeParameters.toSet()
        val potentiallyInferredTypeParameters = mutableSetOf<KtTypeParameterSymbol>()

        /**
         * Collects type arguments of [type] (or type itself in case of [KtTypeParameterType]), which are probably will be inferred.
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
        fun collectPotentiallyInferredTypes(type: KtType, onlyCollectReturnTypeOfFunctionalType: Boolean) {
            when (type) {
                is KtTypeParameterType -> {
                    val typeParameterSymbol = type.symbol
                    if (typeParameterSymbol !in typeParametersToInfer || typeParameterSymbol in potentiallyInferredTypeParameters) return

                    potentiallyInferredTypeParameters.add(type.symbol)
                    // Add type parameters possibly inferred by type arguments of parameter's upper-bound
                    // e.g. <T, C: Iterable<T>>, so T is inferred from C
                    type.symbol.upperBounds
                        .filterIsInstance<KtNonErrorClassType>()
                        .filter { it.ownTypeArguments.isNotEmpty() }
                        .forEach { collectPotentiallyInferredTypes(it, onlyCollectReturnTypeOfFunctionalType = false) }
                }

                is KtFunctionalType -> {
                    val typesToProcess = if (onlyCollectReturnTypeOfFunctionalType) {
                        // do not rely on inference from input of functional type - use only return type of functional type
                        listOf(type.returnType)
                    } else {
                        listOfNotNull(type.receiverType) + type.returnType + type.parameterTypes
                    }
                    typesToProcess.forEach { collectPotentiallyInferredTypes(it, onlyCollectReturnTypeOfFunctionalType) }
                }

                is KtUsualClassType -> {
                    val typeArguments = type.ownTypeArguments.mapNotNull { it.type }
                    typeArguments.forEach { collectPotentiallyInferredTypes(it, onlyCollectReturnTypeOfFunctionalType) }
                }

                else -> {}
            }
        }


        symbol.receiverParameter?.type?.let { collectPotentiallyInferredTypes(it, onlyCollectReturnTypeOfFunctionalType = true) }
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
    val inputValueArgumentsAreRequired: Boolean,
    val inputTypeArgumentsAreRequired: Boolean,
    val insertEmptyLambda: Boolean,
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
        val insertLambda = !preferParentheses && lookupObject.insertEmptyLambda
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

        if (insertTypeArguments) {
            document.insertString(offset, "<>")
            editor.caretModel.moveToOffset(offset + 1)
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

                if (isInsertSpacesInOneLineFunctionEnabled(context.file)) {
                    document.insertString(offset, " {  }")
                    inBracketsShift = 1
                } else {
                    document.insertString(offset, " {}")
                }
            } else {
                if (isSmartEnterCompletion) {
                    document.insertString(offset, "(")
                } else {
                    document.insertString(offset, "()")
                }
            }
            context.commitDocument()

            openingBracketOffset = document.charsSequence.indexOfSkippingSpace(openingBracket, offset)!!
            closeBracketOffset = document.charsSequence.indexOfSkippingSpace(closingBracket, openingBracketOffset + 1)
        }

        if (!insertTypeArguments) {
            if (shouldPlaceCaretInBrackets(completionChar, lookupObject) || closeBracketOffset == null) {
                editor.caretModel.moveToOffset(openingBracketOffset + 1 + inBracketsShift)
                AutoPopupController.getInstance(project)?.autoPopupParameterInfo(editor, offsetElement)
            } else {
                editor.caretModel.moveToOffset(closeBracketOffset + 1)
            }
        }
    }

    private fun shouldPlaceCaretInBrackets(completionChar: Char, lookupObject: FunctionCallLookupObject): Boolean {
        if (completionChar == ',' || completionChar == '.' || completionChar == '=') return false
        if (completionChar == '(') return true
        return lookupObject.inputValueArgumentsAreRequired || lookupObject.insertEmptyLambda
    }

    // FIXME Should be fetched from language settings (INSERT_WHITESPACES_IN_SIMPLE_ONE_LINE_METHOD), but we do not have them right now
    private fun isInsertSpacesInOneLineFunctionEnabled(file: PsiElement) = true

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
                addCallableImportIfRequired(targetFile, importStrategy.nameToImport)
            }
        }
    }
}
