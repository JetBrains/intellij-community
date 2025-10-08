package com.intellij.cce.metric

import com.intellij.cce.evaluation.data.DataPlacement
import com.intellij.cce.evaluation.data.DataRenderer
import com.intellij.cce.evaluation.data.EvalDataDescription
import com.intellij.cce.evaluation.data.EvalDataPresentation
import com.intellij.cce.evaluation.data.PresentationCategory
import com.intellij.cce.evaluation.data.TrivialEvalData

object ClusterMetricConstants {
  const val AIA_HOMOGENEITY_SCORE_KEY: String = "homogeneity_score"
  const val AIA_COMPLETENESS_SCORE_KEY: String = "completeness_score"
  const val AIA_V_MEASURE_SCORE_KEY: String = "v_measure_score"
  const val AIA_ADULTERANT_SCORE_KEY: String = "adulterant_score"

  val HOMOGENEITY_SCORE: TrivialEvalData<Double> = EvalDataDescription(
    name = "Homogeneity score",
    description = "Homogeneity score for a clusterization",
    placement = DataPlacement.AdditionalDouble(AIA_HOMOGENEITY_SCORE_KEY),
    presentation = EvalDataPresentation(
      PresentationCategory.METRIC,
      DataRenderer.InlineDouble
    )
  )

  val COMPLETENESS_SCORE: TrivialEvalData<Double> = EvalDataDescription(
    name = "Completeness score",
    description = "Completeness score for a clusterization",
    placement = DataPlacement.AdditionalDouble(AIA_COMPLETENESS_SCORE_KEY),
    presentation = EvalDataPresentation(
      PresentationCategory.METRIC,
      DataRenderer.InlineDouble
    )
  )

  val V_MEASURE_SCORE: TrivialEvalData<Double> = EvalDataDescription(
    name = "V-Measure score",
    description = "V-Measure score for a clusterization",
    placement = DataPlacement.AdditionalDouble(AIA_V_MEASURE_SCORE_KEY),
    presentation = EvalDataPresentation(
      PresentationCategory.METRIC,
      DataRenderer.InlineDouble
    )
  )

  val ADULTERANT_SCORE: TrivialEvalData<Double> = EvalDataDescription(
    name = "Adulterant score",
    description = "Adulterant score for a clusterization",
    placement = DataPlacement.AdditionalDouble(AIA_ADULTERANT_SCORE_KEY),
    presentation = EvalDataPresentation(
      PresentationCategory.METRIC,
      DataRenderer.InlineDouble
    )
  )

}