// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.codeWithMe.ClientId.Companion.decorateCallable
import com.intellij.codeWithMe.ClientId.Companion.decorateRunnable
import com.intellij.diagnostic.PerformanceWatcher.Companion.getInstance
import com.intellij.diagnostic.PluginException
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ReadActionListener
import com.intellij.openapi.application.ThreadingSupport
import com.intellij.openapi.application.WriteActionListener
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.PotemkinProgress
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.ide.bootstrap.isImplicitReadOnEDTDisabled
import com.intellij.util.EventDispatcher
import com.intellij.util.ReflectionUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.containers.Stack
import com.intellij.util.ui.EDT
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.lang.Deprecated
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import javax.swing.JComponent
import kotlin.concurrent.Volatile

@ApiStatus.Internal
class RwLockHolder(writeThread: Thread) : ThreadingSupport {
  private val logger = Logger.getInstance(RwLockHolder::class.java)

  @JvmField
  internal val lock: ReadMostlyRWLock = ReadMostlyRWLock(writeThread)

  private val myReadActionDispatcher = EventDispatcher.create(ReadActionListener::class.java)
  private val myWriteActionDispatcher = EventDispatcher.create(WriteActionListener::class.java)

  private val myWriteActionsStack = Stack<Class<*>>()
  private var myWriteStackBase = 0
  @Volatile
  private var myWriteActionPending = false

  // @Throws(E::class)
  override fun <T, E : Throwable?> runWriteIntentReadAction(computation: ThrowableComputable<T, E>): T {
    val writeIntentLock = acquireWriteIntentLock(computation.javaClass.getName())
    try {
      return computation.compute()
    }
    finally {
      if (writeIntentLock) {
        lock.writeIntentUnlock()
      }
    }
  }

  override fun acquireWriteIntentLock(invokedClassFqn: String?): Boolean {
    if (lock.isWriteThread && (lock.isWriteIntentLocked || lock.isWriteAcquired)) {
      return false
    }
    lock.writeIntentLock()
    return true
  }

  override fun releaseWriteIntentLock() {
    lock.writeIntentUnlock()
  }

  override fun isWriteIntentLocked(): Boolean {
    return lock.isWriteThread && (lock.isWriteIntentLocked || lock.isWriteAcquired)
  }

  override fun isReadAccessAllowed(): Boolean {
    return lock.isReadAllowed
  }

  override fun runWithoutImplicitRead(runnable: Runnable) {
    if (isImplicitReadOnEDTDisabled) {
      runnable.run()
      return
    }
    runWithDisabledImplicitRead(runnable)
  }

  private fun runWithDisabledImplicitRead(runnable: Runnable) {
    // This method is used to allow easily finding stack traces which violate disabled ImplicitRead
    val oldVal = lock.isImplicitReadAllowed
    try {
      lock.setAllowImplicitRead(false)
      runnable.run()
    }
    finally {
      lock.setAllowImplicitRead(oldVal)
    }
  }

  override fun runWithImplicitRead(runnable: Runnable) {
    if (!isImplicitReadOnEDTDisabled) {
      runnable.run()
      return
    }
    runWithEnabledImplicitRead(runnable)
  }

  private fun runWithEnabledImplicitRead(runnable: Runnable) {
    // This method is used to allow easily find stack traces which violate disabled ImplicitRead
    val oldVal = lock.isImplicitReadAllowed
    try {
      lock.setAllowImplicitRead(true)
      runnable.run()
    }
    finally {
      lock.setAllowImplicitRead(oldVal)
    }
  }

  override fun executeOnPooledThread(action: Runnable, expired: BooleanSupplier): Future<*> {
    val actionDecorated = decorateRunnable(action)
    return AppExecutorUtil.getAppExecutorService().submit(object : Runnable {
      override fun run() {
        if (expired.asBoolean) {
          return
        }

        try {
          actionDecorated.run()
        }
        catch (e: ProcessCanceledException) {
          // ignore
        }
        catch (e: Throwable) {
          logger.error(e)
        }
        finally {
          Thread.interrupted() // reset interrupted status
        }
      }

      override fun toString(): String {
        return action.toString()
      }
    })
  }

