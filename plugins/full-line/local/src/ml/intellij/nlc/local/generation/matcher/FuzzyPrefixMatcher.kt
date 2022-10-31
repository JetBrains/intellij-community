package ml.intellij.nlc.local.generation.matcher

import com.github.benmanes.caffeine.cache.Cache
import ml.intellij.nlc.local.tokenizer.Tokenizer
import ml.intellij.nlc.local.utils.Caching
import kotlin.math.max
import kotlin.math.min

internal class FuzzyPrefixMatcher(val tokenizer: Tokenizer) : PrefixMatcher() {
  private data class Request(val prefix: String, val errLimit: Int)

  private val cache: Cache<Request, Array<IntArray>> = Caching.default()

  private val tokens: List<String>
  private val origIndices: IntArray
  private val trie: Trie

  data class MatchedSubTrie(val start: Int, val finish: Int, var errorsCount: Int)

  inner class Trie {
    private var start = tokenizer.vocabSize
    private var finish = 0
    private val dict = HashMap<Char, Trie>()

    fun add(word: String, ind: Int) {
      start = min(start, ind)
      finish = max(finish, ind)
      if (word.isEmpty()) {
        return
      }

      if (!dict.containsKey(word[0])) {
        dict[word[0]] = Trie()
      }
      dict[word[0]]!!.add(word.substring(1), ind)
    }

    fun prefixIndices(word: String, errLimit: Int = 0): List<MatchedSubTrie> {
      if (word.isEmpty() || dict.isEmpty()) {
        return listOf(MatchedSubTrie(start, finish + 1, 0))
      }

      if (!dict.containsKey(word[0])) {
        var minWithSuffix = finish
        for (node in dict.values) {
          minWithSuffix = min(minWithSuffix, node.start)
        }
        return listOf(MatchedSubTrie(start, minWithSuffix, 0))
      }

      if (word[0] == ' ') {
        return dict[word[0]]!!.prefixIndices(word.substring(1), errLimit)
      }

      val result: MutableList<MatchedSubTrie> = ArrayList()

      if (errLimit > 0) {
        for (symbol in dict.keys) {
          if (symbol == word[0]) {
            continue
          }
          else if (symbol.isLetter()) {
            result.addAll(dict[symbol]!!.prefixIndices(word.substring(1), errLimit - 1))  // replace
            result.addAll(dict[symbol]!!.prefixIndices(word, errLimit - 1))  // insert
          }
        }

        result.addAll(prefixIndices(word.substring(1), errLimit - 1))  // delete
        result.onEach { it.errorsCount++ }
      }

      result.addAll(dict[word[0]]!!.prefixIndices(word.substring(1), errLimit))  // correct

      return result
    }
  }

  init {
    val tokensIndices = Array(tokenizer.vocabSize) { Pair(it, tokenizer.decode(it)) }.sortedBy { it.second }
    origIndices = IntArray(tokensIndices.size) { tokensIndices[it].first }
    tokens = tokensIndices.map { it.second }

    trie = Trie()
    for (i in tokens.indices) {
      trie.add(tokens[i], i)
    }
  }

  override fun prefixTokensByErr(prefix: String, errLimit: Int): Array<IntArray> {
    if (errLimit < 0) {
      return arrayOf(origIndices)
    }
    return cache.get(Request(prefix, errLimit)) {
      val edges = trie.prefixIndices(prefix, errLimit).sortedBy { it.start * tokenizer.vocabSize + it.finish }

      var prevStart = 0
      val result = Array<MutableList<Int>>(errLimit + 2) { ArrayList() }

      for (subtrie in edges) {
        // TODO: it's tokenizer bug, arguments should be (prevStart, triple.first)
        //            if (prevStart < start) {
        result[0].addAll(origIndices.slice(prevStart until subtrie.start))
        //            }
        prevStart = subtrie.finish
        result[subtrie.errorsCount + 1].addAll(origIndices.slice(subtrie.start until subtrie.finish))
      }

      result[0].addAll(origIndices.slice(prevStart until origIndices.size))

      Array(result.size) { result[it].toIntArray() }
    }!!
  }
}
