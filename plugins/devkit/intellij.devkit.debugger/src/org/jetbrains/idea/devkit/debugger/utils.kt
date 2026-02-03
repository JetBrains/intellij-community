// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.debugger

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.SuspendContext
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.sun.jdi.ReferenceType

/**
 * Finds any loaded class by the given FQN.
 * N.B. This method does not check the class loader.
 * Consider using [DebugProcessImpl.findLoadedClass] instead if you need to check the class loader.
 */
internal fun findClassOrNull(suspendContext: SuspendContext, fqn: String): ReferenceType? {
  val debugProcess = suspendContext.debugProcess as? DebugProcessImpl ?: return null
  return try {
    debugProcess.findLoadedClasses(suspendContext, fqn).firstOrNull()
  }
  catch (_: EvaluateException) {
    null
  }
}
