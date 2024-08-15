// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.codeWithMe.ClientId.Companion.decorateCallable
import com.intellij.codeWithMe.ClientId.Companion.decorateRunnable
import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.diagnostic.PluginException
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.*
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
import com.intellij.util.ReflectionUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.containers.Stack
import com.intellij.util.ui.EDT
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.lang.Deprecated
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import javax.swing.JComponent
import kotlin.concurrent.Volatile

@ApiStatus.Internal
internal object RwLockHolder: ThreadingSupport {
  private val logger = Logger.getInstance(RwLockHolder::class.java)

  @JvmField
  internal var lock: ReadMostlyRWLock? = null

  private var myReadActionListener: ReadActionListener? = null
  private var myWriteActionListener: WriteActionListener? = null

  private val myWriteActionsStack = Stack<Class<*>>()
  private var myWriteStackBase = 0
  @Volatile
  private var myWriteActionPending = false
  private var myNoWriteActionCounter = AtomicInteger()

  private var isWriteIntentUnlocked = false

  @Internal
  override fun postInit(writeThread: Thread) {
    lock = ReadMostlyRWLock(writeThread)
  }

  private fun notReady(): Nothing {
    error("Lock and IDE Queue are not ready yet");
  }

  // @Throws(E::class)
  override fun <T, E : Throwable?> runWriteIntentReadAction(computation: ThrowableComputable<T, E>): T {
    val l = lock ?: notReady()
    val writeIntentLock = acquireWriteIntentLock(computation.javaClass.getName())
    try {
      return computation.compute()
    }
    finally {
      if (writeIntentLock) {
        l.writeIntentUnlock()
      }
    }
  }

  override fun acquireWriteIntentLock(invokedClassFqn: String?): Boolean {
    val l = lock ?: notReady()
    if (l.isWriteThread && (l.isWriteIntentLocked || l.isWriteAcquired)) {
      return false
    }
    l.writeIntentLock()
    return true
  }

  override fun releaseWriteIntentLock() {
    val l = lock ?: notReady()
    l.writeIntentUnlock()
  }

  override fun isWriteIntentLocked(): Boolean {
    val l = lock
    return l == null || l.isWriteThread && (l.isWriteIntentLocked || l.isWriteAcquired)
  }

