// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.lookups.factories

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSyntheticJavaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtVariableLikeSymbol
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtSubstitutor
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.idea.base.analysis.withRootPrefixIfNeeded
import org.jetbrains.kotlin.idea.completion.lookups.*
import org.jetbrains.kotlin.idea.completion.lookups.CompletionShortNamesRenderer.renderVariable
import org.jetbrains.kotlin.idea.completion.lookups.TailTextProvider.getTailText
import org.jetbrains.kotlin.idea.completion.lookups.TailTextProvider.insertLambdaBraces
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.types.Variance

internal class VariableLookupElementFactory {
    fun KtAnalysisSession.createLookup(
        symbol: KtVariableLikeSymbol,
        options: CallableInsertionOptions,
        substitutor: KtSubstitutor = KtSubstitutor.Empty(token),
    ): LookupElementBuilder {
        val rendered = renderVariable(symbol, substitutor)
        var builder = createLookupElementBuilder(options, symbol, rendered, substitutor)

        if (symbol is KtPropertySymbol) {
            builder = builder.withLookupString(symbol.javaGetterName.asString())
            symbol.javaSetterName?.let { builder = builder.withLookupString(it.asString()) }
        }

        return withSymbolInfo(symbol, builder, substitutor)
    }

    private fun KtAnalysisSession.createLookupElementBuilder(
        options: CallableInsertionOptions,
        symbol: KtVariableLikeSymbol,
        rendered: String,
        substitutor: KtSubstitutor,
        insertionStrategy: CallableInsertionStrategy = options.insertionStrategy
    ): LookupElementBuilder {
        val symbolType = substitutor.substitute(symbol.returnType)
        return when (insertionStrategy) {
            CallableInsertionStrategy.AsCall -> {
                val functionalType = symbolType as KtFunctionalType
                val lookupObject = FunctionCallLookupObject(
                    symbol.name,
                    options,
                    rendered,
                    inputValueArgumentsAreRequired = functionalType.parameterTypes.isNotEmpty(),
                    inputTypeArgumentsAreRequired = false,
                    insertEmptyLambda = insertLambdaBraces(functionalType),
                )

                val tailText = functionalType.parameterTypes.joinToString(prefix = "(", postfix = ")") {
                    substitutor.substitute(it).render(CompletionShortNamesRenderer.renderer, position = Variance.INVARIANT)
                }

                LookupElementBuilder.create(lookupObject, symbol.name.asString())
                    .withTailText(tailText, true)
                    .withInsertHandler(FunctionInsertionHandler)
            }
            is CallableInsertionStrategy.WithSuperDisambiguation -> {
                val builder = createLookupElementBuilder(options, symbol, rendered, substitutor, insertionStrategy.subStrategy)
                updateLookupElementBuilderToInsertTypeQualifierOnSuper(builder, insertionStrategy)
            }
            else -> {
                val lookupObject = VariableLookupObject(symbol.name, options, rendered)
                markIfSyntheticJavaProperty(
                    LookupElementBuilder.create(lookupObject, symbol.name.asString())
                        .withTailText(getTailText(symbol, substitutor), true), symbol
                ).withInsertHandler(VariableInsertionHandler)
            }
        }
    }

    private fun KtAnalysisSession.markIfSyntheticJavaProperty(
        lookupElementBuilder: LookupElementBuilder,
        symbol: KtVariableLikeSymbol
    ): LookupElementBuilder = when (symbol) {
        is KtSyntheticJavaPropertySymbol -> {
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


private object VariableInsertionHandler : QuotedNamesAwareInsertionHandler() {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val targetFile = context.file as? KtFile ?: return
        val lookupObject = item.`object` as VariableLookupObject

        super.handleInsert(context, item)

        when (val importStrategy = lookupObject.options.importingStrategy) {
            is ImportStrategy.AddImport -> {
                addCallableImportIfRequired(targetFile, importStrategy.nameToImport)
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