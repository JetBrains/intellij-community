package ml.intellij.nlc.local.suggest.ranking

import ml.intellij.nlc.local.CompletionModel
import ml.intellij.nlc.local.tokenizer.BPETokenizer
import java.lang.Double.min
import java.lang.Integer.max
import java.lang.Math.abs

internal data class PrefixState(val prefix: String, val prob: Double, val tab_num: Int)

internal class GolfTrie(private val tokenizer: BPETokenizer,
                        private val prefixState: PrefixState = PrefixState("", 1.0, 0),
                        initScores: DoubleArray? = null,
                        prefix: String = ""
) {
  private var scores: DoubleArray = initScores ?: DoubleArray(50) { i -> max(0, i - prefix.length).toDouble() }
  private val children = HashMap<String, GolfTrie>()
  var updated = false

  private fun copyByPath(words: List<String>): GolfTrie {
    val res = GolfTrie(tokenizer, prefixState.copy(), scores.clone())
    res.updated = updated

    if (words.isEmpty()) {
      return res
    }

    val prefix = prefixState.prefix + words[0]
    if (prefix in children) {
      res.children[prefix] = children[prefix]!!.copyByPath(words.subList(1, words.size))
    }
    return res
  }

  private fun toWords(completion: CompletionModel.CompletionResult): Pair<List<String>, List<Double>> {
    val tokens = completion.info.ids.map { tokenizer.decode(it) }
    val probs = completion.info.probs

    val words = ArrayList<String>()
    val wProbs = ArrayList<Double>()

    var cumProb = 1.0
    var prefix = ""
    for ((token_i, token) in tokens.withIndex()) {
      prefix += token
      cumProb *= probs[token_i]

      val wordEnded = token_i == tokens.size - 1 || tokens[token_i + 1][0] == ' '
      if (wordEnded) {
        words.add(prefix)
        wProbs.add(cumProb)
        prefix = ""
        cumProb = 1.0
      }
    }
    return Pair(words, wProbs)
  }

  private fun patchedTrie(words: List<String>, probs: List<Double>, candPos: Int): Pair<GolfTrie, Double> {
    val resultTrie = copyByPath(words)
    assert(words.size == probs.size)

    var prefix = prefixState.prefix
    var prob = prefixState.prob
    var tabsNum = prefixState.tab_num

    var diff = 0.0
    if (!resultTrie.updated && prefixState.prefix != "") {
      var score = (1 - prob) * scores[prefix.length] + prob * (tabsNum + candPos)
      var newScore = min(resultTrie.scores[prefix.length], score)
      diff += resultTrie.scores[prefix.length] - newScore
      resultTrie.scores[prefix.length] = newScore
      // backspace
      for (i in prefix.length - 1..0) {
        score = (1 - prob) * scores[i] + prob * (tabsNum + candPos + abs(prefix.length - i))
        newScore = min(resultTrie.scores[i], score)
        diff += resultTrie.scores[i] - newScore
        resultTrie.scores[i] = newScore
      }
      // ordinary typing
      for (i in prefix.length + 1 until scores.size) {
        score = (1 - prob) * scores[i] + prob * (tabsNum + candPos + abs(prefix.length - i))
        newScore = min(resultTrie.scores[i], score)
        diff += resultTrie.scores[i] - newScore
        resultTrie.scores[i] = newScore
      }
      resultTrie.updated = true
    }

    if (words.isEmpty()) {
      return Pair(resultTrie, diff)
    }

    val tabPenalty = 0
    prefix = prefixState.prefix + words[0]
    prob = prefixState.prob * probs[0]
    tabsNum = prefixState.tab_num + 1 + tabPenalty
    if (words.size == 1) {
      tabsNum = 1
    }

    val child = if (prefix in resultTrie.children) {
      resultTrie.children[prefix]!!
    }
    else {
      GolfTrie(tokenizer, PrefixState(prefix, prob, tabsNum), resultTrie.scores.clone())
    }

    val (newChild, diff_add) = child.patchedTrie(words.subList(1, words.size), probs.subList(1, probs.size), candPos)
    resultTrie.children[prefix] = newChild
    diff += diff_add
    // if len(words) > 1:
    //    default_new = GolfTrie(self.tokenizer, PrefixState(prefix, prob, tabs_num), copy.deepcopy(result_trie.scores))
    //     result_trie.children[prefix] = result_trie.children.get(prefix, default_new)._patched_trie(words[1:], probs[1:], cand_pos)

    return Pair(resultTrie, diff)
  }

  fun scorePatch(completion: CompletionModel.CompletionResult, pos: Int): Double {
    val (words, probs) = toWords(completion)
    return patchedTrie(words, probs, pos).second
  }

  private fun mergeTries(trie: GolfTrie) {
    for ((word, child) in trie.children.entries) {
      if (!children.containsKey(word)) {
        children[word] = child
      }
      else {
        children[word]!!.mergeTries(child)
      }
    }
    scores = trie.scores
    updated = trie.updated
  }

  fun update(completion: CompletionModel.CompletionResult, pos: Int) {
    val (words, probs) = toWords(completion)
    val newTrie = patchedTrie(words, probs, pos).first

    mergeTries(newTrie)
  }
}


internal class WordTrieIterativeGolfRanking(internal val tokenizer: BPETokenizer,
                                            private val numSeqs: Int,
                                            private val minFirstScore: Double) : RankingModel {
  override fun rank(context: String, prefix: String,
                    completions: List<CompletionModel.CompletionResult>): List<CompletionModel.CompletionResult> {
    val score = GolfTrie(tokenizer, prefix = prefix)
    val topListed = ArrayList<CompletionModel.CompletionResult>()
    val numIters = Integer.min(completions.size, numSeqs)
    var currentVariants = completions
    for (i in 0 until numIters) {
      val scoredVariants = step(currentVariants, score, topListed.size)
      if (scoredVariants.isEmpty()) {
        break
      }
      val (bestScore, bestCompletion) = scoredVariants[0]
      if (i == 0 && bestScore < minFirstScore) {
        break
      }
      score.update(bestCompletion, i)
      topListed.add(bestCompletion)
      currentVariants = scoredVariants.subList(1, scoredVariants.size).map { it.second }
    }
    return topListed
  }

  private fun step(completions: List<CompletionModel.CompletionResult>, score: GolfTrie,
                   pos: Int = 0): List<Pair<Double, CompletionModel.CompletionResult>> {
    val completionsWithScores = completions.map { Pair(score.scorePatch(it, pos), it) }.sortedByDescending { it.first }
    val result = ArrayList<Pair<Double, CompletionModel.CompletionResult>>()
    for (scored_completion in completionsWithScores) {
      //            if (scored_completion.first > 0.4 * pow((pos + result.size).toDouble(), 0.7))
      result.add(scored_completion)
    }
    return result
  }
}
