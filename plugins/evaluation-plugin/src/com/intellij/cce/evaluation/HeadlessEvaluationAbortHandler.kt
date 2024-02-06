// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation

import com.intellij.cce.util.ExceptionsUtil

class HeadlessEvaluationAbortHandler : EvaluationAbortHandler {
  override fun onError(error: Throwable, stage: String) {
    println("$stage error. ${error.localizedMessage}")
    println("StackTrace:")
    println(ExceptionsUtil.stackTraceToString(error))
  }

  override fun onCancel(stage: String) {
    println("$stage was cancelled by user.")
  }
}