package com.intellij.cce.evaluable

import com.intellij.cce.actions.ActionsBuilder
import com.intellij.cce.actions.CallFeature
import com.intellij.cce.actions.DatasetRef
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
  private val datasetName: String,
  private val chunkNamePrefix: String,
  private val iterationsInSameChunk: Boolean = false,
) {
  constructor(datasetRef: DatasetRef, iterationsInSameChunk: Boolean = false) :
    this(datasetRef.datasetName, datasetRef.chunkNamePrefix, iterationsInSameChunk)

  fun <T> chunks(
    chunkSize: Int,
    entities: Sequence<T>,
    evaluate: ChunkHelper.(Props<T>) -> Result,
  ): Sequence<EvaluationChunk> {
    return entities.chunked(chunkSize).mapIndexed { index, values ->
      object : EvaluationChunk {
        override val datasetName: String = this@ChunkHelper.datasetName
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

        override fun iterate(iterationCount: Int): List<EvaluationChunk> =
          if (iterationsInSameChunk)
            chunks(chunkSize * iterationCount, values.asSequence().flatMap { e -> List(iterationCount) { e } }, evaluate).toList()
          else super.iterate(iterationCount)
      }
    }
  }

  fun <T> presentableChunks(
    layoutManager: LayoutManager,
    chunkSize: Int,
    entities: Sequence<T>,
    evaluate: ChunkHelper.(Props<T>) -> PresentableResult,
  ): Sequence<EvaluationChunk> {
    return chunks(chunkSize, entities) { props ->
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

  fun presentableChunk(
    layoutManager: LayoutManager,
    evaluate: ChunkHelper.(Props<Unit>) -> PresentableResult
  ): Sequence<EvaluationChunk> = presentableChunks(layoutManager, 1, sequenceOf(Unit)) { evaluate(it) }

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