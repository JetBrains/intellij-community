package com.intellij.cce.interpreter

import com.intellij.cce.actions.FileActions
import com.intellij.cce.core.Session
import com.intellij.cce.workspace.storages.LogsSaver

class LoggingInterpreterWrapper(private val baseInterpreter: Interpreter, private val logsSaver: LogsSaver) : Interpreter {
  override fun interpret(fileActions: FileActions, sessionHandler: (Session) -> Unit): List<Session> {
    return logsSaver.invokeRememberingLogs {
      baseInterpreter.interpret(fileActions, sessionHandler)
    }
  }
}

fun Interpreter.wrapLogging(logsSaver: LogsSaver): Interpreter = LoggingInterpreterWrapper(this, logsSaver)
