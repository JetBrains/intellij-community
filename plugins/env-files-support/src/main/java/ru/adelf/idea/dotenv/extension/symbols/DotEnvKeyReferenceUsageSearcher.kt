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
            .inContexts(SearchContext.inCode(), SearchContext.inStrings())
            .inScope(parameters.searchScope)
            .buildQuery(LeafOccurrenceMapper.withPointer(symbolPointer, ::validateUsageSearchReferences))
            .mapping { PsiUsage.textUsage(it.element.containingFile, it.element.textRange) }
        return listOf(usages)
    }

}

private fun validateUsageSearchReferences(symbol: DotEnvKeySymbol, leafOccurrence: LeafOccurrence): Collection<DotEnvKeyReference> {
    return validateReferences(symbol, leafOccurrence, false)
}

internal fun validateReferences(symbol: DotEnvKeySymbol, leafOccurrence: LeafOccurrence, allowDeclarations: Boolean): Collection<DotEnvKeyReference> {
    val dismissDeclaration = !allowDeclarations && leafOccurrence.start.parent is DotEnvKey
    val symbolRefersToLeafOccurrence = symbol.file == leafOccurrence.start.containingFile
                                       && symbol.rangeInFile == leafOccurrence.start.textRange
    if (dismissDeclaration || symbolRefersToLeafOccurrence) {
        return emptyList()
    }
    val referenceCandidate = DotEnvKeyReference(leafOccurrence.start)
    return if (referenceCandidate.resolvesTo(symbol)) listOf(referenceCandidate) else emptyList()
}

