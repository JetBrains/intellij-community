// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.codeWithMe.ClientId.Companion.decorateCallable
import com.intellij.codeWithMe.ClientId.Companion.decorateRunnable
import com.intellij.concurrency.currentThreadContext
import com.intellij.core.rwmutex.*
import com.intellij.diagnostic.PluginException
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.LockAcquisitionListener
import com.intellij.openapi.application.isLockStoredInContext
import com.intellij.openapi.application.ThreadingSupport
import com.intellij.openapi.application.ReadActionListener
import com.intellij.openapi.application.SuspendingWriteActionListener
import com.intellij.openapi.application.WriteActionListener
import com.intellij.openapi.application.WriteIntentReadActionListener
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.application.reportInvalidActionChains
import com.intellij.openapi.application.useBackgroundWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.util.coroutines.internal.runSuspend
import com.intellij.util.ReflectionUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.containers.Stack
import com.intellij.util.ui.EDT
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BooleanSupplier
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private class ThreadState() {
  @Volatile
  var permit: Permit? = null
    private set
  private var prevPermit: WriteIntentPermit? = null

  var writeIntentReleased = false
  var inListener: Boolean = false

  @Volatile
  var sharedLock: RWMutexIdea? = null
    private set
  private val sharedCount = AtomicInteger(0)

  fun acquire(p: Permit) {
    check(permit == null || (permit is WriteIntentPermit && p is WritePermit)) {
      "Lock inconsistency: thread ${Thread.currentThread()} trys to acqure permit ${p} when holds ${permit}"
    }
    if (permit is WriteIntentPermit) {
      prevPermit = permit as WriteIntentPermit?
    }
    // Volatile write, release barrier
    permit = p
  }

  fun release() {
    permit?.release()
    if (prevPermit != null) {
      val pp = prevPermit
      prevPermit = null
      // Volatile write, release barrier
      permit = pp
    }
    else {
      // Volatile write, release barrier
      permit = null
    }
  }

  fun fork() {
    check(isLockStoredInContext) { "Operation is not supported when lock is not stored in context" }
    if (sharedCount.incrementAndGet() == 1) {
      sharedLock = RWMutexIdea()
    }
  }

  fun join() {
    check(isLockStoredInContext) { "Operation is not supported when lock is not stored in context" }
    val shared = sharedCount.decrementAndGet()
    check(shared >= 0) { "Lock balance problem: lock ${this} un-shared more than shared (${shared})" }
    if (shared == 0) {
      sharedLock = null
    }
  }

  val hasPermit get() = permit != null
  val hasRead get() = permit is ReadPermit
  val hasWriteIntent get() = permit is WriteIntentPermit
  val hasWrite get() = permit is WritePermit

  override fun toString(): String {
    return "ThreadState(permit=$permit,prevPermit=$prevPermit,writeIntentReleased=$writeIntentReleased)"
  }
}

private class LockStateContextElement(val threadState: ThreadState): CoroutineContext.Element {
  override val key: CoroutineContext.Key<*>
    get() = LockStateContextElement

  companion object : CoroutineContext.Key<LockStateContextElement>

  override fun toString(): String {
    return "LockStateContextElement($threadState)"
  }
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
  private var myWriteIntentActionListener: WriteIntentReadActionListener? = null
  private var myLockAcquisitionListener: LockAcquisitionListener? = null
  private var mySuspendingWriteActionListener: SuspendingWriteActionListener? = null

  private val myWriteActionsStack = Stack<Class<*>>()
  private var myWriteStackBase = 0
  private val myWriteActionPending = AtomicInteger(0)
  private var myNoWriteActionCounter = AtomicInteger()

  private val myState = ThreadLocal.withInitial { ThreadState() }
  // We approximate "on stack" permits with "thread local" permits for shared main lock
  private val mySecondaryPermits = ThreadLocal.withInitial { ArrayList<Permit>() }
  private val myReadActionsInThread = ThreadLocal.withInitial { 0 }
  private val myImpatientReader = ThreadLocal.withInitial { false }

