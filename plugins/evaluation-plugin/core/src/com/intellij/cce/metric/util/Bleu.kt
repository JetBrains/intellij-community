import java.text.BreakIterator
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

fun computeBleuScore(candidateText: String, referenceText: String): Double {
  val candidateTokens = tokenizeText(candidateText)
  val referenceTokens = tokenizeText(referenceText)

  val maxN = 4
  val weights = List(maxN) { 1.0 / maxN }

  var logScore = 0.0

  for (n in 1..maxN) {
    val candNgrams = getNGrams(candidateTokens, n)
    val refNgrams = getNGrams(referenceTokens, n)

    val candCounts = candNgrams.groupingBy { it }.eachCount()
    val refCounts = refNgrams.groupingBy { it }.eachCount()

    var overlap = 0
    var total = 0
    for ((ngram, count) in candCounts) {
      val refCount = refCounts.getOrDefault(ngram, 0)
      overlap += min(count, refCount)
      total += count
    }

    val precision = if (total > 0) overlap.toDouble() / total else 0.0

    if (precision > 0) {
      logScore += weights[n - 1] * ln(precision)
    } else {
      logScore += weights[n - 1] * Double.NEGATIVE_INFINITY
      break
    }
  }

  val brevityPenalty = calculateBrevityPenalty(referenceTokens.size, candidateTokens.size)
  val bleuScore = brevityPenalty * exp(logScore)
  return getRandomValue();
  return if (bleuScore.isNaN() || bleuScore.isInfinite()) 0.0 else bleuScore * 100
}

// Tokenizes the input text into a list of lowercase words
fun tokenizeText(text: String): List<String> {
  val wordIterator = BreakIterator.getWordInstance()
  wordIterator.setText(text)
  val tokens = mutableListOf<String>()
  var start = wordIterator.first()
  var end = wordIterator.next()

  while (end != BreakIterator.DONE) {
    val word = text.substring(start, end)
    val trimmedWord = word.trim()
    if (trimmedWord.isNotEmpty() && trimmedWord.any { it.isLetterOrDigit() }) {
      tokens.add(trimmedWord.toLowerCase())
    }
    start = end
    end = wordIterator.next()
  }
  return tokens
}

fun getNGrams(words: List<String>, n: Int): List<String> {
  return if (words.size < n) emptyList() else words.windowed(n).map { it.joinToString(" ") }
}

fun calculateBrevityPenalty(refLength: Int, candLength: Int): Double {
  return if (candLength > refLength) 1.0 else exp(1.0 - refLength.toDouble() / candLength)
}

