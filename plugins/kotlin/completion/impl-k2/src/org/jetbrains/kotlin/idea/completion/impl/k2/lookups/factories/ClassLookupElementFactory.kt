// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.lookups.factories

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.completion.lookups.*
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.KotlinLookupObject
import org.jetbrains.kotlin.idea.completion.lookups.withSymbolInfo
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.nameOrAnonymous
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.idea.completion.lookups.TailTextProvider.getTailText
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.renderer.render

internal class ClassLookupElementFactory {
    fun KtAnalysisSession.createLookup(
        symbol: KtClassLikeSymbol,
        importingStrategy: ImportStrategy,
    ): LookupElementBuilder {
        val name = symbol.nameOrAnonymous
        return LookupElementBuilder.create(ClassifierLookupObject(name, importingStrategy), name.asString())
            .withInsertHandler(ClassifierInsertionHandler)
            .withTailText(getTailText(symbol))
            .let { withSymbolInfo(symbol, it) }
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

        super.handleInsert(context, item)

        if (lookupObject.importingStrategy is ImportStrategy.InsertFqNameAndShorten) {
            val fqName = lookupObject.importingStrategy.fqName

            context.document.replaceString(context.startOffset, context.tailOffset, fqName.render())
            context.commitDocument()

            shortenReferencesInRange(targetFile, TextRange(context.startOffset, context.tailOffset))
        }
    }
}