  override fun <T> executeOnPooledThread(action: Callable<T>, expired: BooleanSupplier): Future<T> {
    val actionDecorated = decorateCallable(action)
    return AppExecutorUtil.getAppExecutorService().submit<T>(object : Callable<T?> {
      override fun call(): T? {
        if (expired.asBoolean) {
          return null
        }

        try {
          return actionDecorated.call()
        }
        catch (e: ProcessCanceledException) {
          // ignore
        }
        catch (e: Throwable) {
          logger.error(e)
        }
        finally {
          Thread.interrupted() // reset interrupted status
        }
        return null
      }

      override fun toString(): String {
        return action.toString()
      }
    })
  }

  override fun runIntendedWriteActionOnCurrentThread(action: Runnable) {
    if (isWriteIntentLocked()) {
      action.run()
    }
    else {
      acquireWriteIntentLock(action.javaClass.name)
      try {
        action.run()
      }
      finally {
        releaseWriteIntentLock()
      }
    }
  }

  // @Throws(E::class)
  override fun <T, E : Throwable?> runUnlockingIntendedWrite(action: ThrowableComputable<T, E>): T {
    // Do not ever unlock IW in legacy mode (EDT is holding lock at all times)
    return if (isWriteIntentLocked() && isImplicitReadOnEDTDisabled) {
      releaseWriteIntentLock()
      try {
        action.compute()
      }
      finally {
        acquireWriteIntentLock(action.javaClass.name)
      }
    }
    else {
      action.compute()
    }
  }


  @Deprecated
  override fun addReadActionListener(listener: ReadActionListener) {
    myReadActionDispatcher.addListener(listener)
  }

  override fun addReadActionListener(listener: ReadActionListener, parent: Disposable) {
    myReadActionDispatcher.addListener(listener, parent)
  }

  @Deprecated
  override fun removeReadActionListener(listener: ReadActionListener) {
    myReadActionDispatcher.removeListener(listener)
  }

  override fun runReadAction(action: Runnable) {
    fireBeforeReadActionStart(action.javaClass)
    val permit = lock.startRead()
    try {
      fireReadActionStarted(action.javaClass)
      action.run()
      fireReadActionFinished(action.javaClass)
    }
    finally {
      if (permit != null) {
        lock.endRead(permit)
        fireAfterReadActionFinished(action.javaClass)
      }
    }
  }

  override fun <T> runReadAction(computation: Computable<T>): T {
    fireBeforeReadActionStart(computation.javaClass)
    val permit = lock.startRead()
    try {
      fireReadActionStarted(computation.javaClass)
      val rv = computation.compute()
      fireReadActionFinished(computation.javaClass)
      return rv;
    }
    finally {
      if (permit != null) {
        lock.endRead(permit)
        fireAfterReadActionFinished(computation.javaClass)
      }
    }
  }

  override fun <T, E : Throwable?> runReadAction(computation: ThrowableComputable<T, E>): T {
    fireBeforeReadActionStart(computation.javaClass)
    val permit = lock.startRead()
    try {
      fireReadActionStarted(computation.javaClass)
      val rv = computation.compute()
      fireReadActionFinished(computation.javaClass)
      return rv;
    }
    finally {
      if (permit != null) {
        lock.endRead(permit)
        fireAfterReadActionFinished(computation.javaClass)
      }
    }
  }

  override fun tryRunReadAction(action: Runnable): Boolean {
    fireBeforeReadActionStart(action.javaClass)
    val permit = lock.startTryRead()
    if (permit != null && !permit.readRequested) {
      return false
    }
    try {
      fireReadActionStarted(action.javaClass)
      action.run()
      fireReadActionFinished(action.javaClass)
    }
    finally {
      if (permit != null) {
        lock.endRead(permit)
        fireAfterReadActionFinished(action.javaClass)
      }
    }
    return true
  }

  override fun isReadLockedByThisThread(): Boolean {
    return lock.isReadLockedByThisThread
  }

  @Deprecated
  override fun addWriteActionListener(listener: WriteActionListener) {
    myWriteActionDispatcher.addListener(listener)
  }

  override fun addWriteActionListener(listener: WriteActionListener, parent: Disposable) {
    myWriteActionDispatcher.addListener(listener, parent)
  }

  @Deprecated
  override fun removeWriteActionListener(listener: WriteActionListener) {
    myWriteActionDispatcher.removeListener(listener)
  }

  override fun runWriteAction(action: Runnable) {
    startWrite(action.javaClass)
    try {
      action.run()
    }
    finally {
      endWrite(action.javaClass)
    }
  }

  override fun <T> runWriteAction(computation: Computable<T>): T {
    startWrite(computation.javaClass)
    try {
      return computation.compute()
    }
    finally {
      endWrite(computation.javaClass)
    }
  }

