// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Disposer
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.breakpoints.XBreakpointListener
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.breakpoints.BreakpointsUsageCollector.reportBreakpointVerified
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.locks.ReentrantLock
import javax.swing.Icon
import kotlin.concurrent.withLock

/**
 * Owns breakpoint registration, dependencies, and presentations for one debug session.
 */
internal class XDebugSessionBreakpointManager(
  private val session: XDebugSessionImpl,
  private val breakpointManager: XBreakpointManagerImpl,
) {
  private val lock = ReentrantLock()

  private val registeredBreakpoints = hashMapOf<XBreakpoint<*>, CustomizedBreakpointPresentation>()
  private val inactiveSlaveBreakpoints = hashSetOf<XBreakpoint<*>>()
  private var breakpointsDisabled = false
  private var breakpointListenerDisposable: Disposable? = null
  private var breakpointsInitialized = false

  fun reset() {
    lock.withLock {
      breakpointsInitialized = false
      clearRegisteredBreakpoints()
    }
    removeBreakpointListeners()
  }

  fun initBreakpoints() {
    lock.withLock {
      LOG.assertTrue(!breakpointsInitialized)
      breakpointsInitialized = true
      var disposable = breakpointListenerDisposable
      if (disposable == null) {
        disposable = Disposer.newDisposable(session.project)
        breakpointListenerDisposable = disposable
        val busConnection = session.project.getMessageBus().connect(disposable)
        busConnection.subscribe(XBreakpointListener.TOPIC, MyBreakpointListener())
        busConnection.subscribe(XDependentBreakpointListener.TOPIC, MyDependentBreakpointListener())
      }
    }

    disableSlaveBreakpoints()
    processAllBreakpoints(register = true, temporary = false)
  }

  private fun disableSlaveBreakpoints() {
    val slaveBreakpoints = breakpointManager.dependentBreakpointManager.allSlaveBreakpoints
    if (slaveBreakpoints.isEmpty()) {
      return
    }

    val breakpointTypes = hashSetOf<XBreakpointType<*, *>?>()
    for (handler in session.debugProcess.breakpointHandlers) {
      breakpointTypes.add(XDebuggerUtil.getInstance().findBreakpointType(handler.breakpointTypeClass))
    }
    val supportedBreakpoints = slaveBreakpoints.filter { it.type in breakpointTypes }
    lock.withLock {
      inactiveSlaveBreakpoints.addAll(supportedBreakpoints)
    }
  }

  private fun <B : XBreakpoint<*>> processBreakpoints(
    handler: XBreakpointHandler<*>,
    register: Boolean,
    temporary: Boolean,
  ) {
    @Suppress("UNCHECKED_CAST")
    handler as XBreakpointHandler<B>
    val breakpoints = breakpointManager.getBreakpoints<B>(handler.breakpointTypeClass)
    for (b in breakpoints) {
      handleBreakpoint(handler, b, register, temporary)
    }
  }

  private fun <B : XBreakpoint<*>> handleBreakpoint(
    handler: XBreakpointHandler<B>,
    b: B,
    register: Boolean,
    temporary: Boolean,
  ) {
    if (register) {
      lock.withLock {
        if (!isBreakpointActive(b) || b in registeredBreakpoints) return
        registeredBreakpoints[b] = CustomizedBreakpointPresentation()
      }
      if (b is XLineBreakpoint<*>) {
        updateBreakpointPresentation(b, b.getType().pendingIcon, null)
      }
      handler.registerBreakpoint(b)
    }
    else {
      val removed = lock.withLock { registeredBreakpoints.remove(b) != null }
      if (removed) {
        handler.unregisterBreakpoint(b, temporary)
      }
    }
  }

  fun getBreakpointPresentation(breakpoint: XBreakpoint<*>): CustomizedBreakpointPresentation? = lock.withLock {
    registeredBreakpoints[breakpoint]
  }

  private fun processAllHandlers(breakpoint: XBreakpoint<*>, register: Boolean) {
    for (handler in session.debugProcess.breakpointHandlers) {
      processBreakpoint(breakpoint, handler, register)
    }
  }

  private fun <B : XBreakpoint<*>> processBreakpoint(
    breakpoint: B,
    handler: XBreakpointHandler<*>,
    register: Boolean,
  ) {
    val type = breakpoint.type
    if (handler.breakpointTypeClass == type.javaClass) {
      @Suppress("UNCHECKED_CAST")
      handleBreakpoint(handler as XBreakpointHandler<B>, breakpoint, register, temporary = false)
    }
  }

  fun isBreakpointActive(b: XBreakpoint<*>): Boolean = lock.withLock {
    !session.sessionData.isBreakpointsMuted && b.isEnabled && b !in inactiveSlaveBreakpoints && !(b as XBreakpointBase<*, *, *>).isDisposed
  }

  fun areBreakpointsMuted(): Boolean = lock.withLock {
    session.sessionData.isBreakpointsMuted
  }

  fun getBreakpointsMutedFlow(): StateFlow<Boolean> = session.sessionData.breakpointsMutedFlow

  fun setBreakpointMuted(muted: Boolean): Boolean {
    val trigger = lock.withLock {
      if (session.sessionData.isBreakpointsMuted == muted) return false
      session.sessionData.isBreakpointsMuted = muted
      !breakpointsDisabled
    }
    if (trigger) {
      processAllBreakpoints(register = !muted, temporary = muted)
    }
    return true
  }

  private fun processAllBreakpoints(register: Boolean, temporary: Boolean) {
    for (handler in session.debugProcess.breakpointHandlers) {
      processBreakpoints<XBreakpoint<*>>(handler, register, temporary)
    }
  }

  fun setBreakpointsDisabledTemporarily(disabled: Boolean) {
    lock.withLock {
      if (breakpointsDisabled == disabled) return
      breakpointsDisabled = disabled
    }
    if (!areBreakpointsMuted()) {
      processAllBreakpoints(register = !disabled, temporary = disabled)
    }
  }

  fun updateBreakpointPresentation(
    breakpoint: XBreakpoint<*>,
    icon: Icon?,
    errorMessage: String?,
  ) {
    lock.withLock {
      val presentation = registeredBreakpoints[breakpoint] ?: return
      if (Comparing.equal<Icon?>(presentation.icon, icon)
          && Comparing.strEqual(presentation.errorMessage, errorMessage)) {
        return
      }

      presentation.errorMessage = errorMessage
      presentation.icon = icon

      val timestamp = presentation.timestamp
      if (timestamp != 0L && XDebuggerUtilImpl.getVerifiedIcon(breakpoint) == icon) {
        val delay = System.currentTimeMillis() - timestamp
        presentation.timestamp = 0
        reportBreakpointVerified(breakpoint, delay)
      }
    }
    if (breakpoint is XBreakpointBase<*, *, *>) {
      breakpoint.fireBreakpointPresentationUpdated(session)
    }
  }

  fun handleTemporaryBreakpointHit(breakpoint: XBreakpoint<*>?) {
    session.addSessionListener(object : XDebugSessionListener {
      private fun removeBreakpoint() {
        session.removeSessionListener(this)
        XDebuggerUtil.getInstance().removeBreakpoint(session.project, breakpoint)
      }

      override fun sessionResumed() {
        removeBreakpoint()
      }

      override fun sessionStopped() {
        removeBreakpoint()
      }
    })
  }

  fun processDependencies(breakpoint: XBreakpoint<*>) {
    val dependentBreakpointManager = breakpointManager.dependentBreakpointManager
    if (!dependentBreakpointManager.isMasterOrSlave(breakpoint)) return

    val breakpoints = dependentBreakpointManager.getSlaveBreakpoints(breakpoint)
    lock.withLock {
      inactiveSlaveBreakpoints.removeAll(breakpoints)
    }
    for (slaveBreakpoint in breakpoints) {
      processAllHandlers(slaveBreakpoint, register = true)
    }

    if (dependentBreakpointManager.getMasterBreakpoint(breakpoint) != null && !dependentBreakpointManager.isLeaveEnabled(breakpoint)) {
      val added = lock.withLock { inactiveSlaveBreakpoints.add(breakpoint) }
      if (added) {
        processAllHandlers(breakpoint, register = false)
      }
    }
  }

  fun unmuteOnStop() {
    if (XDebuggerSettingManagerImpl.getInstanceImpl().generalSettings.isUnmuteOnStop) {
      lock.withLock {
        session.sessionData.isBreakpointsMuted = false
      }
    }
  }

  fun clearRegisteredBreakpoints() {
    lock.withLock {
      registeredBreakpoints.clear()
    }
  }

  fun removeBreakpointListeners() {
    val disposable = lock.withLock {
      val current = breakpointListenerDisposable
      breakpointListenerDisposable = null
      current
    }
    if (disposable != null) {
      Disposer.dispose(disposable)
    }
  }

  fun isInactiveSlaveBreakpoint(breakpoint: XBreakpoint<*>): Boolean = lock.withLock {
    breakpoint in inactiveSlaveBreakpoints
  }

  private inner class MyBreakpointListener : XBreakpointListener<XBreakpoint<*>> {
    override fun breakpointAdded(breakpoint: XBreakpoint<*>) {
      if (processAdd(breakpoint)) {
        lock.withLock {
          val presentation = registeredBreakpoints[breakpoint] ?: return@withLock
          if (XDebuggerUtilImpl.getVerifiedIcon(breakpoint) == presentation.icon) {
            reportBreakpointVerified(breakpoint, 0)
          }
          else {
            presentation.timestamp = System.currentTimeMillis()
          }
        }
      }
    }

    override fun breakpointRemoved(breakpoint: XBreakpoint<*>) {
      session.checkActiveNonLineBreakpointOnRemoval(breakpoint)
      processAllHandlers(breakpoint, register = false)
    }

    private fun processAdd(breakpoint: XBreakpoint<*>): Boolean {
      lock.withLock {
        if (breakpointsDisabled || breakpoint in registeredBreakpoints) return false
      }
      processAllHandlers(breakpoint, register = true)
      return true
    }

    override fun breakpointChanged(breakpoint: XBreakpoint<*>) {
      processAllHandlers(breakpoint, register = false)
      processAdd(breakpoint)
    }
  }

  private inner class MyDependentBreakpointListener : XDependentBreakpointListener {
    override fun dependencySet(slave: XBreakpoint<*>, master: XBreakpoint<*>) {
      val added = lock.withLock { inactiveSlaveBreakpoints.add(slave) }
      if (added) {
        processAllHandlers(slave, register = false)
      }
    }

    override fun dependencyCleared(breakpoint: XBreakpoint<*>) {
      val removed = lock.withLock { inactiveSlaveBreakpoints.remove(breakpoint) }
      if (removed) {
        processAllHandlers(breakpoint, register = true)
      }
    }
  }

  companion object {
    private val LOG = logger<XDebugSessionBreakpointManager>()
  }
}
