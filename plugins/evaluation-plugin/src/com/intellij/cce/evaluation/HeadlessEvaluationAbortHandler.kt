package com.intellij.cce.evaluation

import com.intellij.cce.util.ExceptionsUtil
import com.intellij.openapi.application.ApplicationManager
import kotlin.system.exitProcess

class HeadlessEvaluationAbortHandler : EvaluationAbortHandler {
  override fun onError(error: Throwable, stage: String) {
    println("$stage error. ${error.localizedMessage}")
    println("StackTrace:")
    println(ExceptionsUtil.stackTraceToString(error))
    exitProcess(1)
  }

  override fun onCancel(stage: String) {
    println("$stage was cancelled by user.")
    ApplicationManager.getApplication().exit(true, false, false)
  }
}