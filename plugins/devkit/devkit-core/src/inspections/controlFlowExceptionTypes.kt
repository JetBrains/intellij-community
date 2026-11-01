// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil
import com.intellij.util.asSafely

internal const val CANCELLATION_EXCEPTION_CLASS_NAME: String = "java.util.concurrent.CancellationException"
internal const val CONTROL_FLOW_EXCEPTION_CLASS_NAME: String = "com.intellij.openapi.diagnostic.ControlFlowException"
internal const val PROCESS_CANCELED_EXCEPTION_CLASS_NAME: String = "com.intellij.openapi.progress.ProcessCanceledException"

/**
 * Whether this type is a control-flow exception the platform never logs, mirroring `isControlFlowException`: a
 * `java.util.concurrent.CancellationException` or a `com.intellij.openapi.diagnostic.ControlFlowException`, including
 * their inheritors. `ProcessCanceledException` implements both, so it is covered without being named.
 */
internal fun PsiType.isControlFlowExceptionType(): Boolean {
  return InheritanceUtil.isInheritor(this, CONTROL_FLOW_EXCEPTION_CLASS_NAME) ||
         InheritanceUtil.isInheritor(this, CANCELLATION_EXCEPTION_CLASS_NAME)
}

/**
 * Whether this type is `com.intellij.openapi.progress.ProcessCanceledException` or one of its inheritors.
 */
internal fun PsiType.isProcessCanceledExceptionType(): Boolean {
  return InheritanceUtil.isInheritor(this, PROCESS_CANCELED_EXCEPTION_CLASS_NAME)
}

/**
 * Whether a `catch` of this type would also intercept a `ProcessCanceledException` without being one itself: this type
 * is a strict supertype of it, such as `CancellationException`, `RuntimeException` or `Throwable`.
 */
internal fun PsiType.isProcessCanceledExceptionSuperType(): Boolean {
  val caughtType = asSafely<PsiClassType>() ?: return false
  val caughtClass = caughtType.resolve() ?: return false
  // Resolve 'ProcessCanceledException' in the usage-site scope (the caught type's scope), not the resolved class's own
  // scope: for a JDK caught type such as 'Exception', the declaration scope would not see the platform class.
  val pceClass = JavaPsiFacade.getInstance(caughtClass.project)
    .findClass(PROCESS_CANCELED_EXCEPTION_CLASS_NAME, caughtType.resolveScope) ?: return false
  return pceClass.isInheritor(caughtClass, true)
}
