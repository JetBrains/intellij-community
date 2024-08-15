// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.codeWithMe.ClientId.Companion.decorateCallable
import com.intellij.codeWithMe.ClientId.Companion.decorateRunnable
import com.intellij.core.rwmutex.*
import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.diagnostic.PluginException
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.util.PotemkinProgress
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.text.StringUtil
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import javax.swing.JComponent

private class ThreadState(var permit: Permit? = null, var prevPermit: WriteIntentPermit? = null, var inListener: Boolean = false, var impatientReader: Boolean = false) {
  fun release() {
    permit?.release()
    permit = prevPermit
    prevPermit = null
  }
  var writeIntentReleased = false
  val hasPermit get() = permit != null
  val hasRead get() = permit is ReadPermit
  val hasWriteIntent get() = permit is WriteIntentPermit
  val hasWrite get() = permit is WritePermit
}

@Suppress("SSBasedInspection")
@ApiStatus.Internal
internal object AnyThreadWriteThreadingSupport: ThreadingSupport {
  private val logger = Logger.getInstance(AnyThreadWriteThreadingSupport::class.java)

  private const val SPIN_TO_WAIT_FOR_LOCK: Int = 100

  @JvmField
  internal val lock = RWMutexIdea()

  private var myReadActionListener: ReadActionListener? = null
  private var myWriteActionListener: WriteActionListener? = null

  private val myWriteActionsStack = Stack<Class<*>>()
  private var myWriteStackBase = 0
  private val myWriteActionPending = AtomicInteger(0)
  private var myNoWriteActionCounter = AtomicInteger()

  private val myState = ThreadLocal.withInitial { ThreadState(null, null,false) }

  @Volatile
  private var myWriteAcquired: Thread? = null

  // @Throws(E::class)
  override fun <T, E : Throwable?> runWriteIntentReadAction(computation: ThrowableComputable<T, E>): T {
    val ts = myState.get()
    var release = true
    when(ts.permit) {
      null -> ts.permit = getWriteIntentPermit()
      is ReadPermit -> throw IllegalStateException("ReadWriteIntentAction can not be called from ReadAction")
      is WriteIntentPermit, is WritePermit -> release = false
    }

    try {
      return computation.compute()
    }
    finally {
      if (release) {
        ts.release()
      }
    }
  }

  override fun acquireWriteIntentLock(invokedClassFqn: String?): Boolean {
    // Legacy support.
    // This method is called by:
    // (a) bytecode instrumentation, which is not used anymore
    // (b) ApplicationImpl on EDT when implicit read on EDT is enabled
    throw UnsupportedOperationException("WriteAction on any thread is not compatible with implicit read on EDT")
  }

  override fun releaseWriteIntentLock() {
    // See acquireWriteIntentLock()
    throw UnsupportedOperationException("WriteAction on any thread is not compatible with implicit read on EDT")
  }

  override fun isWriteIntentLocked(): Boolean {
    val ts = myState.get()
    return ts.hasWrite || ts.hasWriteIntent
  }

  override fun isReadAccessAllowed(): Boolean {
    return myState.get().hasPermit
  }

  override fun runWithoutImplicitRead(runnable: Runnable) {
    // There is no implicit read, as there is no write thread anymore
    runnable.run()
  }

  override fun runWithImplicitRead(runnable: Runnable) {
    // There is no implicit read, as there is no write thread anymore
    runnable.run()
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
    runWriteIntentReadAction<Unit, Throwable> {
      action.run()
    }
  }

