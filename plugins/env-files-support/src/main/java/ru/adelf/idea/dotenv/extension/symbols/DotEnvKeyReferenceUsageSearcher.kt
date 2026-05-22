package ru.adelf.idea.dotenv.extension.symbols

import com.intellij.find.usages.api.PsiUsage
import com.intellij.find.usages.api.Usage
import com.intellij.find.usages.api.UsageSearchParameters
import com.intellij.find.usages.api.UsageSearcher
import com.intellij.model.search.LeafOccurrence
import com.intellij.model.search.LeafOccurrenceMapper
import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.Query
import ru.adelf.idea.dotenv.psi.DotEnvKey
import ru.adelf.idea.dotenv.util.EnvironmentVariablesProviderUtil

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
    val leaf = leafOccurrence.start
    val isDeclarationLeaf = leaf.parent is DotEnvKey
    val dismissDeclaration = !allowDeclarations && isDeclarationLeaf
    val symbolRefersToLeafOccurrence = symbol.file == leaf.containingFile
                                       && symbol.rangeInFile == leaf.textRange
    if (dismissDeclaration || symbolRefersToLeafOccurrence) {
        return emptyList()
    }
    if (!(allowDeclarations && isDeclarationLeaf) && !isAcceptedByLanguageProvider(symbol, leaf)) {
        return emptyList()
    }
    val referenceCandidate = DotEnvKeyReference(leaf, symbol.name)
    return if (referenceCandidate.resolvesTo(symbol)) listOf(referenceCandidate) else emptyList()
}

private fun isAcceptedByLanguageProvider(symbol: DotEnvKeySymbol, leaf: PsiElement): Boolean {
    val containingFile = leaf.containingFile ?: return false
    val acceptedRanges = acceptedUsageRanges(containingFile)[symbol.name] ?: return false
    val leafRange = leaf.textRange
    return acceptedRanges.any { it.contains(leafRange) }
}

private fun acceptedUsageRanges(file: PsiFile): Map<String, List<TextRange>> {
    return CachedValuesManager.getCachedValue(file) {
        val ranges = HashMap<String, MutableList<TextRange>>()
        file.virtualFile?.let { virtualFile ->
            for (provider in EnvironmentVariablesProviderUtil.getEnvVariablesUsagesProviders()) {
                if (!provider.acceptFile(virtualFile)) continue
                for (usage in provider.getUsages(file)) {
                    ranges.getOrPut(usage.key) { ArrayList() }.add(usage.element.textRange)
                }
            }
        }
        CachedValueProvider.Result.create<Map<String, List<TextRange>>>(
            ranges,
            PsiModificationTracker.MODIFICATION_COUNT,
            EnvironmentVariablesProviderUtil.getEnvVariablesUsagesProvidersModificationTracker(),
        )
    }
}
