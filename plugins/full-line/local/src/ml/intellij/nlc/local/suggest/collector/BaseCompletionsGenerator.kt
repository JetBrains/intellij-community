package ml.intellij.nlc.local.suggest.collector

import io.kinference.model.ExecutionContext
import ml.intellij.nlc.local.CompletionModel
import ml.intellij.nlc.local.LongLastLineException
import ml.intellij.nlc.local.generation.generation.BaseGenerationConfig
import ml.intellij.nlc.local.generation.model.ModelWrapper
import ml.intellij.nlc.local.tokenizer.Tokenizer
import kotlin.math.max
import kotlin.math.min

/**
 * Base class for all completions generators that trims and cleans up completions
 * got from beam-search or other implementation before passing it to the client.
 */
internal abstract class BaseCompletionsGenerator<GenerationConfig : BaseGenerationConfig>(
  internal val model: ModelWrapper, internal val tokenizer: Tokenizer
) : CompletionsGenerator<GenerationConfig> {
  protected abstract fun generateWithSearch(
    context: String, prefix: String, config: GenerationConfig, execContext: ExecutionContext
  ): List<CompletionModel.CompletionResult>

  override fun generate(
    context: String, prefix: String, config: GenerationConfig, execContext: ExecutionContext
  ): List<CompletionModel.CompletionResult> {
    if (context.isBlank()) return emptyList()

    val seenCompletions = HashSet<String>()
    val completions = generateWithSearch(context, prefix, config, execContext)
    val result = ArrayList<CompletionModel.CompletionResult>()

    for (completion in completions) {
      val trimmedCompletion = trimCompletion(completion)
      val oneSpecificChar = trimmedCompletion.text.length == 1 && !completion.text[0].isLetterOrDigit()
      val containsInvalidSymbols = !tokenizer.isValidString(trimmedCompletion.text)
      if (trimmedCompletion.text.isEmpty() || oneSpecificChar || containsInvalidSymbols) continue

      val words = trimmedCompletion.text.trim().split(' ')
      val targetLen = words.size

      if (targetLen < config.minLen || trimmedCompletion.text in seenCompletions) continue
      trimmedCompletion.info.wordLen = targetLen

      seenCompletions.add(trimmedCompletion.text)
      result.add(trimmedCompletion)
    }

    return result
  }

  internal fun makeContextIds(context: String, config: GenerationConfig, startIds: List<Int>?): IntArray {
    val contextIds = tokenizer.encode(context)
    return preprocessContextIds(contextIds, config, startIds)
  }

  private fun preprocessContextIds(
    contextIds: IntArray, config: GenerationConfig, startIds: List<Int>?
  ): IntArray {
    // TODO: explain magic number
    val maxPossibleContextLen = model.maxSeqLen - 6 - config.maxLen
    val requestedContextLen =
      config.maxContextLen?.takeIf { it in 1..maxPossibleContextLen } ?: maxPossibleContextLen
    val finalContextLen = min(requestedContextLen, maxPossibleContextLen)
    return cropContextIds(contextIds, finalContextLen, config.minContextLen, startIds)
  }

  protected open fun trimCompletion(completion: CompletionModel.CompletionResult): CompletionModel.CompletionResult {
    return completion
  }

  companion object {
    /**
     * Crops `contextIds` to fit in model input
     * @param contextIds Full tokenized context.
     * @param maxContextLen
     *  Model input capacity.
     *  The returned array is guaranteed to be shorter or equal to `maxContextLen`.
     * @param minContextLen
     *  If null, the longest possible context will be returned.
     *  If not null, consequent calls of this function with `contextIds` having a common prefix will return
     *  cropped context having the same start offset as frequent as possible, considering `minContextLen`.
     *  This means, if you call this function with a certain context, then add a few tokens to the context
     *  and call the function again, cropped context returned from the first call will likely begin at the
     *  same offset in `contextIds` as the context, returned by the second call
     *  (in other words, the first cropped context will be a prefix of the second one).
     *  The returned array is guaranteed to be longer or equal to `minContextLen` when it's possible,
     *  considering `startIds` (if `startIds` is not specified, always except when `contextIds` itself is shorter).
     * @param startIds If not null, crop context only on specified token ids.
     * @return subsequence of `contextIds` shorter or equal to `maxContextLen`
     */
    internal fun cropContextIds(
      contextIds: IntArray, maxContextLen: Int, minContextLen: Int?, startIds: List<Int>?
    ): IntArray {
      val caretPosition = contextIds.size
      var contextStartIndex = max(0, caretPosition - maxContextLen)

      val startIndices = when {
        startIds != null -> {
          val startIdsSet = startIds.toSet()
          var newStartIndices = contextIds.mapIndexed { index, item ->
            if (item in startIdsSet) {
              index
            }
            else {
              null
            }
          }.filterNotNull()
          if (minContextLen != null) {
            val persistentStartIndices = mutableListOf(0)

            for (i in newStartIndices.indices) {
              val lastPersistentIndex = persistentStartIndices.last()
              val maxCropPos = lastPersistentIndex + maxContextLen - minContextLen + 1

              val shortNextContext = i < newStartIndices.size - 1 && newStartIndices[i + 1] > maxCropPos
              val caretFarAway = i == newStartIndices.size - 1 && maxCropPos < caretPosition
              if (shortNextContext || caretFarAway) {
                persistentStartIndices.add(newStartIndices[i])
              }
            }

            newStartIndices = persistentStartIndices
          }
          newStartIndices
        }
        minContextLen != null -> (0..caretPosition step minContextLen).toList()
        else -> null
      }

      if (startIndices != null && startIndices.isNotEmpty()) {
        contextStartIndex =
          startIndices.firstOrNull { it >= contextStartIndex } ?: throw LongLastLineException()
      }
      return contextIds.copyOfRange(contextStartIndex, caretPosition)
    }
  }
}
