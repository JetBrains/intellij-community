package com.intellij.cce.actions

import com.intellij.cce.evaluable.ChunkHelper
import com.intellij.cce.evaluation.EvaluationChunk
import com.intellij.cce.evaluation.SimpleFileEnvironment
import com.intellij.cce.interpreter.*
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines

class CsvEnvironment(
  override val datasetRef: DatasetRef,
  private val chunkSize: Int,
  private val targetField: String,
  private val featureInvoker: FeatureInvoker,
) : SimpleFileEnvironment {

  override val preparationDescription: String = "Checking that CSV file exists"

  override fun checkFile(datasetPath: Path) {
    require(datasetPath.extension == "csv") {
      "Csv dataset should have the appropriate extension: $datasetRef"
    }

    check(datasetPath.isRegularFile()) {
      "$datasetRef didn't create a file: $datasetPath"
    }
  }

  override fun sessionCount(datasetContext: DatasetContext): Int = datasetContext.path(datasetRef).readLines().size - 1

  override fun chunks(datasetContext: DatasetContext): Iterator<EvaluationChunk> {
    val lines = datasetContext.path(datasetRef).readLines()
    val names = lines.first().split(',').map { it.trim() }
    val rows = lines.subList(1, lines.size).asSequence()
      .map { row ->
        val values = names.zip(row.split(',').map { it.trim() }).toMap()
        val features = values.filterNot { it.key == targetField }
        val target = values[targetField]!!

        target to features
      }

    return ChunkHelper(chunkSize, datasetRef.name).chunks(rows) { props ->
      val (target, features) = props.value
      val call = callFeature(target, props.offset, features)
      ChunkHelper.Result(
        featureInvoker.callFeature(call.expectedText, call.offset, call.nodeProperties, call.sessionId.id),
        "$target <- ${features.toList().joinToString(", ") { "${it.first} = ${it.second}" }}",
        call
      )
    }
  }
}

