package org.jetbrains.completion.full.line.local.suggest.collector

import io.kinference.model.ExecutionContext
import org.jetbrains.completion.full.line.local.CompletionModel
import org.jetbrains.completion.full.line.local.generation.generation.FullLineGeneration
import org.jetbrains.completion.full.line.local.generation.generation.FullLineGenerationConfig
import org.jetbrains.completion.full.line.local.generation.model.ModelWrapper
import org.jetbrains.completion.full.line.local.tokenizer.Tokenizer
import org.jetbrains.completion.full.line.local.tokenizer.TokenizerTrie
import java.io.File
import kotlin.math.abs
import kotlin.math.max

internal class FullLineCompletionsGenerator(
  model: ModelWrapper, tokenizer: Tokenizer, loggingCallback: ((String) -> Unit)? = null
) : BaseCompletionsGenerator<FullLineGenerationConfig>(model, tokenizer) {
  val generation = FullLineGeneration(model, tokenizer, loggingCallback)
  private val trie = TokenizerTrie(tokenizer)
  private val vocabTokenLengths = tokenizer.vocab.map { it.key.length }.sorted()
  internal val tokenLengthThreshold = vocabTokenLengths[(vocabTokenLengths.size * 0.999).toInt()]

  override fun generateWithSearch(
    context: String, prefix: String, config: FullLineGenerationConfig, execContext: ExecutionContext
  ): List<CompletionModel.CompletionResult> {
    val (contextIds, newPrefix) = makeCompletionInput(context, config)
    val rawSuggestions = generation.generate(
      contextIds, newPrefix, config, execContext
    )[0]

    var completionResults = rawSuggestions.asSequence()
      .filter { it.ids.isNotEmpty() }
      .map {
        val decodedText = tokenizer.decode(it.ids)
        val stashRegexMatches = config.stashRegex.findAll(decodedText)
        if (stashRegexMatches.none()) { return@map null }
        CompletionModel.CompletionResult(decodedText.substring(0..stashRegexMatches.last().range.last), it)
      }
      .filterNotNull()
      .filter { it.text.length > newPrefix.length }
      .map { CompletionModel.CompletionResult(it.text.mergePrefixes(prefix, newPrefix), it.info) }

    // TODO: replace \n with eosIds
    completionResults = if (config.oneTokenMode) {
      completionResults.map {
        CompletionModel.CompletionResult(
          it.text.substring(
            0,
            generation.oneTokenEosRegex.find(it.text)?.range?.start ?: it.text.length
          ), it.info
        )
      }
    }
    else {
      completionResults.map {
        CompletionModel.CompletionResult(it.text.substringBefore("\n"), it.info)
      }
    }
    return completionResults.toList()
  }

  private fun String.mergePrefixes(prefix1: String, prefix2: String): String {
    val prefixLenDiff = abs(prefix1.length - prefix2.length)
    return if (prefix1.length > prefix2.length) {
      val cutPrefix = prefix1.substring(0, prefixLenDiff)
      cutPrefix + this
    }
    else {
      this.substring(prefixLenDiff)
    }
  }

  internal fun makeCompletionInput(
    context: String,
    config: FullLineGenerationConfig
  ): Pair<IntArray, String> {
    val (contextRolledBack, suffix) = resolveIncompleteContext(context)
    val contextIds = if (contextRolledBack.isNotEmpty()) tokenizer.encode(contextRolledBack) else intArrayOf(4)
    val metaInfo = composeMetaInfo(config)
    val metaInfoIds = tokenizer.encode(metaInfo)
    val combinedIds = model.composeInputIds(metaInfoIds, contextIds, config)
    return Pair(combinedIds, suffix)
  }

  private fun composeMetaInfo(config: FullLineGenerationConfig): String {
    val file = File(config.filename)
    return ".${file.extension}${config.languageSplitSymbol}${file.nameWithoutExtension}${config.metaInfoSplitSymbol}"
  }

  internal fun resolveIncompleteContext(context: String): Pair<String, String> {
    // TODO: implement auto-detection of split tokens in vocab, use them here
    val lastLine = context.split("\n", "⇥", "⇤").last()
    val prefix: String = findLongestSuffix(lastLine)
    val prepContext = if (prefix.isNotEmpty()) context.substring(
      0, context.length - prefix.length
    )
    else context
    return Pair(prepContext, prefix)
  }

  private fun findLongestSuffix(string: String): String {
    if (string.isEmpty()) return ""

    val str = string.substring(max(string.length - tokenLengthThreshold, 0))
    for (i in 0..str.length) {
      val suffix = str.substring(i)
      var foundIds = trie.getValuesWithPrefix(suffix, true)
      foundIds = filterSuffixes(str.substring(0, i), foundIds)
      if (foundIds.isNotEmpty()) {
        if ((foundIds.size == 1) && (tokenizer.decode(foundIds[0]) == suffix)) {
          // The last token is a full token, suffix isn't needed
          return ""
        }
        return suffix
      }
    }
    throw IllegalStateException("Could not find longest suffix for string $string")
  }

  private fun filterSuffixes(prefix: String, suffixIds: IntArray): IntArray {
    if (suffixIds.isEmpty()) {
      return intArrayOf()
    }
    val strings = suffixIds.map {
      prefix + tokenizer.decode(it)
    }
    val encodedStrings = tokenizer.encode(strings)
    return suffixIds.zip(encodedStrings).filter {
      it.second.last() == it.first
    }.map {
      it.first
    }.toIntArray()
  }
}