  // @Throws(E::class)
  override fun <T, E : Throwable?> runUnlockingIntendedWrite(action: ThrowableComputable<T, E>): T {
    val ts = myState.get()
    if (!ts.hasWriteIntent) {
      try {
        ts.writeIntentReleased = true
        return action.compute()
      }
      finally {
        ts.writeIntentReleased = false
      }
    }
    ts.release()
    ts.writeIntentReleased = true
    try {
      return action.compute()
    }
    finally {
      ts.writeIntentReleased = false
      ts.permit = getWriteIntentPermit()
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

  override fun runReadAction(action: Runnable) = runReadAction<Unit, Throwable>(action.javaClass) { action.run() }

  override fun <T> runReadAction(computation: Computable<T>): T  = runReadAction<T, Throwable>(computation.javaClass) { computation.compute() }

  override fun <T, E : Throwable?> runReadAction(computation: ThrowableComputable<T, E>): T = runReadAction(computation.javaClass, computation)

  private fun <T, E : Throwable?> runReadAction(clazz: Class<*>, block: ThrowableComputable<T, E>): T {
    fireBeforeReadActionStart(clazz)
    val ts = myState.get()
    if (ts.hasPermit) {
      fireReadActionStarted(clazz)
      val rv = block.compute()
      fireReadActionFinished(clazz)
      return rv
    }
    else {
      ts.permit = tryGetReadPermit()
      if (ts.permit == null) {
        val progress = ProgressManager.getInstance()
        // Impatient reader not in non-cancellable session will not wait
        if (ts.impatientReader && !progress.isInNonCancelableSection) {
          throw ApplicationUtil.CannotRunReadActionException.create()
        }
        // Check for cancellation
        val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator()
        if (indicator == null || progress.isInNonCancelableSection) {
          // Nothing to check or cannot be canceled
          ts.permit = getReadPermit()
        }
        else {
          var iter = 0
          do {
            if (indicator.isCanceled) {
              throw ProcessCanceledException()
            }
            if (iter++ < SPIN_TO_WAIT_FOR_LOCK) {
              Thread.yield()
              ts.permit = tryGetReadPermit()
            }
            else {
              ts.permit = getReadPermit()// getReadPermitTimed (1) // millis
            }
          } while (ts.permit == null)
        }
      }
      try {
        fireReadActionStarted(clazz)
        val rv = block.compute()
        fireReadActionFinished(clazz)
        return rv
      }
      finally {
        ts.release()
        fireAfterReadActionFinished(clazz)
      }
    }
  }

  override fun tryRunReadAction(action: Runnable): Boolean {
    val ts = myState.get()
    if (ts.hasPermit) {
      fireReadActionStarted(action.javaClass)
      action.run()
      fireReadActionFinished(action.javaClass)
      return true
    }
    else {
      fireBeforeReadActionStart(action.javaClass)
      ts.permit = tryGetReadPermit()
      if (!ts.hasPermit) {
        return false
      }
      try {
        fireReadActionStarted(action.javaClass)
        action.run()
        fireReadActionFinished(action.javaClass)
        return true
      }
      finally {
        ts.release()
        fireAfterReadActionFinished(action.javaClass)
      }
    }
  }

  override fun isReadLockedByThisThread() = myState.get().hasRead

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

  override fun runWriteAction(action: Runnable) = runWriteAction<Unit, Throwable>(action.javaClass) { action.run() }

  override fun <T> runWriteAction(computation: Computable<T>): T = runWriteAction<T, Throwable>(computation.javaClass) { computation.compute() }

  override fun <T, E : Throwable?> runWriteAction(computation: ThrowableComputable<T, E>): T = runWriteAction(computation.javaClass, computation)

  private fun <T, E : Throwable?> runWriteAction(clazz: Class<*>, block: ThrowableComputable<T, E>): T {
    val ts = myState.get()
    val state = startWrite(ts, clazz)
    return try {
      block.compute()
    }
    finally {
      endWrite(ts, clazz, state)
    }
  }

  private fun startWrite(ts: ThreadState, clazz: Class<*>): Boolean {
    if (ts.hasRead) {
      throw IllegalStateException("WriteAction can not be called from ReadAction")
    }

    // Check that write action is not disabled
    // NB: It is before all cancellations will be run via fireBeforeWriteActionStart
    // It is change for old behavior, when ProgressUtilService checked this AFTER all cancellations.
    if (myNoWriteActionCounter.get() > 0) {
      throwCannotWriteException()
    }

    val release = !ts.hasWrite

    myWriteActionPending.incrementAndGet()
    if (myWriteActionsStack.isEmpty()) {
      fireBeforeWriteActionStart(ts, clazz)
    }

    if (release) {
      ts.permit = measureWriteLock { getWritePermit(ts) }
    }
    myWriteAcquired = Thread.currentThread()
    myWriteActionPending.decrementAndGet()

    myWriteActionsStack.push(clazz)
    fireWriteActionStarted(ts, clazz)

    return release
  }

  private fun endWrite(ts: ThreadState, clazz: Class<*>, release: Boolean) {
    fireWriteActionFinished(ts, clazz)
    myWriteActionsStack.pop()
    if (release) {
      ts.release()
      myWriteAcquired = null
      fireAfterWriteActionFinished(ts, clazz)
    }
  }

  override fun executeSuspendingWriteAction(project: Project?,
                                            title: @NlsContexts.DialogTitle String,
                                            runnable: Runnable) {
    ThreadingAssertions.assertWriteIntentReadAccess()
    val ts = myState.get()
    if (ts.hasWriteIntent) {
      runModalProgress(project, title, runnable)
      return
    }

    // We have write access
    val prevBase = myWriteStackBase
    myWriteStackBase = myWriteActionsStack.size
    myWriteAcquired = null
    ts.release()
    try {
      runModalProgress(project, title, runnable)
    }
    finally {
      ProgressIndicatorUtils.cancelActionsToBeCancelledBeforeWrite()
      ts.permit = getWritePermit(ts)
      myWriteAcquired = Thread.currentThread()
      myWriteStackBase = prevBase
    }
  }

  override fun isWriteActionInProgress(): Boolean = myWriteAcquired != null

  override fun isWriteActionPending(): Boolean = myWriteActionPending.get() > 0

  override fun isWriteAccessAllowed(): Boolean = myWriteAcquired == Thread.currentThread()

  @ApiStatus.Experimental
  override fun runWriteActionWithNonCancellableProgressInDispatchThread(title: @NlsContexts.ProgressTitle String,
                                                                        project: Project?,
                                                                        parentComponent: JComponent?,
                                                                        action: Consumer<in ProgressIndicator?>): Boolean =
    runEdtProgressWriteAction(title, project, parentComponent, null, action)

  @ApiStatus.Experimental
  override fun runWriteActionWithCancellableProgressInDispatchThread(title: @NlsContexts.ProgressTitle String,
                                                                     project: Project?,
                                                                     parentComponent: JComponent?,
                                                                     action: Consumer<in ProgressIndicator?>): Boolean =
    runEdtProgressWriteAction(title, project, parentComponent, IdeBundle.message("action.stop"), action)

  private fun runEdtProgressWriteAction(title: @NlsContexts.ProgressTitle String,
                                        project: Project?,
                                        parentComponent: JComponent?,
                                        @Nls(capitalization = Nls.Capitalization.Title) cancelText: String?,
                                        action: Consumer<in ProgressIndicator?>) =
    runWriteAction (action.javaClass, ThrowableComputable {
      val indicator = PotemkinProgress(title, project, parentComponent, cancelText)
      indicator.runInSwingThread { action.accept(indicator) }
      !indicator.isCanceled
    })

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
    val ts = myState.get()
    if (ts.hasWrite) {
      throw IllegalStateException("Write Action can not request Read Access Token")
    }
    if (ts.hasRead || ts.hasWriteIntent) {
      return AccessToken.EMPTY_ACCESS_TOKEN
    }
    ts.permit = getReadPermit()
    return ReadAccessToken()
  }

  @Deprecated
  override fun acquireWriteActionLock(marker: Class<*>): AccessToken {
    PluginException.reportDeprecatedUsage("ThreadingSupport.acquireWriteActionLock", "Use `runWriteAction()` instead")
    return WriteAccessToken(marker)
  }

  override fun prohibitWriteActionsInside(): AccessToken {
    if (isWriteActionPending()) {
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
      return
    }

    val ts = myState.get()
    ts.impatientReader = true
    try {
      runnable.run()
    }
    finally {
      ts.impatientReader = false
    }
  }

  override fun isInImpatientReader(): Boolean = myState.get().impatientReader

  override fun isInsideUnlockedWriteIntentLock(): Boolean = myState.get().writeIntentReleased

  private fun measureWriteLock(acquisitor: () -> WritePermit) : WritePermit {
    val delay = ApplicationImpl.Holder.ourDumpThreadsOnLongWriteActionWaiting
    val reportSlowWrite: Future<*>? = if (delay <= 0 || PerformanceWatcher.getInstanceIfCreated() == null) null
    else AppExecutorUtil.getAppScheduledExecutorService()
      .scheduleWithFixedDelay({ PerformanceWatcher.getInstance().dumpThreads("waiting", true, true) },
                              delay.toLong(), delay.toLong(), TimeUnit.MILLISECONDS)
    val t = System.currentTimeMillis()
    val permit = acquisitor()
    val elapsed = System.currentTimeMillis() - t
    try {
      WriteDelayDiagnostics.registerWrite(elapsed)
    }
    catch (thr: Throwable) {
      // we can be canceled here, it is an expected behavior
      if (thr !is ControlFlowException) {
        // Warn instead of error to avoid breaking acquiring the lock
        logger.warn("Failed to register write lock in diagnostics service", thr)
      }
    }
    if (logger.isDebugEnabled) {
      if (elapsed != 0L) {
        logger.debug("Write action wait time: $elapsed")
      }
    }
    reportSlowWrite?.cancel(false)
    return permit
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

  private fun fireBeforeWriteActionStart(ts: ThreadState, clazz: Class<*>) {
    ts.inListener = true
    try {
      myWriteActionListener?.beforeWriteActionStart(clazz)
    }
    catch (_: Throwable) {
    }
    finally {
      ts.inListener = false
    }
  }

  private fun fireWriteActionStarted(ts: ThreadState, clazz: Class<*>) {
    ts.inListener = true
    try {
      myWriteActionListener?.writeActionStarted(clazz)
    }
    catch (_: Throwable) {
    }
    finally {
      ts.inListener = false
    }
  }

  private fun fireWriteActionFinished(ts: ThreadState, clazz: Class<*>) {
    ts.inListener = true
    try {
      myWriteActionListener?.writeActionFinished(clazz)
    }
    catch (_: Throwable) {
    }
    finally {
      ts.inListener = false
    }
  }

  private fun fireAfterWriteActionFinished(ts: ThreadState, clazz: Class<*>) {
    ts.inListener = true
    try {
      myWriteActionListener?.afterWriteActionFinished(clazz)
    }
    catch (_: Throwable) {
    }
    finally {
      ts.inListener = false
    }
  }

  private fun runModalProgress(project: Project?, title: @NlsContexts.DialogTitle String, runnable: Runnable) {
    ProgressManager.getInstance().run(object : Task.Modal(project, title, false) {
      override fun run(indicator: ProgressIndicator) {
        runnable.run()
      }
    })
  }

  private fun getWriteIntentPermit(): WriteIntentPermit {
    return runSuspend {
      lock.acquireWriteIntentPermit()
    }
  }

  private fun getWritePermit(): WritePermit {
    return runSuspend {
      lock.acquireWritePermit()
    }
  }

  private fun getWritePermit(state: ThreadState): WritePermit {
    return when (state.permit) {
      null -> getWritePermit()
      is WriteIntentPermit -> {
        state.prevPermit = state.permit as WriteIntentPermit
        runSuspend { (state.permit as WriteIntentPermit).acquireWritePermit()  }
      }
      else -> error("Can not acquire write permit when hold ${state.permit}")
    }
  }

  private fun getReadPermit(): ReadPermit {
    return runSuspend {
      lock.acquireReadPermit(false)
    }
  }

  private fun tryGetReadPermit(): ReadPermit? {
    return lock.tryAcquireReadPermit()
  }

  @Deprecated
  private class ReadAccessToken : AccessToken() {
    private val myPermit = run {
      fireBeforeReadActionStart(javaClass)
      val p = getReadPermit()
      fireReadActionStarted(javaClass)
      p
    }

    override fun finish() {
      fireReadActionFinished(javaClass)
      myPermit.release()
      fireAfterReadActionFinished(javaClass)
    }
  }

  @Deprecated
  private class WriteAccessToken(private val clazz: Class<*>) : AccessToken() {
    val ts = myState.get()
    val release = startWrite(ts, clazz)

    init {
      markThreadNameInStackTrace()
    }

    override fun finish() {
      try {
        endWrite(ts, clazz, release)
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
