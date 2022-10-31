package org.jetbrains.completion.full.line.local.generation.generation

import io.kinference.model.ExecutionContext
import org.jetbrains.completion.full.line.local.generation.LapTimer
import org.jetbrains.completion.full.line.local.generation.matcher.FullLinePrefixMatcher
import org.jetbrains.completion.full.line.local.generation.model.ModelWrapper
import org.jetbrains.completion.full.line.local.generation.search.FullLineBeamSearch
import org.jetbrains.completion.full.line.local.generation.search.Search
import org.jetbrains.completion.full.line.local.tokenizer.Tokenizer
import kotlin.math.min
import kotlin.math.pow

class FullLineGeneration(
  model: ModelWrapper, tokenizer: Tokenizer, private val loggingCallback: ((String) -> Unit)? = null
) : BaseGeneration<FullLineGenerationConfig>(model, tokenizer) {
  override val prefixMatcher = FullLinePrefixMatcher(tokenizer)
  private val eosIds = setOf(tokenizer.encode("\n").last())
  internal val oneTokenEosRegex = Regex("\\W+")

  override fun generate(
    context: IntArray, prefix: String, config: FullLineGenerationConfig, execContext: ExecutionContext
  ): List<List<GenerationInfo>> {
    val terminatedHypotheses: MutableList<Search.Hypothesis> = mutableListOf()
    val search = FullLineBeamSearch(
      vocabSize = tokenizer.vocabSize,
      searchSize = config.numBeams,
      lenNormBase = config.lenNormBase,
      lenNormPow = config.lenNormPow
    )

    loggingCallback?.let {
      it("Prefix is $logItemOpen$prefix$logItemClose")
      it("Context length is ${context.size}")
      it("Doing ${config.maxLen} iterations")
      it("Beam search started")
    }

    val timer = loggingCallback?.let { LapTimer() }

    execContext.checkCancelled.invoke()
    initState(prefix, config)
    timer?.lap("initState")
    initLogProbs(context, execContext)
    timer?.lap("initLogProbs")
    sortState(IntArray(search.batchSize))
    timer?.lap("sortState")

    for (i in 0 until config.maxLen) {
      execContext.checkCancelled.invoke()
      loggingCallback?.invoke("Performing step $i")
      search.step(nextLogProbs!!, context)
      timer?.lap("$i: step")

      updateState(search.sortMask, search.lastPredictions)
      timer?.lap("$i: updateState after step")

      val terminatedIndices = search.hypotheses.terminatedIndices(config.oneTokenMode, prefix)
      if (terminatedIndices.isNotEmpty()) {
        val newTerminatedHypotheses = search.dropHypotheses(terminatedIndices)
        terminatedHypotheses.addAll(newTerminatedHypotheses)
        if (search.hypotheses.isEmpty()) break
        timer?.lap("$i: stash terminated hypotheses")
      }

      loggingCallback?.invoke("Unterminated variants:\n${search.hypotheses.logString()}")
      loggingCallback?.invoke("Terminated variants:\n${terminatedHypotheses.logString()}")
      timer?.lap("$i: logging tokens")

      // TODO: find out whether we want this line or some other solution
      if (config.oneTokenMode && terminatedHypotheses.isNotEmpty()) break

      if (i < config.maxLen - 1) {
        if (terminatedIndices.isNotEmpty()) {
          sortState(search.sortMask)
          timer?.lap("$i: sortState after termination")
        }
        updateLogProbs(search.lastPredictions, execContext)
        timer?.lap("$i: updateLogProbs")
      }
    }

    timer?.let {
      it.end()
      it.laps.forEach { lap -> loggingCallback?.invoke("${lap.name} took ${lap.timeMillis} ms") }
      loggingCallback?.invoke("Beam search ended in ${it.endTime - it.startTime} ms")
    }
    resetState()
    val allHypotheses =
      if (config.oneTokenMode) terminatedHypotheses else (terminatedHypotheses + search.hypotheses)

    loggingCallback?.invoke("All returned hypotheses:\n${allHypotheses.logString()}")

    return listOf(allHypotheses.sortedByDescending { it.score }.map { hyp ->
      val len = hyp.ids.size
      // TODO: clean eachStepScore implementation
      val eachStepScore = hyp.score.pow(1.0 / len)
      GenerationInfo(List(len) { eachStepScore }, hyp.ids.toList())
    })
  }

  private fun List<Search.Hypothesis>.terminatedIndices(terminateByWords: Boolean, prefix: String): List<Int> {
    return this.mapIndexed { i, it ->
      val lastTokenId = it.ids.last()
      if (lastTokenId in eosIds) return@mapIndexed i
      if (terminateByWords) {
        val hypothesisText = tokenizer.decode(it.ids)
        var newSequence = hypothesisText
        if (prefix.isNotEmpty()) {
          newSequence = findFirstSuffixLastPrefix(prefix, hypothesisText)
        }
        if (newSequence.contains(oneTokenEosRegex) && !newSequence.matches(oneTokenEosRegex)) return@mapIndexed i
      }
      return@mapIndexed null
    }.filterNotNull()
  }

  private fun List<Search.Hypothesis>.logString(): String {
    if (this.isEmpty()) return "No hypotheses"
    return this.sortedByDescending { it.score }.joinToString("\n") {
      logItemOpen + tokenizer.decode(
        it.ids
      ) + logItemClose + " " + it.score
    }
  }

  private fun findFirstSuffixLastPrefix(first: String, last: String): String {
    for (len in 1..min(first.length, last.length)) {
      if (first.regionMatches(first.length - len, last, 0, len)) {
        return last.substring(len)
      }
    }
    return ""
  }

  companion object {
    private const val logItemOpen = ">>>"
    private const val logItemClose = "<<<"
  }
}


