package com.intellij.cce.interpreter

import com.intellij.cce.actions.*
import com.intellij.cce.core.Session
import com.intellij.cce.util.FileTextUtil.computeChecksum
import com.intellij.cce.util.FileTextUtil.getDiff
import java.nio.file.Paths
import java.util.*

class Interpreter(private val invoker: CompletionInvoker,
                  private val handler: InterpretationHandler,
                  private val filter: InterpretFilter,
                  private val saveContent: Boolean,
                  private val projectPath: String?) {
  companion object {
    const val CCE_SESSION_UID = "sessionUid"
    private const val CCE_SESSION_UID_FEATURE_NAME = "ml_ctx_cce_$CCE_SESSION_UID"
  }

  fun interpret(fileActions: FileActions, sessionHandler: (Session) -> Unit): List<Session> {
    val sessions = mutableListOf<Session>()
    var isFinished = false
    var session: Session? = null
    var position = 0

    val filePath = if (projectPath == null) fileActions.path else Paths.get(projectPath).resolve(fileActions.path).toString()
    val needToClose = !invoker.isOpen(filePath)
    val text = invoker.openFile(filePath)
    if (fileActions.checksum != computeChecksum(text)) {
      handler.onErrorOccurred(IllegalStateException("File $filePath has been modified."), fileActions.sessionsCount)
      return emptyList()
    }
    var shouldCompleteToken = filter.shouldCompleteToken()
    var isCanceled = false

    for (action in fileActions.actions) {
      handler.onActionStarted(action)
      when (action) {
        is MoveCaret -> {
          invoker.moveCaret(action.offset)
          position = action.offset
        }
        is CallCompletion -> {
          isFinished = false
          if (shouldCompleteToken) {
            val lookup = invoker.callCompletion(action.expectedText, action.prefix)
            if (session == null) {
              val sessionUuid = lookup.features?.common?.context?.get(CCE_SESSION_UID_FEATURE_NAME)
                                ?: UUID.randomUUID().toString()
              val content = if (saveContent) invoker.getText() else null
              session = Session(position, action.expectedText, action.expectedText.length, content, action.nodeProperties, sessionUuid)
            }
            session.addLookup(lookup)
          }
        }
        is FinishSession -> {
          if (shouldCompleteToken) {
            if (session == null) throw UnexpectedActionException("Session canceled before created")
            val expectedText = session.expectedText
            isFinished = invoker.finishCompletion(expectedText, session.lookups.last().prefix)
            session.success = session.lookups.last().suggestions.any { it.text == expectedText }
            sessions.add(session)
            sessionHandler(session)
          }
          isCanceled = handler.onSessionFinished(fileActions.path)
          session = null
          shouldCompleteToken = filter.shouldCompleteToken()
        }
        is EmulateUserSession -> {
          session = invoker.emulateUserSession(action.expectedText, action.nodeProperties, position)
          if (session.lookups.isNotEmpty()) sessions.add(session)
          sessionHandler(session)
          isCanceled = handler.onSessionFinished(fileActions.path)
        }
        is CompletionGolfSession -> {
          session = invoker.emulateCompletionGolfSession(action.expectedText, action.ranges, position)
          sessions.add(session)
          isCanceled = handler.onSessionFinished(filePath)
        }
        is PrintText -> {
          if (!action.completable || !isFinished)
            invoker.printText(action.text)
        }
        is DeleteRange -> {
          if (!action.completable || !isFinished)
            invoker.deleteRange(action.begin, action.end)
        }
      }
      if (isCanceled) break
    }

    invoker.save()
    val resultText = invoker.getText()
    if (text != resultText) {
      invoker.deleteRange(0, resultText.length)
      invoker.printText(text)
      if (needToClose) invoker.closeFile(filePath)
      throw IllegalStateException("Text before and after interpretation doesn't match. Diff:\n${getDiff(text, resultText)}")
    }
    if (needToClose) invoker.closeFile(filePath)
    handler.onFileProcessed(fileActions.path)
    return sessions
  }
}