  // todo: reimplement with listeners in IJPL-177760
  private val myTopmostReadAction = ThreadLocal.withInitial { false }

  @Volatile
  private var myWriteAcquired: Thread? = null

  @Volatile
  private var myWriteIntentAcquired: Thread? = null

  override fun getPermitAsContextElement(baseContext: CoroutineContext, shared: Boolean): CoroutineContext {
    if (!isLockStoredInContext) {
      return EmptyCoroutineContext
    }

    val element = baseContext[LockStateContextElement]
    if (element?.threadState?.permit != null) {
      if (shared) {
        element.threadState.fork()
      }
      return element
    }

    val ts = myState.get()
    if (ts.permit != null) {
      if (shared) {
        ts.fork()
      }
      return LockStateContextElement(ts)
    }

    return EmptyCoroutineContext
  }

  override fun returnPermitFromContextElement(ctx: CoroutineContext) {
    if (!isLockStoredInContext) {
      return
    }

    if (ctx is LockStateContextElement) {
      val ts = ctx.threadState
      ts.join()
    }
  }

  override fun hasPermitAsContextElement(context: CoroutineContext): Boolean = isLockStoredInContext && context[LockStateContextElement] != null

  override fun isInTopmostReadAction(): Boolean {
    // once a read action was requested
    return myTopmostReadAction.get()
  }

  private fun getThreadState(): ThreadState {
    val ctxState = if (isLockStoredInContext) currentThreadContext()[LockStateContextElement]?.threadState else null
    val thrState = myState.get()
    if (ctxState?.hasPermit == true) {
      // Special case: another thread upgraded lock in context, and we remember the previous one, which is WIL
      // Or vice versa, we remember "write" state, and it was downgraded by another thread already
      // It could be changed in any moment, so there is no reason to fixup
      val statesAreCompatible = thrState.permit == null ||
                                ctxState.permit == thrState.permit

      check(statesAreCompatible) { "Lock inconsistency: thread ${Thread.currentThread()} has ${thrState.permit} and context is ${currentThreadContext()}" }
      return ctxState
    }
    return thrState
  }

  private fun <T> runWithTemporaryThreadLocal(state: ThreadState, action: () -> T): T {
    if (!isLockStoredInContext) {
      return action()
    }

    val thrState = myState.get()
    // We use thread state
    if (thrState === state) {
      return action()
    }

    // We use context state
    myState.set(state)
    try {
      return action()
    }
    finally {
      myState.set(thrState)
    }
  }

  // @Throws(E::class)
  override fun <T, E : Throwable?> runWriteIntentReadAction(computation: ThrowableComputable<T, E>): T {
    fireBeforeWriteIntentReadActionStart(computation.javaClass)
    val currentReadState = myTopmostReadAction.get()
    myTopmostReadAction.set(false)

    val ts = getThreadState()
    var release = true
    var releaseSecondary = false

    // See similar technique in `startWrite`
    val sharedLock = ts.sharedLock
    if (sharedLock != null) {
      // Check secondary protection lock
      val sps = mySecondaryPermits.get()
      when (sps.lastOrNull()) {
        null -> {
          sps.add(getWriteIntentPermit(sharedLock))
          releaseSecondary = true
        }
        is ReadPermit -> error("WriteIntentReadAction can not be called from ReadAction")
        is WriteIntentPermit, is WritePermit -> {}
      }
    }

    when (ts.permit) {
      null -> {
        ts.acquire(getWriteIntentPermit())
        myWriteIntentAcquired = Thread.currentThread()
      }
      is ReadPermit -> error("WriteIntentReadAction can not be called from ReadAction")
      is WriteIntentPermit -> {
        // Volatile read
        release = false
        checkWriteFromRead("Write Intent Read", "Write Intent")
      }
      is WritePermit -> {
        release = false
        checkWriteFromRead("Write Intent Read", "Write")
      }
    }

    val prevImplicitLock = ThreadingAssertions.isImplicitLockOnEDT()
    try {
      ThreadingAssertions.setImplicitLockOnEDT(false)
      fireWriteIntentActionStarted(computation.javaClass)
      return runWithTemporaryThreadLocal(ts) { computation.compute() }
    }
    finally {
      fireWriteIntentActionFinished(computation.javaClass)
      ThreadingAssertions.setImplicitLockOnEDT(prevImplicitLock)
      if (release) {
        ts.release()
        myWriteIntentAcquired = null
      }
      if (releaseSecondary) {
        mySecondaryPermits.get().removeLast().release()
      }
      myTopmostReadAction.set(currentReadState)
      afterWriteIntentReadActionFinished(computation.javaClass)
    }
  }

