package org.jetbrains.completion.full.line.platform

import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.completion.full.line.*
import org.jetbrains.completion.full.line.language.FullLineLanguageSupporter
import org.jetbrains.completion.full.line.platform.diagnostics.FullLinePart
import org.jetbrains.completion.full.line.platform.diagnostics.logger

// Top-level interface to feed standard and FL proposals.
// Contains logic for transforming, filtering, grouping, analyzing FL proposals
interface FullLineMerger {
  fun addStandardResult(result: CompletionResult)
  fun addRawProposals(proposals: List<RawFullLineProposal>)
  fun addAnalyzedProposals(proposals: List<AnalyzedFullLineProposal>)

  fun hasSpace(): Boolean
}

class FullLinePipeline(
  private val filters: List<ProposalsFilter>,
  private val rawTransformers: List<ProposalTransformer>,
  private val resultWrapper: ResultSetWrapper,
  private val grouper: RawProposalsGrouper,
  private val analyzer: TextProposalsAnalyzer,
  private val mapper: ProposalsMapper
) : FullLineMerger {

  override fun addStandardResult(result: CompletionResult) {
    val element = result.lookupElement
    val reason = checkStandard(element)
    if (reason == null) {
      resultWrapper.addStandard(result)
    }
    else {
      LOG.debug("Standard proposal \"${element.lookupString}\" filtered out: $reason")
    }
  }

  override fun addRawProposals(proposals: List<RawFullLineProposal>) {
    if (proposals.isEmpty()) return
    logPipelineStarted(proposals)
    val transformed = proposals.map {
      rawTransformers.fold(it) { proposal, transformer -> transformer.transform(proposal) }
    }

    logMappingDiagnostics(proposals, transformed, rawTransformers.javaClass.name)

    val grouped = grouper.groupSimilar(transformed)

    logGroupingDiagnostics(grouped, grouper.javaClass.name)

    val groupedItems = grouped.keys.toList()
    val filteredRaw = filterWithDiagnostics(groupedItems) { checkRawFullLine(it) }

    val analyzed = filteredRaw.analyzeWithDiagnostics()
    addAnalyzedProposals(analyzed)
  }

  override fun addAnalyzedProposals(proposals: List<AnalyzedFullLineProposal>) {
    val filteredAnalyzed = filterWithDiagnostics(proposals) { checkAnalyzedFullLine(it) }

    resultWrapper.addFullLine(filteredAnalyzed.map { mapper.createLookupElement(it, false) })
  }

  private fun logPipelineStarted(proposals: List<RawFullLineProposal>) {
    if (!LOG.isDebugEnabled) return
    LOG.debug("Got ${proposals.size} raw full line proposals")
    val longest = proposals.maxOf { it.suggestion.length }
    for (proposal in proposals) {
      LOG.debug("\t${proposal.suggestion.padEnd(longest)} [${proposal.describe()}]")
    }
  }

  private fun logMappingDiagnostics(
    before: List<FullLineProposal>,
    after: List<FullLineProposal>,
    description: String
  ) {
    if (!LOG.isDebugEnabled) return
    val changes = before.zip(after).filter { it.first.suggestion != it.second.suggestion }
    if (changes.isNotEmpty()) {
      val longest = changes.maxOf { it.first.suggestion.length }
      LOG.debug("Some elements were transformed due to $description")
      for ((b, a) in changes) {
        LOG.debug("\t${b.suggestion.padEnd(longest)} -> ${a.suggestion}")
      }
    }
  }

  private fun List<RawFullLineProposal>.analyzeWithDiagnostics(): List<AnalyzedFullLineProposal> {
    if (isEmpty()) return emptyList()
    if (!LOG.isDebugEnabled) return map { analyzer.analyze(it) }
    LOG.debug("Start analyzing $size proposals...")
    val result = mutableListOf<AnalyzedFullLineProposal>()
    for (raw in this) {
      val (time, analyzed) = measureTimeMillisWithResult { analyzer.analyze(raw) }
      LOG.debug("Analyzing \"${analyzed.suggestion}\" took $time ms: [${analyzed.describe()}]")
      analyzed.details.checksTime = time.toInt()
      result.add(analyzed)
    }

    return result
  }

  private fun checkStandard(element: LookupElement): String? {
    return filters.firstOrNull { !it.checkStandard(element) }?.description
  }

  private fun <T : FullLineProposal> filterWithDiagnostics(items: List<T>, predicate: ProposalsFilter.(T) -> Boolean): List<T> {
    var result: List<T> = items
    for (filter in filters) {
      val beforeFiltering = result
      result = beforeFiltering.filter { filter.predicate(it) }
      logFilteringDiagnostics(beforeFiltering, result, filter.description)
    }
    return result
  }

  private fun RawFullLineProposal.describe(): String {
    return buildString {
      append("score: $score, basic correctness: $isSyntaxCorrect")

      details.provider?.let {
        append(", provider: $it")
      }

      details.cacheHitLength?.let {
        append(", cacheHitLength: $it")
      }
    }
  }

  private fun AnalyzedFullLineProposal.describe(): String {
    return "correctness: $refCorrectness, suffix: $suffix"
  }

  private fun logGroupingDiagnostics(
    grouped: Map<RawFullLineProposal, List<RawFullLineProposal>>,
    description: String
  ) {
    if (!LOG.isDebugEnabled) return
    val merges = grouped.entries.filter { it.value.size != 1 }
    if (merges.isNotEmpty()) {
      LOG.debug("Some elements were grouped due to $description")
      for ((to, from) in merges) {
        LOG.debug("\t${to.suggestion} <- ${from.map { it.suggestion }}")
      }
    }
  }

  private fun logFilteringDiagnostics(
    before: List<FullLineProposal>,
    after: List<FullLineProposal>,
    description: String
  ) {
    if (!LOG.isDebugEnabled) return
    if (before.size != after.size) {
      LOG.debug("Some elements were filtered out due to $description:")
      val afterSet = after.toSet()
      for (b in before) {
        if (b !in afterSet) {
          LOG.debug("\t${b.suggestion}")
        }
      }
    }
  }

  // TODO: move to interface?
  fun addTabSelected(proposal: AnalyzedFullLineProposal) {
    resultWrapper.addFullLine(listOf(mapper.createLookupElement(proposal, true)))
  }

  override fun hasSpace(): Boolean {
    return resultWrapper.hasSpaceForFullLine()
  }

  private companion object {
    private val LOG = logger<FullLinePipeline>(FullLinePart.PRE_PROCESSING)
  }
}

