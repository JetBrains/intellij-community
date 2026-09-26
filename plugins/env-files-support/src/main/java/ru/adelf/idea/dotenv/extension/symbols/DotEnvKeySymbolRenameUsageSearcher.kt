package ru.adelf.idea.dotenv.extension.symbols

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.search.LeafOccurrence
import com.intellij.model.search.LeafOccurrenceMapper
import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchService
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.util.TextRange
import com.intellij.refactoring.rename.api.PsiModifiableRenameUsage.Companion.defaultPsiModifiableRenameUsage
import com.intellij.refactoring.rename.api.RenameUsage
import com.intellij.refactoring.rename.api.RenameUsageSearchParameters
import com.intellij.refactoring.rename.api.RenameUsageSearcher
import com.intellij.util.AbstractQuery
import com.intellij.util.Processor
import com.intellij.util.Query

class DotEnvKeySymbolRenameUsageSearcher : RenameUsageSearcher {

    override fun collectSearchRequests(parameters: RenameUsageSearchParameters): Collection<Query<out RenameUsage>> {
        val targetSymbol = parameters.target as? DotEnvKeySymbol ?: return emptyList()
        val symbolPointer = targetSymbol.createPointer()
        val usages = SearchService.getInstance()
            .searchWord(parameters.project, targetSymbol.name)
            .caseSensitive(true)
            .inContexts(SearchContext.inCode(), SearchContext.inStrings())
            .inScope(parameters.searchScope)
            .buildQuery(LeafOccurrenceMapper.withPointer(symbolPointer, ::validateRenameUsageSearchReferences))
        val selfUsage = DotEnvKeySymbolUsageQuery(
            defaultPsiModifiableRenameUsage(targetSymbol.declarationUsage())
        )
        return listOf(usages, selfUsage)
    }

    private fun validateRenameUsageSearchReferences(symbol: DotEnvKeySymbol, leafOccurrence: LeafOccurrence): Collection<RenameUsage> {
        if (validateReferences(symbol, leafOccurrence, true).isEmpty()) return emptyList()

        val range = TextRange.from(leafOccurrence.start.textRange.startOffset + leafOccurrence.offsetInStart, symbol.name.length)
        return listOf(defaultPsiModifiableRenameUsage(PsiUsage.textUsage(leafOccurrence.start.containingFile, range)))
    }

    internal class DotEnvKeySymbolUsageQuery<T>(private val targetDeclaration: T) : AbstractQuery<T>() {
        override fun processResults(processor: Processor<in T>): Boolean {
            return runReadActionBlocking {
                processor.process(targetDeclaration)
            }
        }
    }

}
