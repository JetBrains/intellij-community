package com.intellij.cce.evaluable.standaloneExample

import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.intellij.cce.actions.CsvEnvironment
import com.intellij.cce.actions.DatasetRef
import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.evaluable.StandaloneFeature
import com.intellij.cce.evaluable.StrategySerializer
import com.intellij.cce.evaluation.EvaluationEnvironment
import com.intellij.cce.filter.EvaluationFilter
import com.intellij.cce.metric.Metric
import com.intellij.cce.metric.PrecisionMetric
import com.intellij.cce.metric.SessionsCountMetric
import com.intellij.cce.workspace.Config
import java.lang.reflect.Type

class StandaloneExampleFeature : StandaloneFeature<DatasetStrategy>("standalone-example") {

  override fun getStrategySerializer(): StrategySerializer<DatasetStrategy> = object : StrategySerializer<DatasetStrategy> {
    override fun serialize(src: DatasetStrategy, typeOfSrc: Type, context: JsonSerializationContext): JsonObject = JsonObject()
    override fun deserialize(map: Map<String, Any>, language: String): DatasetStrategy = DatasetStrategy()
  }

  override fun prepareEnvironment(config: Config): EvaluationEnvironment {
    val fileDataset = config.fileDataset ?: throw IllegalStateException("Required dataset config")
    return CsvEnvironment(
      datasetRef = DatasetRef.parse(fileDataset.url),
      chunkSize = fileDataset.chunkSize ?: 1,
      targetField = "Type",
      featureInvoker = StandaloneExampleInvoker(),
    )
  }

  override fun getMetrics(): List<Metric> = listOf(
    SessionsCountMetric(),
    PrecisionMetric()
  )
}

class DatasetStrategy : EvaluationStrategy {
  override val filters: Map<String, EvaluationFilter> = emptyMap()
}
