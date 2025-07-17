// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.lookups.factories

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.nameOrAnonymous
import org.jetbrains.kotlin.idea.base.analysis.withRootPrefixIfNeeded
import org.jetbrains.kotlin.idea.base.serialization.names.KotlinNameSerializer
import org.jetbrains.kotlin.idea.completion.lookups.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.renderer.render

internal object ClassLookupElementFactory {

    context(KaSession)
    fun createLookup(
        symbol: KaClassLikeSymbol,
        importingStrategy: ImportStrategy,
        aliasName: Name? = null,
    ): LookupElementBuilder {
        val name = aliasName ?: symbol.nameOrAnonymous
        return LookupElementBuilder.create(ClassifierLookupObject(name, importingStrategy), name.asString())
            .withInsertHandler(ClassifierInsertionHandler)
            .withTailText(TailTextProvider.getTailText(symbol, useFqnAsTailText = aliasName != null), true)
            .let { withClassifierSymbolInfo(symbol, it) }
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    fun createConstructorLookup(
        containingSymbol: KaNamedClassSymbol,
        constructorSymbols: List<KaConstructorSymbol>,
        importingStrategy: ImportStrategy,
        aliasName: Name? = null,
    ): LookupElementBuilder {
        val name = aliasName ?: containingSymbol.nameOrAnonymous
        val singleConstructor = constructorSymbols.singleOrNull()
        val valueParameters = singleConstructor?.valueParameters?.map { it.asSignature() }

        val options = CallableInsertionOptions(
            importingStrategy = importingStrategy,
            insertionStrategy = CallableInsertionStrategy.AsCall
        )

        val lookupObject = FunctionCallLookupObject(
            shortName = name,
            options = options,
            renderedDeclaration = valueParameters?.let { CompletionShortNamesRenderer.renderFunctionParameters(it) } ?: "(...)",
            hasReceiver = false,
            inputValueArgumentsAreRequired = constructorSymbols.size > 1 || valueParameters?.isNotEmpty() == true,
            inputTypeArgumentsAreRequired = false,
        )
        return LookupElementBuilder.create(lookupObject, name.asString())
            .withInsertHandler(FunctionInsertionHandler)
            .appendTailText(lookupObject.renderedDeclaration, true)
            .appendTailText(TailTextProvider.getTailText(containingSymbol), true)
            .let { withClassifierSymbolInfo(containingSymbol, it) }
    }
}


@Serializable
internal data class ClassifierLookupObject(
    @Serializable(with = KotlinNameSerializer::class) override val shortName: Name,
    val importingStrategy: ImportStrategy
) : KotlinLookupObject

/**
 * The simplest implementation of the insertion handler for a classifiers.
 */
@Serializable
internal object ClassifierInsertionHandler : QuotedNamesAwareInsertionHandler() {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val targetFile = context.file as? KtFile ?: return
        val lookupObject = item.`object` as ClassifierLookupObject
        val importingStrategy = lookupObject.importingStrategy

        super.handleInsert(context, item)

        if (importingStrategy is ImportStrategy.InsertFqNameAndShorten) {
            val shortenCommand = item.shortenCommand
                ?.takeUnless { it.isEmpty }

            val fqName = importingStrategy.fqName
                .withRootPrefixIfNeeded()

            context.insertAndShortenReferencesInStringUsingTemporarySuffix(
                string = fqName.render(),
                shortenCommand = shortenCommand,
            )
        } else if (importingStrategy is ImportStrategy.AddImport) {
            addImportIfRequired(context, importingStrategy.nameToImport)
        }
    }
}
