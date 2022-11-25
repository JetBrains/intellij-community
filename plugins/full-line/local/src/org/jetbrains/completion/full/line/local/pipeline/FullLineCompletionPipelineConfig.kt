package org.jetbrains.completion.full.line.local.pipeline

import org.jetbrains.completion.full.line.local.CompletionConfig
import org.jetbrains.completion.full.line.local.generation.generation.FullLineGenerationConfig

class FullLineCompletionPipelineConfig(
  generationConfig: FullLineGenerationConfig,
  filterConfig: CompletionConfig.Filter,
  numSuggestions: Int?
) : BaseCompletionPipelineConfig<FullLineGenerationConfig, CompletionConfig.Filter>(
  generationConfig,
  filterConfig,
  numSuggestions
) {
  constructor(
    minLen: Int = 1,
    prefixErrLimit: Int = 0,
    spellProb: Double = 0.0001,
    maxLen: Int = 3,
    numBeams: Int = 5,
    lenNormBase: Double = 5.0,
    lenNormPow: Double = 0.7,
    oneTokenMode: Boolean = false,
    filename: String = "",
    languageSplitSymbol: Char = '₣',
    metaInfoSplitSymbol: String = "𐌼",
    minSymbolLen: Int = 2,
    minAvgLogProb: Double = -100.0,
    minProb: Double = 0.0,
    numSuggestions: Int? = 5
  ) : this(
    FullLineGenerationConfig(
      minLen = minLen,
      prefixErrLimit = prefixErrLimit,
      spellProb = spellProb,
      maxLen = maxLen,
      numBeams = numBeams,
      lenNormBase = lenNormBase,
      lenNormPow = lenNormPow,
      oneTokenMode = oneTokenMode,
      filename = filename,
      languageSplitSymbol = languageSplitSymbol,
      metaInfoSplitSymbol = metaInfoSplitSymbol
    ),
    CompletionConfig.Filter(
      minSymbolLen = minSymbolLen,
      minAvgLogProb = minAvgLogProb,
      minProb = minProb
    ),
    numSuggestions
  )
}
