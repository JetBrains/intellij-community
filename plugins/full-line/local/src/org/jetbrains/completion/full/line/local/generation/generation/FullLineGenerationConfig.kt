package org.jetbrains.completion.full.line.local.generation.generation

class FullLineGenerationConfig(
  minLen: Int = 1,
  prefixErrLimit: Int = 0,
  spellProb: Double = 0.0001,
  maxLen: Int = 3,
  maxContextLen: Int? = null,
  minContextLen: Int? = 100,
  val numBeams: Int = 5,
  val lenNormBase: Double = 5.0,
  val lenNormPow: Double = 0.7,
  val filename: String = "",
  val filenameSplitSymbol: Char = '₣',
  val metaInfoSplitSymbol: String = "𐌼",
  val oneTokenMode: Boolean = false,
  val stashRegex: Regex = Regex("\\W+"),
  val terminateRegex: Regex = Regex("\n"),
  val addLang: Boolean = false,
  val language: String = "",
  val bosString: String = "<BOS>"
) : BaseGenerationConfig(minLen, maxLen, prefixErrLimit, spellProb, maxContextLen, minContextLen)
