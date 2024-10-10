package com.intellij.cce.evaluable.conflictResolution

import com.intellij.cce.actions.*
import com.intellij.cce.core.*
import com.intellij.cce.evaluable.AIA_CONTEXT
import com.intellij.cce.evaluation.EvaluationChunk
import com.intellij.cce.evaluation.SimpleFileEnvironment
import com.intellij.cce.interpreter.InterpretFilter
import com.intellij.cce.interpreter.InterpretationHandler
import com.intellij.cce.interpreter.InterpretationOrder
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

class ConflictEnvironment(
  override val datasetRef: DatasetRef,
  private val conflictResolver: ConflictResolver<*>
) : SimpleFileEnvironment() {

  override val preparationDescription: String = "Checking that conflict dataset exists"

  override fun sessionCount(datasetContext: DatasetContext): Int =
    datasetContext.path(datasetRef).listDirectoryEntries().size // FIXME should be pre-calculated

  override fun chunks(datasetContext: DatasetContext): Iterator<EvaluationChunk> {
    return datasetContext.path(datasetRef).listDirectoryEntries().map { conflictPath ->
      ConflictChunk(conflictPath)
    }.iterator()
  }

  private inner class ConflictChunk(
    private val conflictPath: Path,
  ) : EvaluationChunk {
    override val datasetName: String = datasetRef.name
    override val name: String = conflictPath.fileName.toString()
    override val presentationText: String = readText("result")

    override fun evaluate(
      handler: InterpretationHandler,
      filter: InterpretFilter, // TODO should we use it somehow?
      order: InterpretationOrder, // TODO should we use somehow?
      sessionHandler: (Session) -> Unit
    ): List<Session> {
      val props = ConflictResolver.Props(
        base = readText("base"),
        parent1 = readText("parent1"),
        parent2 = readText("parent2"),
        target = readText("result"),
      )

      val resolvedConflicts = conflictResolver.resolveConflicts(props)

      require(resolvedConflicts.keys.containsAll(TextLabel.entries))

      val sessions = (0 until resolvedConflicts.size).map { conflictIndex ->
        conflictSession(resolvedConflicts, conflictIndex)
      }

      for ((index, session) in sessions.withIndex()) {
        sessionHandler(session)
        handler.onSessionFinished(name, sessions.size - index - 1)
      }

      handler.onFileProcessed(name)

      return sessions
    }

    private fun <T> conflictSession(
      conflicts: MatchSequence<T>,
      conflictIndex: Int,
    ): Session {
      val expected = conflicts.text(TextLabel.TARGET, conflictIndex)
      val session = Session(
        conflicts.charOffset(TextLabel.TARGET, conflictIndex),
        expected,
        expected.length,
        TokenProperties.UNKNOWN
      )

      val result = conflicts.text(TextLabel.RESULT, conflictIndex)

      val suggestions = listOf(
        Suggestion(
          result,
          result,
          SuggestionSource.INTELLIJ,
          emptyMap(),
          isRelevant = expected == result
        )
      )

      val lookup = Lookup(
        "",
        0,
        suggestions,
        0,
        null,
        selectedPosition = suggestions.indexOfFirst { it.isRelevant },
        isNew = false,
        mapOf(
          AIA_CONTEXT to contextPresentation(conflicts, conflictIndex)
        )
      )

      session.addLookup(lookup)

      return session
    }

    private fun readText(namePrefix: String): String =
      conflictPath.listDirectoryEntries().first { it.fileName.toString().startsWith(namePrefix) }.readText()

    private fun contextPresentation(conflicts: MatchSequence<*>, conflictIndex: Int) = """
<<<<<<< BASE
${conflicts.text(TextLabel.BASE, conflictIndex).trimEnd()}
======= PARENT1
${conflicts.text(TextLabel.PARENT_1, conflictIndex).trimEnd()}
======= PARENT2
${conflicts.text(TextLabel.PARENT_2, conflictIndex).trimEnd()}
>>>>>>>
""".trim()
  }
}