// Wrapper over standard result sets
interface ResultSetWrapper {
  fun addStandard(result: CompletionResult)
  fun addFullLine(proposals: List<FullLineLookupElement>)

  fun hasSpaceForFullLine(): Boolean
}

// Groups similar proposals and keep only one proposal from each group to show
interface RawProposalsGrouper {
  fun groupSimilar(proposals: List<RawFullLineProposal>): Map<RawFullLineProposal, List<RawFullLineProposal>>
}

// Creates LookupElement for FL proposals
interface ProposalsMapper {
  fun createLookupElement(proposal: AnalyzedFullLineProposal, selectedByTab: Boolean): FullLineLookupElement

  companion object {
    fun create(supporter: FullLineLanguageSupporter, head: String, prefix: String): ProposalsMapper {
      return object : ProposalsMapper {
        override fun createLookupElement(
          proposal: AnalyzedFullLineProposal,
          selectedByTab: Boolean
        ): FullLineLookupElement {
          return FullLineLookupElement(head, prefix, proposal, supporter, selectedByTab)
        }
      }
    }
  }
}

object SameTextGrouper : RawProposalsGrouper {
  override fun groupSimilar(proposals: List<RawFullLineProposal>): Map<RawFullLineProposal, List<RawFullLineProposal>> {
    val grouped = proposals.groupByTo(linkedMapOf()) { it.suggestion }
    val result = linkedMapOf<RawFullLineProposal, List<RawFullLineProposal>>()
    for ((_, items) in grouped) {
      if (items.size == 1) {
        result[items.single()] = items
      }
      else {
        val chosen = items.maxByOrNull { it.score } ?: items.single()
        result[chosen] = items
      }
    }

    return result
  }
}
