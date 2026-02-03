package com.intellij.completion.ml.experiments

interface MLRankingExperiment {
  val id: Int

  val useMLRanking: Boolean?
  val showArrows: Boolean?
  val calculateFeatures: Boolean?
  val logElementFeatures: Boolean?

  val targetLanguageId: String?

  companion object {
    const val SEED: Long = 111L
    const val NO_EXP: Int = -1
  }
}

data class RawMLRankingExperimentData(
  val isEAP: Boolean,
  val userFraction: Double,
  val releaseVersion: String,
  val groups: List<MLRankingExperiment>,
  /** Must not be filled manually! */
  val languageId: String = "",
)

class ExperimentDataHolder() {
  private val _experiments: MutableList<RawMLRankingExperimentData> = mutableListOf()
  val experiments: List<RawMLRankingExperimentData>
    get() = _experiments.toList()

  fun updateLanguageIds(newLanguageId: String) {
    _experiments.replaceAll { it.copy(languageId = newLanguageId) }
  }

  fun addExperiment(data: RawMLRankingExperimentData) {
    _experiments += data
  }
}

class ExperimentDataForRelease(var releaseVersion: String) {
  var userFraction: Double = 0.0
  lateinit var groups: List<MLRankingExperiment>

  fun toExperimentData(): RawMLRankingExperimentData = RawMLRankingExperimentData(
    isEAP = false,
    userFraction = userFraction,
    groups = groups,
    releaseVersion = releaseVersion,
  )
}

class ExperimentDataForEAP {
  lateinit var groups: List<MLRankingExperiment>

  fun toExperimentData(): RawMLRankingExperimentData = RawMLRankingExperimentData(
    isEAP = true,
    userFraction = 1.0,
    groups = groups,
    releaseVersion = "EAP",
  )
}

fun MLRankingExperimentConnector.experiments(languageId: String, block: ExperimentDataHolder.() -> Unit): List<RawMLRankingExperimentData> {
  return ExperimentDataHolder().apply(block).also { it.updateLanguageIds(languageId) }.experiments
}

fun ExperimentDataHolder.release(version: String, block: ExperimentDataForRelease.() -> Unit): ExperimentDataForRelease {
  return ExperimentDataForRelease(releaseVersion = version).apply(block).also {
    addExperiment(it.toExperimentData())
  }
}

fun ExperimentDataHolder.eap(block: ExperimentDataForEAP.() -> Unit): ExperimentDataForEAP {
  return ExperimentDataForEAP().apply(block).also {
    addExperiment(it.toExperimentData())
  }
}

enum class CommonExperiments(
  override val id: Int,
  override val useMLRanking: Boolean? = null,
  override val showArrows: Boolean? = null,
  override val calculateFeatures: Boolean? = null,
  override val logElementFeatures: Boolean? = null,
) : MLRankingExperiment {
  NoExperiment(id = -1),

  StandardCompletion(id = 1, useMLRanking = false, showArrows = false, calculateFeatures = false, logElementFeatures = true),
  DefaultMLModelWithoutArrows(id = 8, useMLRanking = true, showArrows = false, calculateFeatures = true, logElementFeatures = true),
  DefaultMLModelWithArrows(id = 11, useMLRanking = true, showArrows = true, calculateFeatures = true, logElementFeatures = true),
  StandardCompletionNoFeatures(id = 12, useMLRanking = false, showArrows = false, calculateFeatures = false, logElementFeatures = true),
  FirstExperimentalModel(id = 13, useMLRanking = true, showArrows = false, calculateFeatures = true, logElementFeatures = true),
  SecondExperimentalModel(id = 14, useMLRanking = true, showArrows = false, calculateFeatures = true, logElementFeatures = true),
  CompletionPerformanceEarlyML(id = 17, useMLRanking = true, showArrows = false, calculateFeatures = true, logElementFeatures = true),
  CompletionPerformanceEarlyLookup(id = 18, useMLRanking = false, showArrows = false, calculateFeatures = true, logElementFeatures = true),
  CompletionPerformance(id = 19, useMLRanking = false, showArrows = false, calculateFeatures = true, logElementFeatures = true),

  ControlA(id = 50, useMLRanking = true),
  ControlB(id = 51, useMLRanking = false),
  ;

  override val targetLanguageId: String? = null
}
