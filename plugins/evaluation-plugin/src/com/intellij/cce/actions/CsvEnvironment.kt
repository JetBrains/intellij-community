package com.intellij.cce.actions

import com.intellij.cce.core.Session
import com.intellij.cce.core.SimpleTokenProperties
import com.intellij.cce.core.SymbolLocation
import com.intellij.cce.core.TypeProperty
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
) : SimpleFileEnvironment() {

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
    val dataLines = lines.subList(1, lines.size)
    val names = lines.first().split(',').map { it.trim() }

    var offset = 0
    val result = mutableListOf<EvaluationChunk>()
    for (rows in dataLines.chunked(chunkSize)) {
      val presentationText = StringBuilder()
      val calls = mutableListOf<CallFeature>()
      for (row in rows) {
        if (presentationText.isNotBlank()) {
          presentationText.append("\n")
        }

        val values = names.zip(row.split(',').map { it.trim() }).toMap()
        val features = values.filterNot { it.key == targetField }
        val target = values[targetField]!!

        calls += callFeature(target, presentationText.length, features)

        presentationText.append("$target <- ${features.toList().joinToString(", ") { "${it.first} = ${it.second}" }}")

        offset += 1
      }

      result += object : EvaluationChunk {
        override val datasetName: String = this@CsvEnvironment.datasetRef.name
        override val name: String = "$datasetName:${offset - rows.size + 1}-${offset}"
        override val presentationText: String = presentationText.toString()

        override fun evaluate(
          handler: InterpretationHandler,
          filter: InterpretFilter,
          order: InterpretationOrder,
          sessionHandler: (Session) -> Unit
        ): List<Session> {
          val sessions = mutableListOf<Session>()

          for (call in calls.reorder(order)) {
            if (!filter.shouldCompleteToken()) {
              continue
            }

            handler.onActionStarted(call)
            val session = featureInvoker.callFeature(call.expectedText, call.offset, call.nodeProperties)
            sessions += session
            sessionHandler(session)
            if (handler.onSessionFinished(name, calls.size - sessions.size)) {
              break
            }
          }

          handler.onFileProcessed(name)

          return sessions
        }
      }
    }

    return result.iterator()
  }

  private fun callFeature(target: String, offset: Int, features: Map<String, String>): CallFeature {
    val actions = ActionsBuilder().run {
      session {
        val properties = SimpleTokenProperties.create(TypeProperty.UNKNOWN, SymbolLocation.UNKNOWN) {
          features.forEach { put(it.key, it.value) }
        }
        callFeature(target, offset, properties)
      }

      build()
    }

    val action = actions.first()

    check(action is CallFeature)

    return action
  }
}