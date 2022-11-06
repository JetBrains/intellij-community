package org.jetbrains.completion.full.line.platform

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.Consumer
import org.jetbrains.completion.full.line.*
import org.jetbrains.completion.full.line.language.FullLineLanguageSupporter
import org.jetbrains.completion.full.line.platform.weigher.FullLineWeigher
import org.jetbrains.completion.full.line.providers.FullLineCompletionProvider
import org.jetbrains.completion.full.line.services.TabSelectedItemStorage
import org.jetbrains.completion.full.line.settings.MLServerCompletionBundle.Companion.message
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings

class FullLineContributor : CompletionContributor(), DumbAware {
  private val recentRequestsCache = RequestsCache()

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    when (val request = FullLineRequest.of(parameters)) {
      is FullLineRequest.Applicable -> startCompletion(parameters, result, request)
      is FullLineRequest.Inapplicable -> LOG.debug("Skipped: ${request.reason}")
    }
  }

  override fun handleAutoCompletionPossibility(context: AutoCompletionContext): AutoCompletionDecision? {
    return if (FullLineRequest.of(context.parameters) is FullLineRequest.Applicable)  AutoCompletionDecision.SHOW_LOOKUP else null
  }

  private fun startCompletion(
    parameters: CompletionParameters,
    result: CompletionResultSet,
    request: FullLineRequest.Applicable
  ) {
    val file = parameters.originalFile
    val realOffset = parameters.offset

    // Get language support and checkReferences if completion is not being called in the comments
    val supporter = request.supporter
    val language = request.supporter.language

    val firstTokenStorage = TabSelectedItemStorage.getInstance()
    val prefix = result.prefixMatcher.prefix.let { pref ->
      // If completion called in string - prefix would be last word
      if (supporter.isStringElement(parameters.position) && firstTokenStorage.getSavedProposal() == null) {
        pref.takeLastWhile { !it.isWhitespace() }
      }
      else {
        pref
      }
    }
    val head = firstTokenStorage.prefixFromPreviousSession().removeSuffix(prefix)

    val fullLineResultSet = result.withPrefixMatcher(CamelHumpMatcher(head + prefix)).apply {
      addLookupAdvertisement(message("full.line.lookup.advertisement.tab"))
      restartCompletionWhenNothingMatches()
    }

    val cache = recentRequestsCache.getOrInitializeCache(
      file.projectFilePath(),
      realOffset - result.prefixMatcher.prefix.length - head.length
    )

    val itemsHolder = SuggestionHolder()

    val limit = MLServerCompletionSettings.getInstance().topN() ?: Int.MAX_VALUE

    val resultSet = ResultSetWrapperImpl(result, BoundedResultSet(fullLineResultSet, cache, itemsHolder, limit), itemsHolder) {
      FullLineWeigher.customizeSorter(
        CompletionSorter.defaultSorter(parameters, result.prefixMatcher)
      )
    }


    val filters = mutableListOf<ProposalsFilter>(
      ASCIIFilter,
      ScoreFilter,
      EmptyStringFilter,
      ProhibitedWordsFilter,
      SemanticFilter,
      RepetitionFilter,
    )
    if (prefix.isNotEmpty()) {
      filters.add(SameAsPrefixFilter(prefix))
    }
    filters.add(itemsHolder.duplicateFilter())
    if (MLServerCompletionSettings.getInstance().hideRedCode(language)) {
      filters.add(RedCodeFilter)
    }

    val config = request.config
    filters.addAll(config.filters)

    val rawTransformers = mutableListOf(
      request.config.transformer,
      IncompleteWordTransformer,
      TrimTransformer,
    )
    if (MLServerCompletionSettings.getInstance().getModelState(supporter.language).psiBased) {
      rawTransformers.add(ProposalTransformer.reformatCode(supporter, file, parameters.offset, prefix))
    }

    val merger = FullLinePipeline(
      filters = filters,
      rawTransformers = rawTransformers,
      grouper = SameTextGrouper,
      analyzer = expensiveAnalyzer(supporter, file, realOffset, parameters.originalPosition, prefix),
      mapper = ProposalsMapper.create(supporter, head, prefix),
      resultWrapper = resultSet
    )

    val cachedCopy = cache.toList()
    cache.clear() // don't cache tab-selected proposal

    val tabSelectedProposal = firstTokenStorage.getSavedProposal()
    if (tabSelectedProposal != null) {
      merger.addTabSelected(tabSelectedProposal)
    }

    val filteredCache = cachedCopy.filter { it.suggestion.startsWith(head) }
    merger.addAnalyzedProposals(filteredCache)

    val providers = if (merger.hasSpace()) FullLineCompletionProvider.getSuitable(language) else emptyList()

    val query = request.createQuery(prefix)

    val fullLineRequest = AsyncFullLineRequest.requestAllAsync(providers, query, head)
    val combiningCallback = ProposalsMergingCallback(merger, fullLineRequest)
    if (shouldRequestStandardContributors(head)) {
      result.runRemainingContributors(parameters, combiningCallback)
      combiningCallback.waitForResponse(Registry.get("full.line.server.host.max.latency").asInteger())
    }
    else {
      result.stopHere()
      combiningCallback.waitForResponse(Registry.get("full.line.server.host.max.latency").asInteger())
    }
  }

  companion object {
    private val LOG = logger<FullLineContributor>()

    fun shouldRequestStandardContributors(head: String): Boolean {
      return head.isEmpty() && !Registry.`is`("full.line.only.proposals")
    }
  }

  // Implementation details are below - classes to ensure proper ordering and count of FL proposals

  private class ProposalsMergingCallback(
    private val merger: FullLineMerger,
    private val request: AsyncFullLineRequest
  ) : Consumer<CompletionResult> {

    private var fullLineResponseProceed: Boolean = false
    override fun consume(result: CompletionResult) {
      checkServerAnswerImmediately()
      merger.addStandardResult(result)
    }

    private fun checkServerAnswerImmediately() {
      if (fullLineResponseProceed) return

      val availableProposals = request.getAvailableProposals()
      if (availableProposals.isNotEmpty()) {
        merger.addRawProposals(availableProposals)
      }

      if (request.isFinished()) {
        fullLineResponseProceed = true
      }
    }

    fun waitForResponse(timeout: Int) {
      checkServerAnswer(System.currentTimeMillis(), timeout)
    }

    private fun checkServerAnswer(start: Long, timeout: Int) {
      checkServerAnswerImmediately()
      val endTime = start + timeout
      var waitFor = endTime - System.currentTimeMillis()
      while (!fullLineResponseProceed && waitFor >= 0) {
        val proposals = request.waitForProposals(waitFor)

        if (proposals.isNotEmpty()) merger.addRawProposals(proposals)

        if (request.isFinished()) {
          fullLineResponseProceed = true
        }

        waitFor = endTime - System.currentTimeMillis()
      }
    }
  }

  // Contains logic to ensure proper sorting
  private class ResultSetWrapperImpl(
    private val stdResultSet: CompletionResultSet,
    private val fullLineResultSet: BoundedResultSet,
    private val itemsHolder: SuggestionHolder,
    defaultSorterFactory: () -> CompletionSorter
  ) : ResultSetWrapper {
    private val fullLineSorter: CompletionSorter by lazy { defaultSorterFactory() }
    private var firstSorter: CompletionSorter? = null

    override fun addStandard(result: CompletionResult) {
      if (firstSorter == null) {
        firstSorter = FullLineWeigher.customizeSorter(result.sorter)
      }

      val sorter = firstSorter
      if (sorter != null) {
        val newResult = CompletionResult.wrap(result.lookupElement, result.prefixMatcher, sorter)
        if (newResult != null) {
          stdResultSet.passResult(newResult)
          itemsHolder.standardAdded(newResult.lookupElement.lookupString)
          return
        }
      }

      stdResultSet.passResult(result)
    }

    override fun addFullLine(proposals: List<FullLineLookupElement>) {
      val sorter = firstSorter ?: fullLineSorter
      val results = proposals.mapNotNull { CompletionResult.wrap(it, fullLineResultSet.prefixMatcher(), sorter) }
      fullLineResultSet.addInBatch(results)
    }

    override fun hasSpaceForFullLine(): Boolean {
      return !fullLineResultSet.isLimitReached()
    }
  }

  private class BoundedResultSet(
    private val delegate: CompletionResultSet,
    private val cache: MutableList<AnalyzedFullLineProposal>,
    private val itemsHolder: SuggestionHolder,
    private val limit: Int
  ) {
    private var added: Int = 0
    fun addInBatch(flResults: List<CompletionResult>) {
      if (isLimitReached()) return
      delegate.startBatch()
      flResults.take(limit - added).forEach {
        delegate.passResult(it)
        // TODO: make the code clearer
        val element = (it.lookupElement as FullLineLookupElement).proposal
        cache.add(element)
        itemsHolder.fullLineAdded(element.suggestion)
        added += 1
      }
      delegate.endBatch()
    }

    fun prefixMatcher(): PrefixMatcher = delegate.prefixMatcher

    fun isLimitReached(): Boolean {
      LOG.assertTrue(added <= limit)
      return added >= limit
    }
  }

  private fun expensiveAnalyzer(
    supporter: FullLineLanguageSupporter,
    file: PsiFile,
    offset: Int,
    position: PsiElement?,
    prefix: String
  ): TextProposalsAnalyzer {
    return object : TextProposalsAnalyzer {
      override fun analyze(proposal: RawFullLineProposal): AnalyzedFullLineProposal {
        val suggestion = proposal.suggestion
        val suffix = supporter.getMissingBraces(suggestion, position, offset)?.joinToString("") ?: ""
        val checkRedCode = MLServerCompletionSettings.getInstance().checkRedCode(supporter.language)
        val isCorrect = when {
          DumbService.isDumb(file.project) -> ReferenceCorrectness.UNDEFINED
          checkRedCode -> supporter.isCorrect(file, suggestion + suffix, offset, prefix)
          else -> ReferenceCorrectness.UNDEFINED
        }

        return AnalyzedFullLineProposal(proposal, suffix, isCorrect)
      }
    }
  }
}
