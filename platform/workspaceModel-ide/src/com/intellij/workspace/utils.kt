package com.intellij.workspace

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import kotlin.concurrent.getOrSet

private val bracketIndent = ThreadLocal<Int>()

internal fun <R> Logger.bracket(message: String, block: () -> R): R {
  val indentLevel = bracketIndent.getOrSet { 0 }
  val indent = "  ".repeat(indentLevel)

  info("START: $indent$message")
  bracketIndent.set(indentLevel + 1)
  try {
    return block()
  }
  finally {
    bracketIndent.set(indentLevel)
    info("END  : $indent$message")
  }
}

// TODO Drop? Use standard function? Review usages.
internal fun executeOrQueueOnDispatchThread(block: () -> Unit) {
  val application = ApplicationManager.getApplication()
  if (application.isDispatchThread) {
    block()
  }
  else {
    application.invokeLater(block)
  }
}
