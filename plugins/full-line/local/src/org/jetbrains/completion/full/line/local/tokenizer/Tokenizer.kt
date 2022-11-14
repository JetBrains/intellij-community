package org.jetbrains.completion.full.line.local.tokenizer

interface Tokenizer {
  fun encode(sentences: List<String>): List<IntArray>
  fun encode(sentence: String): IntArray
  fun decode(ids: IntArray): String
  fun decode(ids: IntArray, separator: String): String
  fun decode(id: Int): String

  val vocab: Map<String, Int>
  val vocabSize: Int
  val eosTokenId: Int
  val invalidIds: Set<Int>
  fun isValidString(s: String): Boolean
  fun idsByRegex(regex: Regex): Set<Int>
}
