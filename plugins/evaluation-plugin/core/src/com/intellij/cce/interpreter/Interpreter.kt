// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.interpreter

import com.intellij.cce.actions.*
import com.intellij.cce.core.Session
import com.intellij.cce.util.FileTextUtil.computeChecksum
import com.intellij.cce.util.FileTextUtil.getDiff
import java.nio.file.Paths

class Interpreter(private val invoker: ActionsInvoker,
                  private val handler: InterpretationHandler,
                  private val filter: InterpretFilter,
                  private val projectPath: String?) {

  fun interpret(fileActions: FileActions, sessionHandler: (Session) -> Unit): List<Session> {
    val sessions = mutableListOf<Session>()
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
        }
        is CallFeature -> {
          if (shouldCompleteToken) {
            val session = invoker.callFeature(action.expectedText, action.offset, action.nodeProperties)
            sessions.add(session)
            sessionHandler(session)
          }
          isCanceled = handler.onSessionFinished(fileActions.path)
          shouldCompleteToken = filter.shouldCompleteToken()
        }
        is Rename -> invoker.rename(action.newName)
        is PrintText -> invoker.printText(action.text)
        is DeleteRange -> invoker.deleteRange(action.begin, action.end)
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
