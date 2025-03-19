package ru.adelf.idea.dotenv.extension.symbols

import com.intellij.find.usages.api.PsiUsage
import com.intellij.find.usages.api.Usage
import com.intellij.find.usages.api.UsageSearchParameters
import com.intellij.find.usages.api.UsageSearcher
import com.intellij.model.search.LeafOccurrence
import com.intellij.model.search.LeafOccurrenceMapper
import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchService
import com.intellij.util.Query
import ru.adelf.idea.dotenv.psi.DotEnvKey

class DotEnvKeyReferenceUsageSearcher : UsageSearcher {

    override fun collectSearchRequests(parameters: UsageSearchParameters): Collection<Query<out Usage>> {
        val symbol = parameters.target as? DotEnvKeySymbol ?: return emptyList()
        val symbolPointer = symbol.createPointer()
        val usages = SearchService.getInstance()
            .searchWord(parameters.project, symbol.name).caseSensitive(true)
            .inContexts(SearchContext.IN_CODE, SearchContext.IN_STRINGS)
            .inScope(parameters.searchScope)
            .buildQuery(LeafOccurrenceMapper.withPointer(symbolPointer, ::validateReferences))
            .mapping { PsiUsage.textUsage(it.element.containingFile, it.element.textRange) }
        return listOf(usages)
    }

    fun validateReferences(symbol: DotEnvKeySymbol, leafOccurrence: LeafOccurrence): Collection<DotEnvKeyReference> {
        if (leafOccurrence.start.parent is DotEnvKey) {
            return emptyList()
        }
        val referenceCandidate = DotEnvKeyReference(leafOccurrence.start)
        return if (referenceCandidate.resolvesTo(symbol)) listOf(referenceCandidate) else emptyList()
    }

}