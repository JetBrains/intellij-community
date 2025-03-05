// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.debugger

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.openapi.diagnostic.fileLogger
import com.sun.jdi.IncompatibleThreadStateException
import com.sun.jdi.ReferenceType

internal fun findClassOrNull(evaluationContext: EvaluationContext, fqn: String): ReferenceType? {
  val debugProcess = evaluationContext.debugProcess as? DebugProcessImpl ?: return null
  return try {
    debugProcess.findLoadedClass(evaluationContext, fqn, evaluationContext.classLoader)
  }
  catch (_: EvaluateException) {
    null
  }
}

internal fun logIncorrectSuspendState(e: Exception): Boolean {
  if (e !is EvaluateException) return false
  val cause = e.cause ?: return false
  if (cause !is IncompatibleThreadStateException) return false
  fileLogger().info(e)
  return true
}
