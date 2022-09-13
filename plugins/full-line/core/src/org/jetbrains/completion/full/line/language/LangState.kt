package org.jetbrains.completion.full.line.language

data class LangState(
  // Enable the language
  var enabled: Boolean = true,

  // Advanced settings
  var onlyFullLines: Boolean = false,
  var stringsWalking: Boolean = true,
  var showScore: Boolean = false,
  var redCodePolicy: RedCodePolicy = RedCodePolicy.FILTER,
  var groupAnswers: Boolean = false,

  // Beam search and other model configurations
  @IgnoreInInference
  var localModelState: ModelState = ModelState(),
  @IgnoreInInference
  var cloudModelState: ModelState = ModelState(),
)

enum class RedCodePolicy {
  SHOW, DECORATE, FILTER;
}

data class ModelState(
  // Beam search configuration
  var numIterations: Int = 5,
  var beamSize: Int = 6,
  var diversityGroups: Int = 1,
  var diversityStrength: Double = 0.3,

  var lenPow: Double = 0.7,
  var lenBase: Double = 2.0,

  @CheckboxFlag
  var useGroupTopN: Boolean = false,
  var groupTopN: Int = 5,
  @CheckboxFlag
  var useCustomContextLength: Boolean = false,
  var customContextLength: Int = 384,

  // Deduplication
  var minimumPrefixDist: Double = 0.2,
  var minimumEditDist: Double = 0.0,
  var keepKinds: MutableSet<KeepKind> = mutableSetOf(KeepKind.PROBABLE, KeepKind.LONG),

  var psiBased: Boolean = false,
) {
  fun contextLength() = if (useCustomContextLength) customContextLength else -1
}

@Target(AnnotationTarget.PROPERTY)
annotation class IgnoreInInference

@Target(AnnotationTarget.PROPERTY)
annotation class CheckboxFlag
