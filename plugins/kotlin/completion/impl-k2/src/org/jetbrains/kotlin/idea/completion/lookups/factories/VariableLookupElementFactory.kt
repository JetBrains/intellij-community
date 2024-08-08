// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.lookups.factories

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSyntheticJavaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.idea.base.analysis.withRootPrefixIfNeeded
import org.jetbrains.kotlin.idea.completion.lookups.*
import org.jetbrains.kotlin.idea.completion.lookups.CompletionShortNamesRenderer.renderVariable
import org.jetbrains.kotlin.idea.completion.lookups.TailTextProvider.getTailText
import org.jetbrains.kotlin.idea.completion.lookups.TailTextProvider.getTailTextForVariableCall
import org.jetbrains.kotlin.idea.completion.lookups.TailTextProvider.insertLambdaBraces
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.renderer.render

internal object VariableLookupElementFactory {

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    fun createLookup(
        signature: KaVariableSignature<*>,
        options: CallableInsertionOptions,
    ): LookupElementBuilder {
        val rendered = renderVariable(signature)
        var builder = createLookupElementBuilder(options, signature, rendered)

        val symbol = signature.symbol
        if (symbol is KaPropertySymbol) {
            builder = builder.withLookupString(symbol.javaGetterName.asString())
            symbol.javaSetterName?.let { builder = builder.withLookupString(it.asString()) }
        }

        return withCallableSignatureInfo(signature, builder)
    }

    context(KaSession)
    private fun createLookupElementBuilder(
        options: CallableInsertionOptions,
        signature: KaVariableSignature<*>,
        rendered: String,
        insertionStrategy: CallableInsertionStrategy = options.insertionStrategy
    ): LookupElementBuilder {
        val name = signature.symbol.name

        return when (insertionStrategy) {
            CallableInsertionStrategy.AsCall -> {
                val functionalType = signature.returnType as KaFunctionType
                val lookupObject = FunctionCallLookupObject(
                    name,
                    options,
                    rendered,
                    inputValueArgumentsAreRequired = functionalType.parameterTypes.isNotEmpty(),
                    inputTypeArgumentsAreRequired = false,
                    insertEmptyLambda = insertLambdaBraces(functionalType),
                )

                val tailText = getTailTextForVariableCall(functionalType, signature)

                LookupElementBuilder.create(lookupObject, name.asString())
                    .withTailText(tailText, true)
                    .withInsertHandler(FunctionInsertionHandler)
            }

            is CallableInsertionStrategy.WithSuperDisambiguation -> {
                val builder = createLookupElementBuilder(options, signature, rendered, insertionStrategy.subStrategy)
                updateLookupElementBuilderToInsertTypeQualifierOnSuper(builder, insertionStrategy)
            }

            else -> {
                val lookupObject = VariableLookupObject(name, options, rendered)
                markIfSyntheticJavaProperty(
                    LookupElementBuilder.create(lookupObject, name.asString())
                        .withTailText(getTailText(signature), true), signature.symbol
                ).withInsertHandler(VariableInsertionHandler)
            }
        }
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun markIfSyntheticJavaProperty(
        lookupElementBuilder: LookupElementBuilder,
        symbol: KaVariableSymbol
    ): LookupElementBuilder = when (symbol) {
        is KaSyntheticJavaPropertySymbol -> {
            val getterName = symbol.javaGetterName.asString()
            val setterName = symbol.javaSetterName?.asString()
            lookupElementBuilder.withTailText((" (from ${buildSyntheticPropertyTailText(getterName, setterName)})"))
                .withLookupStrings(listOfNotNull(getterName, setterName))
        }

        else -> lookupElementBuilder
    }

    private fun buildSyntheticPropertyTailText(getterName: String, setterName: String?): String =
        if (setterName != null) "$getterName()/$setterName()" else "$getterName()"
}

/**
 * Simplest lookup object so two lookup elements for the same property will clash.
 */
private data class VariableLookupObject(
    override val shortName: Name,
    override val options: CallableInsertionOptions,
    override val renderedDeclaration: String,
) : KotlinCallableLookupObject()


private object VariableInsertionHandler : CallableIdentifierInsertionHandler()

internal open class CallableIdentifierInsertionHandler : QuotedNamesAwareInsertionHandler() {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val targetFile = context.file as? KtFile ?: return
        val lookupObject = item.`object` as KotlinCallableLookupObject

        super.handleInsert(context, item)

        when (val importStrategy = lookupObject.options.importingStrategy) {
            is ImportStrategy.AddImport -> {
                addImportIfRequired(targetFile, importStrategy.nameToImport)
            }

            is ImportStrategy.InsertFqNameAndShorten -> {
                context.document.replaceString(
                    context.startOffset,
                    context.tailOffset,
                    importStrategy.fqName.withRootPrefixIfNeeded().render()
                )

                context.commitDocument()
                shortenReferencesInRange(targetFile, TextRange(context.startOffset, context.tailOffset))
            }

            is ImportStrategy.DoNothing -> {
            }
        }
    }
}