package org.jetbrains.completion.full.line.local.generation.generation

class FullLineGenerationConfig(
  minLen: Int = 1,
  prefixErrLimit: Int = 0,
  spellProb: Double = 0.0001,
  maxLen: Int = 3,
  val numBeams: Int = 5,
  val lenNormBase: Double = 5.0,
  val lenNormPow: Double = 0.7,
  val filename: String = "",
  val languageSplitSymbol: Char = '₣',
  val metaInfoSplitSymbol: String = "𐌼",
  val oneTokenMode: Boolean = false,
  val stashRegex: Regex = Regex("\\W+"),
  val terminateRegex: Regex = Regex("[\n⇥⇤]"),
  // Cache stuff
  val contextOffsetForCache: Double = 0.5  // Which part of model's context length will be used for cache hitting for next sessions
) : BaseGenerationConfig(minLen, maxLen, prefixErrLimit, spellProb)
