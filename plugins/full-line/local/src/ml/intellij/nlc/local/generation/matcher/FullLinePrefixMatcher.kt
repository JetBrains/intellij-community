package ml.intellij.nlc.local.generation.matcher

import ml.intellij.nlc.local.tokenizer.Tokenizer
import ml.intellij.nlc.local.tokenizer.TokenizerTrie

internal class FullLinePrefixMatcher(val tokenizer: Tokenizer) : PrefixMatcher() {
  private val trie = TokenizerTrie(tokenizer)
  private val vocabIds = tokenizer.vocab.values.toSet()

  override fun prefixTokensByErr(prefix: String, errLimit: Int): Array<IntArray> {
    if (errLimit > 0) {
      throw IllegalArgumentException("errLimit greater than 0 is not supported")
    }
    val values = trie.getValuesWithCompletionAwarePrefix(prefix)
    return Array(2) {
      if (it == 0) vocabIds.minus(values.toList()).toIntArray()
      else values
    }
  }

}