  override fun isWriteIntentLocked(): Boolean {
    val ts = myState.get()
    val shared = ts.sharedLock
    if (shared == null) {
      return ts.hasWrite || ts.hasWriteIntent
    }
    else {
      return myWriteIntentAcquired == Thread.currentThread()
    }
  }

  override fun isReadAccessAllowed(): Boolean {
    val threadState = getThreadState()
    val shared = threadState.sharedLock
    if (shared == null) {
      // Having any permit (r/w/wi) without the presence of the second lock means that this thread has _some_ permission,
      // and even the weakest possible permission allows read access
      return threadState.hasPermit
    }
    else {
      // When there is the second lock installed, it is not enough to look at the primary lock:
      // the current thread now has inherited write intent permit, which should not give read access.
      // Otherwise, it would be impossible to upgrade WI to W
      return !mySecondaryPermits.get().isNullOrEmpty()
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
    runWriteIntentReadAction<Unit, Throwable> {
      action.run()
    }
  }

  // @Throws(E::class)
  override fun <T, E : Throwable?> runUnlockingIntendedWrite(action: ThrowableComputable<T, E>): T {
    if (isLockStoredInContext) {
      return action.compute()
    }

    val ts = getThreadState()
    if (!ts.hasWriteIntent) {
      try {
        ts.writeIntentReleased = true
        return action.compute()
      }
      finally {
        ts.writeIntentReleased = false
      }
    }

    ts.writeIntentReleased = true
    ts.release()
    try {
      return action.compute()
    }
    finally {
      ts.writeIntentReleased = false
      ts.acquire(getWriteIntentPermit())
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

  private fun acquireReadPermit(lock: RWMutexIdea): Permit {
    var permit = tryGetReadPermit(lock)
    if (permit != null) {
      return permit
    }

    // Impatient reader not in non-cancellable session will not wait
    if (myImpatientReader.get() && !Cancellation.isInNonCancelableSection()) {
      throw ApplicationUtil.CannotRunReadActionException.create()
    }

    // Check for cancellation
    val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator()
    // Nothing to check or cannot be canceled
    if (indicator == null || Cancellation.isInNonCancelableSection()) {
      return getReadPermit(lock)
    }

    // Spin & sleep with checking for cancellation
    var iter = 0
    do {
      if (indicator.isCanceled) {
        throw ProcessCanceledException()
      }
      if (iter++ < SPIN_TO_WAIT_FOR_LOCK) {
        Thread.yield()
        permit = tryGetReadPermit(lock)
      }
      else {
        permit = getReadPermit(lock) // getReadPermitTimed (1) // millis
      }
    } while (permit == null)
    return permit
  }

  private fun <T, E : Throwable?> runReadAction(clazz: Class<*>, block: ThrowableComputable<T, E>): T {
    fireBeforeReadActionStart(clazz)

    val currentReadState = myTopmostReadAction.get()
    myTopmostReadAction.set(true)

    val ts = getThreadState()
    var release = false
    var releaseSecondary = false

    // We must acquire read lock on the second permit first; see similar technique in `startWrite`
    val sharedLock = ts.sharedLock
    if (sharedLock != null) {
      // We need a secondary permit, read one. Optimization: if we have on as last, do nothing
      val sps = mySecondaryPermits.get()
      val last = sps.lastOrNull()
      // If secondary lock does not protect this shared lock yet, get secondary lock
      // If here is any secondary permit, then additional read permit will do nothing but prevent
      // wil -> rl -> wl sequence which is (unfortunately) allowed and used now.
      if (last == null) {
        sps.add(acquireReadPermit(sharedLock))
        releaseSecondary = true
      }
    }

    when (ts.permit) {
      null -> {
        ts.acquire(acquireReadPermit(lock))
        release = true
      }
      is ReadPermit, is WritePermit, is WriteIntentPermit -> {}
    }

    // For diagnostic purposes register that we in read action, even if we use stronger lock
    myReadActionsInThread.set(myReadActionsInThread.get() + 1)

    val prevImplicitLock = ThreadingAssertions.isImplicitLockOnEDT()
    ThreadingAssertions.setImplicitLockOnEDT(false)
    try {
      fireReadActionStarted(clazz)
      val rv = runWithTemporaryThreadLocal(ts) { block.compute() }
      fireReadActionFinished(clazz)
      return rv
    }
    finally {
      ThreadingAssertions.setImplicitLockOnEDT(prevImplicitLock)

      myReadActionsInThread.set(myReadActionsInThread.get() - 1)
      if (release) {
        ts.release()
      }
      if (releaseSecondary) {
        mySecondaryPermits.get().removeLast().release()
      }
      myTopmostReadAction.set(currentReadState)
      fireAfterReadActionFinished(clazz)
    }
  }

  override fun tryRunReadAction(action: Runnable): Boolean {
    fireBeforeReadActionStart(action.javaClass)

    val currentReadState = myTopmostReadAction.get()
    myTopmostReadAction.set(true)

    val ts = getThreadState()
    var release = false
    var releaseSecondary = false

    // See similar technique in `startWrite`
    val sharedLock = ts.sharedLock
    if (sharedLock != null) {
      // We need a secondary permit, read one. Optimization: if we have on as last, do nothing
      val sps = mySecondaryPermits.get()
      val last = sps.lastOrNull()
      // If secondary lock does not protect this shared lock yet, get secondary lock
      // If here is any secondary permit, then additional read permit will do nothing but prevent
      // wil -> rl -> wl sequence which is (unfortunately) allowed and used now.
      if (last == null) {
        val p = tryGetReadPermit(sharedLock)
        if (p == null) {
          return false
        }
        sps.add(p)
        releaseSecondary = true
      }
    }

    when (ts.permit) {
      null -> {
        val p = tryGetReadPermit(lock)
        if (p == null) {
          return false
        }
        ts.acquire(p)
        release = true
      }
      is ReadPermit, is WritePermit, is WriteIntentPermit -> {}
    }

    // For diagnostic purposes register that we in read action, even if we use stronger lock
    myReadActionsInThread.set(myReadActionsInThread.get() + 1)

    val prevImplicitLock = ThreadingAssertions.isImplicitLockOnEDT()
    ThreadingAssertions.setImplicitLockOnEDT(false)
    try {
      fireReadActionStarted(action.javaClass)
      runWithTemporaryThreadLocal(ts) { action.run() }
      fireReadActionFinished(action.javaClass)
      return true
    }
    finally {
      ThreadingAssertions.setImplicitLockOnEDT(prevImplicitLock)
      myReadActionsInThread.set(myReadActionsInThread.get() - 1)
      if (release) {
        ts.release()
      }
      if (releaseSecondary) {
        mySecondaryPermits.get().removeLast().release()
      }

      myTopmostReadAction.set(currentReadState)
      fireAfterReadActionFinished(action.javaClass)
    }
  }

  override fun isReadLockedByThisThread(): Boolean {
    val ts = myState.get()
    return ts != null && ts.hasRead
  }

  @ApiStatus.Internal
  override fun setWriteActionListener(listener: WriteActionListener) {
    if (myWriteActionListener != null)
      error("WriteActionListener already registered")
    myWriteActionListener = listener
  }

  @ApiStatus.Internal
  override fun setWriteIntentReadActionListener(listener: WriteIntentReadActionListener) {
    if (myWriteIntentActionListener != null)
      error("WriteActionListener already registered")
    myWriteIntentActionListener = listener
  }

  @ApiStatus.Internal
  override fun removeWriteActionListener(listener: WriteActionListener) {
    if (myWriteActionListener != listener)
      error("WriteActionListener is not registered")
    myWriteActionListener = null
  }

  @ApiStatus.Internal
  override fun setLockAcquisitionListener(listener: LockAcquisitionListener) {
    if (myLockAcquisitionListener != null)
      error("WriteActionListener already registered")
    myLockAcquisitionListener = listener
  }

  @ApiStatus.Internal
  override fun setSuspendingWriteActionListener(listener: SuspendingWriteActionListener) {
    if (mySuspendingWriteActionListener != null)
      error("SuspendingWriteActionListener already registered")
    mySuspendingWriteActionListener = listener
  }

  @ApiStatus.Internal
  override fun removeLockAcquisitionListener(listener: LockAcquisitionListener) {
    if (myLockAcquisitionListener != listener)
      error("WriteActionListener is not registered")
    myLockAcquisitionListener = null
  }

  override fun <T> runWriteAction(clazz: Class<*>, action: () -> T): T {
    return runWriteAction(clazz, ThrowableComputable(action))
  }

  override fun runWriteAction(action: Runnable) = runWriteAction<Unit, Throwable>(action.javaClass) { action.run() }

  override fun <T> runWriteAction(computation: Computable<T>): T = runWriteAction<T, Throwable>(computation.javaClass) { computation.compute() }

  override fun <T, E : Throwable?> runWriteAction(computation: ThrowableComputable<T, E>): T = runWriteAction(computation.javaClass, computation)

  private fun <T, E : Throwable?> runWriteAction(clazz: Class<*>, block: ThrowableComputable<T, E>): T {
    val ts = getThreadState()
    val releases = startWrite(ts, clazz)
    val prevImplicitLock = ThreadingAssertions.isImplicitLockOnEDT()
    return try {
      ThreadingAssertions.setImplicitLockOnEDT(false)
      runWithTemporaryThreadLocal(ts) { block.compute() }
    }
    finally {
      endWrite(ts, clazz, releases)
      ThreadingAssertions.setImplicitLockOnEDT(prevImplicitLock)
    }
  }

  private fun startWrite(ts: ThreadState, clazz: Class<*>): Triple<Boolean, Boolean, Boolean> {
    // Read permit is incompatible
    check(!ts.hasRead) { "WriteAction can not be called from ReadAction" }

    // Check that write action is not disabled
    // NB: It is before all cancellations will be run via fireBeforeWriteActionStart
    // It is change for old behavior, when ProgressUtilService checked this AFTER all cancellations.
    if (!useBackgroundWriteAction && myNoWriteActionCounter.get() > 0) {
      throwCannotWriteException()
    }

    myWriteActionPending.incrementAndGet()
    if (myWriteActionsStack.isEmpty()) {
      fireBeforeWriteActionStart(ts, clazz)
    }

    var release = false
    var releaseSecondary = false
    val currentReadState = myTopmostReadAction.get()
    myTopmostReadAction.set(false)

    val sharedLock = ts.sharedLock
    // If the shared lock is present, then the primary lock is at least in WIL state;
    // The current process of interaction with the primary lock involves reading its mutable field for permit.
    // We must establish mutual exclusion with other parties that attempt to read this field
    // before proceeding with the operations on the primary lock.
    if (sharedLock != null) {
      // We need a secondary permit, read one. Optimization: if we have on as last, do nothing
      val sps = mySecondaryPermits.get()
      val last = sps.lastOrNull()
      when (last) {
        null -> {
          sps.add(processWriteLockAcquisition { getWritePermit(sharedLock) })
          releaseSecondary = true
        }
        is WriteIntentPermit -> {
          sps.add(processWriteLockAcquisition { runSuspend { last.acquireWritePermit() } })
          releaseSecondary = true
        }
        is ReadPermit -> {
          // Rollback pending
          myWriteActionPending.decrementAndGet()
          error("WriteAction can not be called from ReadAction")
        }
        is WritePermit -> {}
      }
    }

    when (ts.permit) {
      null -> {
        ts.acquire(processWriteLockAcquisition { getWritePermit(ts) })
        release = true
      }
      // Read permit is impossible here, as it is first check before all "pendings"
      is ReadPermit -> {}
      is WriteIntentPermit -> {
        // Upgrade main permit
        ts.acquire(processWriteLockAcquisition { getWritePermit(ts) })
        release = true
        checkWriteFromRead("Write", "Write Intent")
      }
      is WritePermit -> {
        checkWriteFromRead("Write", "Write")
      }
    }

    myWriteAcquired = Thread.currentThread()
    myWriteActionPending.decrementAndGet()

    myWriteActionsStack.push(clazz)
    fireWriteActionStarted(ts, clazz)

    return Triple(release, releaseSecondary, currentReadState)
  }

  private fun endWrite(ts: ThreadState, clazz: Class<*>, releases: Triple<Boolean, Boolean, Boolean>) {
    fireWriteActionFinished(ts, clazz)
    myWriteActionsStack.pop()
    if (releases.first) {
      ts.release()
      myWriteAcquired = null
    }
    if (releases.second) {
      mySecondaryPermits.get().removeLast().release()
    }
    myTopmostReadAction.set(releases.third)
    if (releases.first) {
      fireAfterWriteActionFinished(ts, clazz)
    }
  }

  override fun executeSuspendingWriteAction(action: () -> Unit) {
    ThreadingAssertions.assertWriteIntentReadAccess()
    val ts = getThreadState()
    if (ts.hasWriteIntent) {
      action()
      return
    }

    // We have write access
    val prevBase = myWriteStackBase
    myWriteStackBase = myWriteActionsStack.size
    myWriteAcquired = null
    ts.release()
    try {
      runWithTemporaryThreadLocal(ts) { action() }
    }
    finally {
      mySuspendingWriteActionListener?.beforeWriteLockReacquired()
      ts.acquire(getWritePermit(ts))
      myWriteAcquired = Thread.currentThread()
      myWriteStackBase = prevBase
    }
  }

  override fun isWriteActionInProgress(): Boolean = myWriteAcquired != null

  override fun isWriteActionPending(): Boolean = myWriteActionPending.get() > 0

  override fun isWriteAccessAllowed(): Boolean = myWriteAcquired == Thread.currentThread()

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

  @Deprecated("Use `runReadAction` instead")
  override fun acquireReadActionLock(): AccessToken {
    PluginException.reportDeprecatedUsage("ThreadingSupport.acquireReadActionLock", "Use `runReadAction()` instead")
    val ts = getThreadState()
    if (ts.hasWrite) {
      throw IllegalStateException("Write Action can not request Read Access Token")
    }
    if (ts.hasRead || ts.hasWriteIntent) {
      return AccessToken.EMPTY_ACCESS_TOKEN
    }
    ts.acquire(getReadPermit(lock))
    return ReadAccessToken()
  }

  @Deprecated("Use `runWriteAction`, `WriteAction.run`, or `WriteAction.compute` instead")
  override fun acquireWriteActionLock(marker: Class<*>): AccessToken {
    PluginException.reportDeprecatedUsage("ThreadingSupport.acquireWriteActionLock", "Use `runWriteAction()` instead")
    return WriteAccessToken(marker)
  }

  override fun prohibitWriteActionsInside(): AccessToken {
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

    myImpatientReader.set(true)
    try {
      runnable.run()
    }
    finally {
      myImpatientReader.set(false)
    }
  }

  override fun isInImpatientReader(): Boolean = myImpatientReader.get()

  override fun isInsideUnlockedWriteIntentLock(): Boolean {
    if (isLockStoredInContext) {
      return false
    }
    return getThreadState().writeIntentReleased
  }

  private fun processWriteLockAcquisition(acquisitor: () -> WritePermit): WritePermit {
    myLockAcquisitionListener?.beforeWriteLockAcquired()
    try {
      return acquisitor()
    }
    finally {
      myLockAcquisitionListener?.afterWriteLockAcquired()
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

  private fun fireBeforeWriteIntentReadActionStart(clazz: Class<*>) {
    myWriteIntentActionListener?.beforeWriteIntentReadActionStart(clazz)
  }

  private fun fireWriteIntentActionStarted(clazz: Class<*>) {
    myWriteIntentActionListener?.writeIntentReadActionStarted(clazz)
  }

  private fun fireWriteIntentActionFinished(clazz: Class<*>) {
    myWriteIntentActionListener?.writeIntentReadActionFinished(clazz)
  }

  private fun afterWriteIntentReadActionFinished(clazz: Class<*>) {
    myWriteIntentActionListener?.afterWriteIntentReadActionFinished(clazz)
  }

  private fun getWriteIntentPermit(lock: RWMutexIdea): WriteIntentPermit {
    return runSuspend {
      lock.acquireWriteIntentPermit()
    }
  }

  private fun getWriteIntentPermit(): WriteIntentPermit {
    return runSuspend {
      lock.acquireWriteIntentPermit()
    }
  }

  private fun getWritePermit(lock: RWMutexIdea): WritePermit {
    return runSuspend {
      lock.acquireWritePermit()
    }
  }

  private fun getWritePermit(state: ThreadState): WritePermit {
    return when (state.permit) {
      null -> getWritePermit(lock)
      is WriteIntentPermit -> runSuspend { (state.permit as WriteIntentPermit).acquireWritePermit()  }
      else -> error("Can not acquire write permit when hold ${state.permit}")
    }
  }

  private fun getReadPermit(lock: RWMutexIdea): ReadPermit {
    return runSuspend {
      lock.acquireReadPermit(false)
    }
  }

  private fun tryGetReadPermit(lock: RWMutexIdea): ReadPermit? {
    return lock.tryAcquireReadPermit()
  }

  @Deprecated("")
  private class ReadAccessToken : AccessToken() {
    private val myPermit = run {
      fireBeforeReadActionStart(javaClass)
      val p = getReadPermit(lock)
      fireReadActionStarted(javaClass)
      p
    }

    override fun finish() {
      fireReadActionFinished(javaClass)
      myPermit.release()
      fireAfterReadActionFinished(javaClass)
    }
  }

  @Deprecated("")
  private class WriteAccessToken(private val clazz: Class<*>) : AccessToken() {
    val ts = getThreadState()
    val releases = startWrite(ts, clazz)

    init {
      markThreadNameInStackTrace()
    }

    override fun finish() {
      try {
        endWrite(ts, clazz, releases)
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

  private fun checkWriteFromRead(whatIsCalled: String, permitUsed: String) {
    if (!reportInvalidActionChains) {
      return
    }
    if (myReadActionsInThread.get() > 0) {
      val stackBottom = if (permitUsed == "Write") "Write" else "WriteIntentRead"
      logger.warn("${whatIsCalled} Action is called from Read Action with ${permitUsed} lock.\n" +
                  "Looks like there is ${stackBottom}Action -> ReadAction -> ${whatIsCalled.replace(" ", "")}Action call chain." +
                  "It is not error technically, as Read Action uses more strong lock, but looks like logical error.", Throwable())
    }
  }

  private fun throwCannotWriteException() {
    throw java.lang.IllegalStateException("Write actions are prohibited")
  }
}
