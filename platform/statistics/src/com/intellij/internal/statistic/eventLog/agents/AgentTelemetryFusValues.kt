// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.agents

import org.jetbrains.annotations.ApiStatus

/**
 * Value sets that AI Assistant and AIR both validate agent telemetry against.
 *
 * They live here because the two plugins report the same event ids but no module depends on both, so the only
 * alternative is copying them and letting the copies drift. The event schema those values belong to is fixed by
 * `build/events/agent-telemetry-contract.json`.
 *
 * Changing any value here changes a published validation rule, so it needs a metadata review.
 */
@ApiStatus.Internal
object AgentTelemetryFusValues {
  const val UNKNOWN: String = "unknown"

  val allowedModes: List<String> = listOf(
    "accept-edits", "acceptEdits", "agent", "agent-full-access",
    "ask", "auto", "auto-approve", "autoEdit", "autopilot",
    "build", "bypass", "bypassPermissions", "chat",
    "code", "debug", "default", "dontAsk",
    "orchestrator", "plan", "read-only", "yolo",
    UNKNOWN,
  )

  val allowedReasoningLevels: List<String> = listOf(
    "minimal", "low", "medium", "high", "xhigh", "max", "ultra", UNKNOWN,
  )

  private val modelStartPattern = """
    (?:
      ai21|amazon|anthropic|arcee-ai|auto|big-pickle|claude|clip|code|codex|composer|corethink|default|devstral|fable|free
      |gemini|gemma|glm|google|gpt|grok|haiku|kilo|kimi|lite|local|luna|lyria|mimo|minimax|nano|nemotron|nvidia
      |openai|opencode|openrouter|opus|preview|qwen|sol|sonnet|step|stepfun|terra|trinity|x-ai|xiaomi|xiaomimimo
      |azure|bedrock|codellama|codestral|cohere|command|deepcoder|deepseek|ernie|exaone|fireworks|granite|groq
      |hunyuan|jamba|llama|lmstudio|magistral|mistral|mixtral|nova|olmo|ollama|perplexity|phi|phind|qwq|seed
      |smollm|solar|sonar|starcoder|together|vertex|vicuna|yi|zephyr
    )
  """.trimIndent()

  private val modelModifierPattern = """
    (?:
      base|chat|coder|distill|embed|embedding|experimental|fast|flash|instruct|it|large|latest|max|medium|mini|omni
      |optimized|plus|pro|reasoner|reasoning|realtime|search|small|super|thinking|tiny|ultra|vision|vl
    )
  """.trimIndent()

  // Matches one numeric model component: an optional single ASCII letter on either side of dot-separated digit groups (v3, 5.4, 70b).
  private const val MODEL_NUMBER_PATTERN = """[a-z]?[0-9]+(?:\.[0-9]+)*[a-z]?"""

  // Matches a mixture-of-experts size, as in mixtral-8x7b.
  private const val MODEL_EXPERTS_PATTERN = """[0-9]+x[0-9]+[a-z]?"""

  // Matches a single-letter variant suffix, as in command-a.
  private const val MODEL_VARIANT_LETTER_PATTERN = """[a-z]"""

  // Matches exactly one separator between model components: `:`, `.`, `/`, `-`, or an ASCII space.
  private const val MODEL_SEPARATOR_PATTERN = """(?:[:./-]|\x20)"""

  // BYOK and local models are listed alongside the hosted ones on purpose: a name that is not listed collapses into
  // UNKNOWN, which silently merges every self-hosted setup into one bucket.
  // This regex validates model identifiers. A model must start with a known name, provider, or alias. A compact
  // version may immediately follow it (qwen3, gpt4o); all remaining known names, modifiers, and numbers need separators.
  val modelPattern: String = """(?xi)
      (?:
        $UNKNOWN
        |
        $modelStartPattern
        (?:$MODEL_NUMBER_PATTERN)?
        (?:
          $MODEL_SEPARATOR_PATTERN
          (?:
            $modelStartPattern(?:$MODEL_NUMBER_PATTERN)?
            |$modelModifierPattern
            |$MODEL_EXPERTS_PATTERN
            |$MODEL_NUMBER_PATTERN
            |$MODEL_VARIANT_LETTER_PATTERN
          )
        )*
      )
    """.trimIndent()

  const val CONTEXT_SIZE_PATTERN: String = "[0-9]+[km]|$UNKNOWN"

  private val allowedModesSet = allowedModes.toSet()
  private val allowedReasoningLevelsSet = allowedReasoningLevels.toSet()
  private val modelRegex = modelPattern.toRegex()
  private val contextSizeRegex = CONTEXT_SIZE_PATTERN.toRegex()

  fun normalizeMode(value: String): String = value.takeIf { it in allowedModesSet } ?: UNKNOWN

  fun normalizeReasoningLevel(value: String): String = value.takeIf { it in allowedReasoningLevelsSet } ?: UNKNOWN

  fun normalizeModel(value: String): String = value.takeIf { modelRegex.matches(it) } ?: UNKNOWN

  fun normalizeContextSize(value: String): String = value.takeIf { contextSizeRegex.matches(it) } ?: UNKNOWN
}
