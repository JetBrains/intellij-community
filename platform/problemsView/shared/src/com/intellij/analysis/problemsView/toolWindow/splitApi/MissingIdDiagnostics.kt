// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow.splitApi

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.Problem
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.ApiStatus

/**
 * Temporary test-only logging, to help understand causes behind IJPL-245334
 **/
@ApiStatus.Internal
object MissingIdDiagnostics {
  private val LOG = Logger.getInstance("com.intellij.platform.problemsView.MissingIdDiagnostics")

  const val STEP_SUBSCRIPTION: Int = 0
  const val STEP_APPEARED: Int = 1
  const val STEP_UPDATED: Int = 2
  const val STEP_DISAPPEARED: Int = 3

  fun trace(site: String, event: String, step: Int, problem: Problem, extra: String = "") {
    if (!LOG.isDebugEnabled) return

    val text = problem.text.let { if (it.length > 80) it.take(80) + "..." else it }
    val file = if (problem is FileProblem) " file=${problem.file.name} line=${problem.line}" else ""

    LOG.debug(
      "step=$step site=$site event=$event hash=${problem.hashCode()} " +
      "type=${problem.javaClass.simpleName} text='$text'$file thread=${Thread.currentThread().name}" +
      (if (extra.isNotEmpty()) " $extra" else "")
    )
  }

  fun trace(site: String, event: String, step: Int, extra: String = "") {
    if (!LOG.isDebugEnabled) return

    LOG.debug(
      "step=$step site=$site event=$event thread=${Thread.currentThread().name}" +
      (if (extra.isNotEmpty()) " $extra" else "")
    )
  }
}