  override fun <T, E : Throwable?> runWriteAction(computation: ThrowableComputable<T, E>): T {
    startWrite(computation.javaClass)
    try {
      return computation.compute()
    }
    finally {
      endWrite(computation.javaClass)
    }
  }

  override fun executeSuspendingWriteAction(project: Project?,
                                            title: @NlsContexts.DialogTitle String,
                                            runnable: Runnable) {
    ThreadingAssertions.assertWriteIntentReadAccess()
    if (!lock.isWriteAcquired) {
      runModalProgress(project, title, runnable)
      return
    }

    val prevBase = myWriteStackBase
    myWriteStackBase = myWriteActionsStack.size
    try {
      lock.writeSuspendWhilePumpingIdeEventQueueHopingForTheBest { runModalProgress(project, title, runnable) }
    }
    finally {
      myWriteStackBase = prevBase
    }
  }

  override fun isWriteActionInProgress(): Boolean {
    return lock.isWriteAcquired
  }

  override fun isWriteActionPending(): Boolean {
    return myWriteActionPending
  }

  override fun isWriteAccessAllowed(): Boolean {
    return lock.isWriteThread && lock.isWriteAcquired
  }

  @ApiStatus.Experimental
  override fun runWriteActionWithNonCancellableProgressInDispatchThread(title: @NlsContexts.ProgressTitle String,
                                                                        project: Project?,
                                                                        parentComponent: JComponent?,
                                                                        action: Consumer<in ProgressIndicator?>): Boolean {
    return runEdtProgressWriteAction(title, project, parentComponent, null, action)
  }

  @ApiStatus.Experimental
  override fun runWriteActionWithCancellableProgressInDispatchThread(title: @NlsContexts.ProgressTitle String,
                                                                     project: Project?,
                                                                     parentComponent: JComponent?,
                                                                     action: Consumer<in ProgressIndicator?>): Boolean {
    return runEdtProgressWriteAction(title, project, parentComponent, IdeBundle.message("action.stop"), action)
  }

  private fun runEdtProgressWriteAction(title: @NlsContexts.ProgressTitle String,
                                        project: Project?,
                                        parentComponent: JComponent?,
                                        @Nls(capitalization = Nls.Capitalization.Title) cancelText: String?,
                                        action: Consumer<in ProgressIndicator?>): Boolean {
    return runWriteActionWithClass<Boolean, RuntimeException>(action.javaClass) {
      val indicator = PotemkinProgress(title, project, parentComponent, cancelText)
      indicator.runInSwingThread { action.accept(indicator) }
      !indicator.isCanceled
    }
  }

  // @Throws(E::class)
  private fun <T, E : Throwable?> runWriteActionWithClass(clazz: Class<*>, computable: ThrowableComputable<T, E>): T {
    startWrite(clazz)
    try {
      return computable.compute()
    }
    finally {
      endWrite(clazz)
    }
  }

  override fun hasWriteAction(actionClass: Class<*>): Boolean {
    ThreadingAssertions.softAssertReadAccess()

    for (i in myWriteActionsStack.size - 1 downTo 0) {
      val action = myWriteActionsStack[i]
      if (actionClass == action || ReflectionUtil.isAssignable(actionClass, action)) {
        return true
      }
    }
    return false
  }

  @Deprecated
  override fun acquireReadActionLock(): AccessToken {
    PluginException.reportDeprecatedUsage("ThreadingSupport.acquireReadActionLock", "Use `runReadAction()` instead")
    return if (lock.isWriteIntentLocked || lock.isReadLockedByThisThread) AccessToken.EMPTY_ACCESS_TOKEN else ReadAccessToken()
  }

  @Deprecated
  override fun acquireWriteActionLock(marker: Class<*>): AccessToken {
    PluginException.reportDeprecatedUsage("ThreadingSupport.acquireWriteActionLock", "Use `runWriteAction()` instead")
    return WriteAccessToken(marker)
  }

  override fun executeByImpatientReader(runnable: Runnable) {
    if (EDT.isCurrentThreadEdt()) {
      runnable.run()
    }
    else {
      lock.executeByImpatientReader(runnable)
    }
  }

  override fun isInImpatientReader(): Boolean {
    return lock.isInImpatientReader
  }