  override fun isReadAccessAllowed(): Boolean {
    val l = lock
    return l == null || l.isReadAllowed
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
    val l = lock ?: notReady()
    val oldVal = l.isImplicitReadAllowed
    try {
      l.setAllowImplicitRead(false)
      runnable.run()
    }
    finally {
      l.setAllowImplicitRead(oldVal)
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
    val l = lock ?: notReady()
    val oldVal = l.isImplicitReadAllowed
    try {
      l.setAllowImplicitRead(true)
      runnable.run()
    }
    finally {
      l.setAllowImplicitRead(oldVal)
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
      isWriteIntentUnlocked = true
      try {
        action.compute()
      }
      finally {
        isWriteIntentUnlocked = false
        acquireWriteIntentLock(action.javaClass.name)
      }
    }
    else {
      try {
        isWriteIntentUnlocked = true
        action.compute()
      }
      finally {
        isWriteIntentUnlocked = false
      }
    }
  }

  @ApiStatus.Internal
  override fun setReadActionListener(listener: ReadActionListener) {
    if (myReadActionListener != null)
      error("ReadActionListener already registered")
    myReadActionListener = listener
  }

  @ApiStatus.Internal
  override fun removeReadActionListener(listener: ReadActionListener) {
    if (myReadActionListener != listener)
      error("ReadActionListener is not registered")
    myReadActionListener = null
  }

  override fun runReadAction(action: Runnable) {
    val l = lock ?: notReady()
    fireBeforeReadActionStart(action.javaClass)
    val permit = l.startRead()
    try {
      fireReadActionStarted(action.javaClass)
      action.run()
      fireReadActionFinished(action.javaClass)
    }
    finally {
      if (permit != null) {
        l.endRead(permit)
        fireAfterReadActionFinished(action.javaClass)
      }
    }
  }

  override fun <T> runReadAction(computation: Computable<T>): T {
    val l = lock ?: notReady()
    fireBeforeReadActionStart(computation.javaClass)
    val permit = l.startRead()
    try {
      fireReadActionStarted(computation.javaClass)
      val rv = computation.compute()
      fireReadActionFinished(computation.javaClass)
      return rv;
    }
    finally {
      if (permit != null) {
        l.endRead(permit)
        fireAfterReadActionFinished(computation.javaClass)
      }
    }
  }

  override fun <T, E : Throwable?> runReadAction(computation: ThrowableComputable<T, E>): T {
    val l = lock ?: notReady()
    fireBeforeReadActionStart(computation.javaClass)
    val permit = l.startRead()
    try {
      fireReadActionStarted(computation.javaClass)
      val rv = computation.compute()
      fireReadActionFinished(computation.javaClass)
      return rv;
    }
    finally {
      if (permit != null) {
        l.endRead(permit)
        fireAfterReadActionFinished(computation.javaClass)
      }
    }
  }

  override fun tryRunReadAction(action: Runnable): Boolean {
    val l = lock ?: notReady()
    fireBeforeReadActionStart(action.javaClass)
    val permit = l.startTryRead()
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
        l.endRead(permit)
        fireAfterReadActionFinished(action.javaClass)
      }
    }
    return true
  }

  override fun isReadLockedByThisThread(): Boolean {
    val l = lock ?: notReady()
    return l.isReadLockedByThisThread
  }

  @ApiStatus.Internal
  override fun setWriteActionListener(listener: WriteActionListener) {
    if (myWriteActionListener != null)
      error("WriteActionListener already registered")
    myWriteActionListener = listener
  }

  @ApiStatus.Internal
  override fun removeWriteActionListener(listener: WriteActionListener) {
    if (myWriteActionListener != listener)
      error("WriteActionListener is not registered")
    myWriteActionListener = null
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
    val l = lock ?: notReady()
    ThreadingAssertions.assertWriteIntentReadAccess()
    if (!l.isWriteAcquired) {
      runModalProgress(project, title, runnable)
      return
    }

    val prevBase = myWriteStackBase
    myWriteStackBase = myWriteActionsStack.size
    try {
      l.writeSuspendWhilePumpingIdeEventQueueHopingForTheBest { runModalProgress(project, title, runnable) }
    }
    finally {
      myWriteStackBase = prevBase
    }
  }

  override fun isWriteActionInProgress(): Boolean {
    val l = lock ?: notReady()
    return l.isWriteAcquired
  }

  override fun isWriteActionPending(): Boolean {
    val l = lock ?: notReady()
    // writeSuspendWhilePumpingIdeEventQueueHopingForTheBest() could have WriteRequested but
    // not myWriteActionPending set, and it could confuse runActionAndCancelBeforeWrite()
    return myWriteActionPending || l.isWriteRequested
  }

  override fun isWriteAccessAllowed(): Boolean {
    val l = lock
    return l == null || l.isWriteThread && l.isWriteAcquired
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
    val l = lock ?: notReady()
    return if (l.isWriteIntentLocked || l.isReadLockedByThisThread) AccessToken.EMPTY_ACCESS_TOKEN else ReadAccessToken()
  }

  @Deprecated
  override fun acquireWriteActionLock(marker: Class<*>): AccessToken {
    PluginException.reportDeprecatedUsage("ThreadingSupport.acquireWriteActionLock", "Use `runWriteAction()` instead")
    return WriteAccessToken(marker)
  }

  override fun prohibitWriteActionsInside(): AccessToken {
    val l = lock ?: notReady()
    if (myWriteActionPending || l.isWriteAcquired || !EDT.isCurrentThreadEdt()) {
      throwCannotWriteException()
    }
    myNoWriteActionCounter.incrementAndGet()
    return object: AccessToken() {
      override fun finish() {
        myNoWriteActionCounter.decrementAndGet()
      }
    }
  }

  override fun executeByImpatientReader(runnable: Runnable) {
    if (EDT.isCurrentThreadEdt()) {
      runnable.run()
    }
    else {
      val l = lock ?: notReady()
      l.executeByImpatientReader(runnable)
    }
  }

  override fun isInImpatientReader(): Boolean {
    val l = lock ?: notReady()
    return l.isInImpatientReader
  }

  override fun isInsideUnlockedWriteIntentLock(): Boolean {
    return isWriteIntentUnlocked
  }

  private fun startWrite(clazz: Class<*>) {
    assertNotInsideListener()
    val l = lock ?: notReady()
    // We should not set "pending write action" if write action can not be run at all - for example, if thread is wrong
    // See IDEA-338808
    // This change will modify behavior of all Application listeners: erroneous write actions will not fire pre-run
    // callback (beforeWriteActionStart) anymore, but it should be Ok and lead to less cancelled read actions.
    l.checkForPossibilityOfWriteLock()

    // Check that write action is not disabled
    // NB: It is before all cancellations will be run via fireBeforeWriteActionStart
    // It is change for old behavior, when ProgressUtilService checked this AFTER all cancellations.
    if (myNoWriteActionCounter.get() > 0) {
      throwCannotWriteException()
    }

    myWriteActionPending = true
    try {
      if (myWriteActionsStack.isEmpty()) {
        fireBeforeWriteActionStart(clazz)
      }

      // otherwise (when myLock is locked) there's a nesting write action:
      // - allow it,
      // - fire listeners for it (somebody can rely on having listeners fired for each write action)
      // - but do not re-acquire any locks because it could be deadlock-level dangerous
      if (!l.isWriteAcquired) {
        val delay = ApplicationImpl.Holder.ourDumpThreadsOnLongWriteActionWaiting
        val reportSlowWrite: Future<*>? = if (delay <= 0 || PerformanceWatcher.getInstanceIfCreated() == null) null
        else AppExecutorUtil.getAppScheduledExecutorService()
          .scheduleWithFixedDelay({ PerformanceWatcher.getInstance().dumpThreads("waiting", true, true) },
                                  delay.toLong(), delay.toLong(), TimeUnit.MILLISECONDS)
        val t = System.currentTimeMillis()
        l.writeLock()
        val elapsed = System.currentTimeMillis() - t
        WriteDelayDiagnostics.registerWrite(elapsed)
        if (logger.isDebugEnabled) {
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
    val l = lock ?: notReady()
    fireWriteActionFinished(clazz)
    myWriteActionsStack.pop()
    if (myWriteActionsStack.size == myWriteStackBase) {
      l.writeUnlock()
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
      myReadActionListener?.beforeReadActionStart(clazz)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireReadActionStarted(clazz: Class<*>) {
    try {
      myReadActionListener?.readActionStarted(clazz)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireReadActionFinished(clazz: Class<*>) {
    try {
      myReadActionListener?.readActionFinished(clazz)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireAfterReadActionFinished(clazz: Class<*>) {
    try {
      myReadActionListener?.afterReadActionFinished(clazz)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireBeforeWriteActionStart(clazz: Class<*>) {
    try {
      myWriteActionListener?.beforeWriteActionStart(clazz)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireWriteActionStarted(clazz: Class<*>) {
    try {
      myWriteActionListener?.writeActionStarted(clazz)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireWriteActionFinished(clazz: Class<*>) {
    try {
      myWriteActionListener?.writeActionFinished(clazz)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireAfterWriteActionFinished(clazz: Class<*>) {
    try {
      myWriteActionListener?.afterWriteActionFinished(clazz)
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
  private class ReadAccessToken : AccessToken() {
    private val myReader: ReadMostlyRWLock.Reader? = lock?.startRead()

    override fun finish() {
      fireReadActionFinished(javaClass)
      lock?.endRead(myReader)
      fireAfterReadActionFinished(javaClass)
    }
  }

  @Deprecated
  private class WriteAccessToken(private val clazz: Class<*>) : AccessToken() {
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

  private fun throwCannotWriteException() {
    throw java.lang.IllegalStateException("Write actions are prohibited")
  }
}
