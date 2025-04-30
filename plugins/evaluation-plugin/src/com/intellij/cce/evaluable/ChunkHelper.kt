package com.intellij.cce.evaluable

import com.intellij.cce.actions.ActionsBuilder
import com.intellij.cce.actions.CallFeature
import com.intellij.cce.core.Session
import com.intellij.cce.core.SimpleTokenProperties
import com.intellij.cce.core.SymbolLocation
import com.intellij.cce.core.TokenProperties
import com.intellij.cce.core.TypeProperty
import com.intellij.cce.evaluation.EvaluationChunk
import com.intellij.cce.interpreter.InterpretFilter
import com.intellij.cce.interpreter.InterpretationHandler
import com.intellij.cce.interpreter.InterpretationOrder
import com.intellij.cce.interpreter.PresentableEvalData
import com.intellij.cce.interpreter.naiveReorder
import java.util.UUID
import kotlin.collections.plusAssign

class ChunkHelper(
  private val chunkSize: Int,
  datasetName: String,
) {
  private val datasetGeneralName = datasetName.split("_").dropLast(1).joinToString("_")
  private val chunkNamePrefix = datasetName.split("_").last()

  fun <T> chunks(
    entities: Sequence<T>,
    evaluate: ChunkHelper.(Props<T>) -> Result,
  ): Iterator<EvaluationChunk> {
    return entities.chunked(chunkSize).mapIndexed { index, values ->
      object : EvaluationChunk {
        override val datasetName: String = datasetGeneralName
        override val name: String = "${chunkNamePrefix}:${chunkSize * index}-${chunkSize * index + values.size - 1}"

        override fun evaluate(
          handler: InterpretationHandler,
          filter: InterpretFilter,
          order: InterpretationOrder,
          sessionHandler: (Session) -> Unit,
        ): EvaluationChunk.Result {
          val sessions = mutableListOf<Session>()

          var presentationText = ""
          for (value in values.naiveReorder(order)) {
            if (!filter.shouldCompleteToken()) {
              continue
            }

            val props = Props(value, presentationText.length)

            val result = evaluate(props)
            handler.onActionStarted(result.call ?: callFeature((result.presentationLineText ?: ""), presentationText.length, emptyMap()))
            sessions += result.session
            sessionHandler(result.session)
            presentationText += result.presentationLineText?.let { it + "\n" } ?: ""
            if (handler.onSessionFinished(name, values.size - sessions.size)) {
              break
            }
          }

          handler.onFileProcessed(name)

          return EvaluationChunk.Result(
            sessions,
            presentationText
          )
        }
      }
    }.iterator()
  }

  fun <T> presentableChunks(
    layoutManager: LayoutManager,
    entities: Sequence<T>,
    evaluate: ChunkHelper.(Props<T>) -> PresentableResult,
  ): Iterator<EvaluationChunk> {
    return chunks(entities) { props ->
      var call: CallFeature? = null
      var presentationLineText: String? = null

      val data = layoutManager.processData {
        val result = evaluate(props)
        call = result.call
        presentationLineText = result.presentationLineText
        result.data
      }

      val session = data.session(
        call?.expectedText ?: "",
        call?.offset ?: props.offset,
        call?.nodeProperties ?: TokenProperties.Companion.UNKNOWN,
        call?.sessionId?.id ?: UUID.randomUUID().toString()
      )

      return@chunks Result(
        session,
        presentationLineText,
        call,
      )
    }
  }

  fun callFeature(target: String, offset: Int, features: Map<String, String>): CallFeature {
    val actions = ActionsBuilder().run {
      session {
        val properties = SimpleTokenProperties.Companion.create(TypeProperty.UNKNOWN, SymbolLocation.UNKNOWN) {
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

  data class Props<T>(
    val value: T,
    val offset: Int,
  )

  data class Result(
    val session: Session,
    val presentationLineText: String? = null,
    val call: CallFeature? = null,
  )

  data class PresentableResult(
    val data: PresentableEvalData,
    val presentationLineText: String? = null,
    val call: CallFeature? = null,
  )
}