// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.interpreter

import com.intellij.cce.actions.*
import com.intellij.cce.core.Session
import com.intellij.cce.util.FileTextUtil.computeChecksum
import com.intellij.cce.util.FileTextUtil.getDiff
import java.nio.file.Paths

class Interpreter(private val invokersFactory: InvokersFactory,
                  private val handler: InterpretationHandler,
                  private val filter: InterpretFilter,
                  private val projectPath: String?) {

  fun interpret(fileActions: FileActions, sessionHandler: (Session) -> Unit): List<Session> {
    val actionsInvoker = invokersFactory.createActionsInvoker()
    val featureInvoker = invokersFactory.createFeatureInvoker()
    val sessions = mutableListOf<Session>()
    val filePath = if (projectPath == null) fileActions.path else Paths.get(projectPath).resolve(fileActions.path).toString()
    val needToClose = !actionsInvoker.isOpen(filePath)
    val text = actionsInvoker.openFile(filePath)
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
          actionsInvoker.moveCaret(action.offset)
        }
        is CallFeature -> {
          if (shouldCompleteToken) {
            val session = featureInvoker.callFeature(action.expectedText, action.offset, action.nodeProperties)
            sessions.add(session)
            sessionHandler(session)
          }
          isCanceled = handler.onSessionFinished(fileActions.path)
          shouldCompleteToken = filter.shouldCompleteToken()
        }
        is Rename -> actionsInvoker.rename(action.newName)
        is PrintText -> actionsInvoker.printText(action.text)
        is DeleteRange -> actionsInvoker.deleteRange(action.begin, action.end)
      }
      if (isCanceled) break
    }

    actionsInvoker.save()
    val resultText = actionsInvoker.getText()
    if (text != resultText) {
      actionsInvoker.deleteRange(0, resultText.length)
      actionsInvoker.printText(text)
      if (needToClose) actionsInvoker.closeFile(filePath)
      throw IllegalStateException("Text before and after interpretation doesn't match. Diff:\n${getDiff(text, resultText)}")
    }
    if (needToClose) actionsInvoker.closeFile(filePath)
    handler.onFileProcessed(fileActions.path)
    return sessions
  }
}
