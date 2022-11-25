package org.jetbrains.completion.full.line.local.generation.generation

abstract class BaseGenerationConfig(
  open val minLen: Int,
  open val maxLen: Int = 3,
  open val prefixErrLimit: Int,
  open val spellProb: Double,
)
