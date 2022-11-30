package org.jetbrains.completion.full.line.local.generation.generation

import io.kinference.model.ExecutionContext
import org.jetbrains.completion.full.line.local.generation.LapTimer
import org.jetbrains.completion.full.line.local.generation.matcher.FullLinePrefixMatcher
import org.jetbrains.completion.full.line.local.generation.model.ModelWrapper
import org.jetbrains.completion.full.line.local.generation.search.FullLineBeamSearch
import org.jetbrains.completion.full.line.local.generation.search.Search
import org.jetbrains.completion.full.line.local.tokenizer.Tokenizer
import kotlin.math.pow

class FullLineGeneration(
  model: ModelWrapper, tokenizer: Tokenizer, private val loggingCallback: ((String) -> Unit)? = null
) : BaseGeneration<FullLineGenerationConfig>(model, tokenizer) {
  override val prefixMatcher = FullLinePrefixMatcher(tokenizer)
  internal val oneTokenEosRegex = Regex("\\W+")

  override fun generate(
    context: IntArray, prefix: String, config: FullLineGenerationConfig, execContext: ExecutionContext
  ): List<List<GenerationInfo>> {
    val stashIds = tokenizer.idsByRegex(config.stashRegex)
    val terminateIds = tokenizer.idsByRegex(config.terminateRegex)

    val stashedHypotheses: MutableList<Search.Hypothesis> = mutableListOf()
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

      // Stashing
      val stashedIndices = search.hypotheses.mapIndexed { i, it ->
        if (it.ids.last() in stashIds) return@mapIndexed i
        return@mapIndexed null
      }.filterNotNull()
      if (stashedIndices.isNotEmpty()) {
        val newStashedHypotheses = stashedIndices.map { search.hypotheses.elementAt(it) }
        stashedHypotheses.addAll(newStashedHypotheses)
        timer?.lap("$i: stash hypotheses")
      }
      // Terminating
      val terminatedIndices = search.hypotheses.mapIndexed { i, it ->
        if (it.ids.last() in terminateIds) return@mapIndexed i
        return@mapIndexed null
      }.filterNotNull()
      if (terminatedIndices.isNotEmpty()) {
        search.dropHypotheses(terminatedIndices)
        if (search.hypotheses.isEmpty()) break
        timer?.lap("$i: terminate hypotheses")
      }

      loggingCallback?.invoke("Unterminated variants:\n${search.hypotheses.logString()}")
      loggingCallback?.invoke("Terminated variants:\n${stashedHypotheses.logString()}")
      timer?.lap("$i: logging tokens")

      // TODO: find out whether we want this line or some other solution
      if (config.oneTokenMode && stashedHypotheses.isNotEmpty()) break

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

    loggingCallback?.invoke("All returned hypotheses:\n${stashedHypotheses.logString()}")

    return listOf(stashedHypotheses.sortedByDescending { it.score }.map { hyp ->
      val len = hyp.ids.size
      // TODO: clean eachStepScore implementation
      val eachStepScore = hyp.score.pow(1.0 / len)
      GenerationInfo(List(len) { eachStepScore }, hyp.ids.toList())
    })
  }

  private fun List<Search.Hypothesis>.logString(): String {
    if (this.isEmpty()) return "No hypotheses"
    return this.sortedByDescending { it.score }.joinToString("\n") {
      logItemOpen + tokenizer.decode(it.ids, "|") + logItemClose + " " + it.score
    }
  }

  companion object {
    private const val logItemOpen = ">>>"
    private const val logItemClose = "<<<"
  }
}
