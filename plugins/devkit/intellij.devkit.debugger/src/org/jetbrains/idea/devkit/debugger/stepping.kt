// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.debugger

import com.intellij.debugger.engine.*
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerManagerListener
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.sun.jdi.BooleanValue
import com.sun.jdi.ClassType
import com.sun.jdi.ObjectReference
import java.util.concurrent.ConcurrentHashMap

private object PauseListener : DebuggerManagerListener {
  private val sessions = ConcurrentHashMap<DebuggerSession, SessionThreadsData>()

  override fun sessionAttached(session: DebuggerSession?) {
    if (session == null) return
    val disposable = Disposer.newDisposable()
    sessions[session] = SessionThreadsData(disposable)
    session.process.addDebugProcessListener(object : DebugProcessListener {
      override fun paused(suspendContext: SuspendContext) {
        val context = suspendContext as? SuspendContextImpl ?: return
        val pausedThreads = context.debugProcess.suspendManager.pausedContexts.mapNotNull { it.thread }
        getSessionData(context.debugProcess.session)?.resetNonCancellableSection(context, pausedThreads)
      }
    }, disposable)
  }

  override fun sessionDetached(session: DebuggerSession?) {
    if (session == null) return
    val sessionData = sessions.remove(session) ?: return
    Disposer.dispose(sessionData.disposable)
  }

  fun getSessionData(session: DebuggerSession): SessionThreadsData? = sessions[session]
}

private class SteppingStartListener : SteppingListener {
  override fun beforeSteppingStarted(suspendContext: SuspendContextImpl, steppingAction: SteppingAction) {
    PauseListener.getSessionData(suspendContext.debugProcess.session)?.setNonCancellableSection(suspendContext)
  }
}

private data class ThreadState(val reference: ObjectReference, var state: Boolean = false) {
  /**
   * @see com.intellij.openapi.progress.Cancellation.DebugNonCancellableState.inNonCancelableSection
   */
  fun setNonCancellable(suspendContext: SuspendContextImpl, value: Boolean) {
    if (value == state) return
    state = value
    val field = (reference.type() as ClassType).fieldByName("inNonCancelableSection") ?: return
    reference.setValue(field, booleanValue(suspendContext, value))
  }
}

/**
 * Manages cancellability state of the IDE threads within a single debugger session.
 */
private class SessionThreadsData(val disposable: Disposable) {
  private val threadStates = hashMapOf<ThreadReferenceProxyImpl, ThreadState?>()

  /**
   * Sets the non-cancellable state for the current thread.
   * This method requires a suspend context command, as it may cause evaluation.
   */
  fun setNonCancellableSection(suspendContext: SuspendContextImpl) {
    if (!isSteppingAdjustmentEnabled()) return
    try {
      val state = getOrCreateThreadState(suspendContext) ?: return
      state.setNonCancellable(suspendContext, true)
    }
    catch (e: Exception) {
      thisLogger().error(e)
    }
  }

  /**
   * Resets the non-cancellable flag for the paused threads.
   */
  fun resetNonCancellableSection(suspendContext: SuspendContextImpl, pausedThreads: List<ThreadReferenceProxyImpl>) {
    if (!isSteppingAdjustmentEnabled()) return
    try {
      for (state in pausedThreads.mapNotNull { threadStates[it] }) {
        state.setNonCancellable(suspendContext, false)
      }
    }
    catch (e: Exception) {
      thisLogger().error(e)
    }
  }

  /**
   * Get a reference to the [com.intellij.openapi.progress.Cancellation.DebugNonCancellableState] instance
   * bounded to the current thread.
   * Uses cached value if already created to reduce the number of evaluations.
   */
  private fun getOrCreateThreadState(suspendContext: SuspendContextImpl): ThreadState? {
    val thread = suspendContext.thread ?: return null
    if (threadStates.containsKey(thread)) return threadStates[thread]
    val reference = initializeThreadState(suspendContext)
    val state = reference?.let { ThreadState(it) }
    return state.also { threadStates[thread] = it }
  }
}

private fun isSteppingAdjustmentEnabled() = Registry.`is`("devkit.debugger.prevent.pce.while.stepping")



/**
 * @see com.intellij.openapi.progress.Cancellation.initThreadNonCancellableState
 * @see com.intellij.openapi.progress.Cancellation.isInNonCancelableSection
 */
private fun initializeThreadState(suspendContext: SuspendContextImpl): ObjectReference? {
  val evaluationContext = EvaluationContextImpl(suspendContext, suspendContext.frameProxy)
  val cancellationClass = findClassOrNull(evaluationContext, "com.intellij.openapi.progress.Cancellation") as? ClassType ?: return null
  return DebuggerUtilsImpl.invokeClassMethod(evaluationContext, cancellationClass, "initThreadNonCancellableState",
                                             "()Lcom/intellij/openapi/progress/Cancellation\$DebugNonCancellableState;") as? ObjectReference
}

private fun booleanValue(suspendContext: SuspendContextImpl, b: Boolean): BooleanValue = suspendContext.virtualMachineProxy.mirrorOf(b)
