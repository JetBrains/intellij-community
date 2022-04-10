// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.lookups.factories

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.completion.lookups.*
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.KotlinLookupObject
import org.jetbrains.kotlin.idea.completion.lookups.shortenReferencesForFirCompletion
import org.jetbrains.kotlin.idea.completion.lookups.withSymbolInfo
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.nameOrAnonymous
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.renderer.render

internal class ClassLookupElementFactory {
    fun KtAnalysisSession.createLookup(
        symbol: KtClassLikeSymbol,
        importingStrategy: ImportStrategy = detectImportStrategy(symbol)
    ): LookupElementBuilder {
        val name = symbol.nameOrAnonymous
        return LookupElementBuilder.create(ClassifierLookupObject(name, importingStrategy), name.asString())
            .withInsertHandler(ClassifierInsertionHandler)
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
private object ClassifierInsertionHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val targetFile = context.file as? KtFile ?: return
        val lookupObject = item.`object` as ClassifierLookupObject

        if (lookupObject.importingStrategy is ImportStrategy.InsertFqNameAndShorten) {
            val fqName = lookupObject.importingStrategy.fqName

            context.document.replaceString(context.startOffset, context.tailOffset, fqName.render())
            context.commitDocument()

            shortenReferencesForFirCompletion(targetFile, TextRange(context.startOffset, context.tailOffset))
        }
    }
}