  private fun startWrite(clazz: Class<*>) {
    assertNotInsideListener()
    myWriteActionPending = true
    try {
      fireBeforeWriteActionStart(clazz)

      // otherwise (when myLock is locked) there's a nesting write action:
      // - allow it,
      // - fire listeners for it (somebody can rely on having listeners fired for each write action)
      // - but do not re-acquire any locks because it could be deadlock-level dangerous
      if (!lock.isWriteAcquired) {
        val delay = ApplicationImpl.Holder.ourDumpThreadsOnLongWriteActionWaiting
        val reportSlowWrite: Future<*>? = if (delay <= 0) null
        else AppExecutorUtil.getAppScheduledExecutorService()
          .scheduleWithFixedDelay({ getInstance().dumpThreads("waiting", true, true) },
                                  delay.toLong(), delay.toLong(), TimeUnit.MILLISECONDS)
        val t = if (logger.isDebugEnabled) System.currentTimeMillis() else 0
        lock.writeLock()
        if (logger.isDebugEnabled) {
          val elapsed = System.currentTimeMillis() - t
          if (elapsed != 0L) {
            logger.debug("Write action wait time: $elapsed")
          }
        }
        reportSlowWrite?.cancel(false)
      }
    }
    finally {
      myWriteActionPending = false
    }

    myWriteActionsStack.push(clazz)
    fireWriteActionStarted(clazz)
  }

  private fun endWrite(clazz: Class<*>) {
    fireWriteActionFinished(clazz)
    myWriteActionsStack.pop()
    if (myWriteActionsStack.size == myWriteStackBase) {
      lock.writeUnlock()
    }
    if (myWriteActionsStack.isEmpty()) {
      fireAfterWriteActionFinished(clazz)
    }
  }

  private fun assertNotInsideListener() {
    if (myWriteActionPending) {
      throw IllegalStateException("Must not start write action from inside write action listener")
    }
  }

  private fun fireBeforeReadActionStart(clazz: Class<*>) {
    try {
      myReadActionDispatcher.multicaster.beforeReadActionStart(clazz)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireReadActionStarted(clazz: Class<*>) {
    try {
      myReadActionDispatcher.multicaster.readActionStarted(clazz)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireReadActionFinished(clazz: Class<*>) {
    try {
      myReadActionDispatcher.multicaster.readActionFinished(clazz)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireAfterReadActionFinished(clazz: Class<*>) {
    try {
      myReadActionDispatcher.multicaster.afterReadActionFinished(clazz)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireBeforeWriteActionStart(clazz: Class<*>) {
    try {
      myWriteActionDispatcher.multicaster.beforeWriteActionStart(clazz)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireWriteActionStarted(clazz: Class<*>) {
    try {
      myWriteActionDispatcher.multicaster.writeActionStarted(clazz)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireWriteActionFinished(clazz: Class<*>) {
    try {
      myWriteActionDispatcher.multicaster.writeActionFinished(clazz)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireAfterWriteActionFinished(clazz: Class<*>) {
    try {
      myWriteActionDispatcher.multicaster.afterWriteActionFinished(clazz)
    }
    catch (_: Throwable) {
    }
  }

  private fun runModalProgress(project: Project?, title: @NlsContexts.DialogTitle String, runnable: Runnable) {
    ProgressManager.getInstance().run(object : Task.Modal(project, title, false) {
      override fun run(indicator: ProgressIndicator) {
        runnable.run()
      }
    })
  }


  @Deprecated
  private inner class ReadAccessToken : AccessToken() {
    private val myReader: ReadMostlyRWLock.Reader = lock.startRead()

    override fun finish() {
      fireReadActionFinished(javaClass)
      lock.endRead(myReader)
      fireAfterReadActionFinished(javaClass)
    }
  }

  @Deprecated
  private inner class WriteAccessToken(private val clazz: Class<*>) : AccessToken() {
    init {
      startWrite(clazz)
      markThreadNameInStackTrace()
    }

    override fun finish() {
      try {
        endWrite(clazz)
      }
      finally {
        unmarkThreadNameInStackTrace()
      }
    }

    private fun markThreadNameInStackTrace() {
      val id = id()

      val thread = Thread.currentThread()
      thread.name = thread.name + id
    }

    private fun unmarkThreadNameInStackTrace() {
      val id = id()

      val thread = Thread.currentThread()
      var name = thread.name
      name = StringUtil.replace(name!!, id, "")
      thread.name = name
    }

    private fun id(): String {
      return " [WriteAccessToken]"
    }
  }
}
