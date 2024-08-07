// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.lookups.factories

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.nameOrAnonymous
import org.jetbrains.kotlin.idea.completion.lookups.*
import org.jetbrains.kotlin.idea.completion.lookups.TailTextProvider.getTailText
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.renderer.render

internal object ClassLookupElementFactory {

    context(KaSession)
    fun createLookup(
        symbol: KaClassLikeSymbol,
        importingStrategy: ImportStrategy,
    ): LookupElementBuilder {
        val name = symbol.nameOrAnonymous
        return LookupElementBuilder.create(ClassifierLookupObject(name, importingStrategy), name.asString())
            .withInsertHandler(ClassifierInsertionHandler)
            .withTailText(getTailText(symbol))
            .let { withClassifierSymbolInfo(symbol, it) }
    }
}


private data class ClassifierLookupObject(
    override val shortName: Name,
    val importingStrategy: ImportStrategy
) : KotlinLookupObject

/**
 * The simplest implementation of the insertion handler for a classifiers.
 */
private object ClassifierInsertionHandler : QuotedNamesAwareInsertionHandler() {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val targetFile = context.file as? KtFile ?: return
        val lookupObject = item.`object` as ClassifierLookupObject
        val importingStrategy = lookupObject.importingStrategy

        super.handleInsert(context, item)

        if (importingStrategy is ImportStrategy.InsertFqNameAndShorten) {
            val fqNameRendered = importingStrategy.fqName.render()
            context.insertAndShortenReferencesInStringUsingTemporarySuffix(fqNameRendered)
        } else if (importingStrategy is ImportStrategy.AddImport) {
            addImportIfRequired(targetFile, importingStrategy.nameToImport)
        }
    }
}
