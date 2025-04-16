// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.concurrency.currentThreadContext
import com.intellij.core.rwmutex.*
import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.util.coroutines.runSuspend
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ReflectionUtil
import com.intellij.util.containers.Stack
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
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
  private var myWriteLockReacquisitionListener: WriteLockReacquisitionListener? = null
  private var myLegacyProgressIndicatorProvider: LegacyProgressIndicatorProvider? = null

  private val myWriteActionsStack = Stack<Class<*>>()
  private var myWriteStackBase = 0
  private val myWriteActionPending = AtomicInteger(0)
  private var myNoWriteActionCounter = AtomicInteger()

  private val myState = ThreadLocal.withInitial { ThreadState() }
  // We approximate "on stack" permits with "thread local" permits for shared main lock
  private val mySecondaryPermits = ThreadLocal.withInitial { ArrayList<Permit>() }
  private val myReadActionsInThread = ThreadLocal.withInitial { 0 }
  private val myLockingProhibited: ThreadLocal<Pair<Boolean, String>?> = ThreadLocal.withInitial { null }

  // todo: reimplement with listeners in IJPL-177760
  private val myTopmostReadAction = ThreadLocal.withInitial { false }

  @Volatile
  private var myWriteAcquired: Thread? = null

  @Volatile
  private var myWriteIntentAcquired: Thread? = null

  override fun getPermitAsContextElement(baseContext: CoroutineContext, shared: Boolean): Pair<CoroutineContext, AccessToken> {
    if (!isLockStoredInContext) {
      return EmptyCoroutineContext to AccessToken.EMPTY_ACCESS_TOKEN
    }

    val element = baseContext[LockStateContextElement]
    if (element?.threadState?.permit != null) {
      if (shared) {
        element.threadState.fork()
      }
      return element to object : AccessToken() {
        override fun finish() {
          if (shared) {
            element.threadState.join()
          }
        }
      }
    }

    val ts = myState.get()
    if (ts.permit != null) {
      if (shared) {
        ts.fork()
      }
      return LockStateContextElement(ts) to object : AccessToken() {
        override fun finish() {
          if (shared) {
            ts.join()
          }
        }
      }
    }

    return EmptyCoroutineContext to AccessToken.EMPTY_ACCESS_TOKEN
  }

  override fun isParallelizedReadAction(context: CoroutineContext): Boolean = isLockStoredInContext && context[LockStateContextElement] != null

  override fun isInTopmostReadAction(): Boolean {
    // once a read action was requested
    return myTopmostReadAction.get()
  }

  override fun <T> relaxPreventiveLockingActions(action: () -> T): T {
    return action()
  }

  override fun getLockingProhibitedAdvice(): String? {
    return myLockingProhibited.get()?.second
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

  override fun <T> runPreventiveWriteIntentReadAction(computation: () -> T): T {
    return doRunWriteIntentReadAction(computation)
  }

  override fun <T> runWriteIntentReadAction(computation: () -> T): T {
    handleLockAccess("write-intent lock")
    return doRunWriteIntentReadAction(computation)
  }

  private fun <T> doRunWriteIntentReadAction(computation: () -> T): T {
    val listener = myWriteIntentActionListener
    fireBeforeWriteIntentReadActionStart(listener, computation.javaClass)
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

    try {
      fireWriteIntentActionStarted(listener, computation.javaClass)
      return runWithTemporaryThreadLocal(ts) { computation() }
    }
    finally {
      fireWriteIntentActionFinished(listener, computation.javaClass)
      if (release) {
        ts.release()
        myWriteIntentAcquired = null
      }
      if (releaseSecondary) {
        mySecondaryPermits.get().removeLast().release()
      }
      myTopmostReadAction.set(currentReadState)
      afterWriteIntentReadActionFinished(listener, computation.javaClass)
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

  override fun <T> runUnlockingIntendedWrite(action: () -> T): T {
    if (isLockStoredInContext) {
      return action()
    }

    val ts = getThreadState()
    if (!ts.hasWriteIntent) {
      try {
        ts.writeIntentReleased = true
        return action()
      }
      finally {
        ts.writeIntentReleased = false
      }
    }

    ts.writeIntentReleased = true
    ts.release()
    try {
      return action()
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

  private fun acquireReadPermit(lock: RWMutexIdea): Permit {
    var permit = tryGetReadPermit(lock)
    if (permit != null) {
      return permit
    }

    myReadActionListener?.fastPathAcquisitionFailed()

    // Check for cancellation
    val indicator = myLegacyProgressIndicatorProvider?.obtainProgressIndicator()
    // Nothing to check or cannot be canceled
    if (indicator == null || Cancellation.isInNonCancelableSection()) {
      return getReadPermit(lock)
    }

    // Spin & sleep with checking for cancellation
    var iter = 0
    do {
      indicator.checkCanceled()
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

  private fun handleLockAccess(culprit: String) {
    val lockProhibition = myLockingProhibited.get()
    if (lockProhibition != null) {
      val exception = ThreadingSupport.LockAccessDisallowed("Attempt to take $culprit was prevented\n${lockProhibition.second}")
      if (lockProhibition.first) {
        logger.error(exception)
      }
      else {
        throw exception
      }
    }
  }

  override fun <T> runReadAction(clazz: Class<*>, action: () -> T): T {
    handleLockAccess("read lock")

    val listener = myReadActionListener
    fireBeforeReadActionStart(listener, clazz)

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

    try {
      fireReadActionStarted(listener, clazz)
      val rv = runWithTemporaryThreadLocal(ts) { action() }
      return rv
    }
    finally {
      fireReadActionFinished(listener, clazz)

      myReadActionsInThread.set(myReadActionsInThread.get() - 1)
      if (release) {
        ts.release()
      }
      if (releaseSecondary) {
        mySecondaryPermits.get().removeLast().release()
      }
      myTopmostReadAction.set(currentReadState)
      fireAfterReadActionFinished(listener, clazz)
    }
  }

  override fun tryRunReadAction(action: Runnable): Boolean {
    handleLockAccess("fail-fast read lock")

    val listener = myReadActionListener
    fireBeforeReadActionStart(listener, action.javaClass)

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

    try {
      fireReadActionStarted(listener, action.javaClass)
      runWithTemporaryThreadLocal(ts) { action.run() }
      return true
    }
    finally {
      fireReadActionFinished(listener, action.javaClass)

      myReadActionsInThread.set(myReadActionsInThread.get() - 1)
      if (release) {
        ts.release()
      }
      if (releaseSecondary) {
        mySecondaryPermits.get().removeLast().release()
      }

      myTopmostReadAction.set(currentReadState)
      fireAfterReadActionFinished(listener, action.javaClass)
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
      error("WriteIntentReadActionListener already registered")
    myWriteIntentActionListener = listener
  }

  override fun removeWriteIntentReadActionListener(listener: WriteIntentReadActionListener) {
    if (myWriteIntentActionListener != listener)
      error("WriteActionListener is not registered")
    myWriteIntentActionListener = null
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
      error("LockAcquisitionListener already registered")
    myLockAcquisitionListener = listener
  }

  override fun setLockAcquisitionInterceptor(delayMillis: Long, consumer: (() -> Boolean) -> Unit) {
    return
  }

  @ApiStatus.Internal
  override fun setWriteLockReacquisitionListener(listener: WriteLockReacquisitionListener) {
    if (myWriteLockReacquisitionListener != null)
      error("WriteLockReacquisitionListener already registered")
    myWriteLockReacquisitionListener = listener
  }

  @ApiStatus.Internal
  override fun removeWriteLockReacquisitionListener(listener: WriteLockReacquisitionListener) {
    if (myWriteLockReacquisitionListener != listener)
      error("WriteLockReacquisitionListener is not registered")
    myWriteLockReacquisitionListener = null
  }

  @ApiStatus.Internal
  override fun setLegacyIndicatorProvider(provider: LegacyProgressIndicatorProvider) {
    if (myLegacyProgressIndicatorProvider != null)
      error("LegacyProgressIndicatorProvider already registered")
    myLegacyProgressIndicatorProvider = provider
  }

  @ApiStatus.Internal
  override fun removeLegacyIndicatorProvider(provider: LegacyProgressIndicatorProvider) {
    if (myLegacyProgressIndicatorProvider != provider)
      error("LegacyProgressIndicatorProvider is not registered")
    myLegacyProgressIndicatorProvider = null
  }

  @ApiStatus.Internal
  override fun removeLockAcquisitionListener(listener: LockAcquisitionListener) {
    if (myLockAcquisitionListener != listener)
      error("LockAcquisitionListener is not registered")
    myLockAcquisitionListener = null
  }

  override fun <T> runWriteAction(clazz: Class<*>, action: () -> T): T {
    val ts = getThreadState()
    val releases = startWrite(ts, clazz)
    return try {
      runWithTemporaryThreadLocal(ts) { action() }
    }
    finally {
      endWrite(ts, clazz, releases)
    }
  }

  private data class WriteListenerInitResult(
    val releasePrime: Boolean,
    val releaseSecondary: Boolean,
    val currentReadState: Boolean,
    val listener: WriteActionListener?,
  )

  private fun startWrite(ts: ThreadState, clazz: Class<*>): WriteListenerInitResult {
    val listener = myWriteActionListener
    // Read permit is incompatible
    check(!ts.hasRead) { "WriteAction can not be called from ReadAction" }

    // Check that write action is not disabled
    // NB: It is before all cancellations will be run via fireBeforeWriteActionStart
    // It is change for old behavior, when ProgressUtilService checked this AFTER all cancellations.
    if (!useBackgroundWriteAction && myNoWriteActionCounter.get() > 0) {
      throwCannotWriteException()
    }

    handleLockAccess("write lock")

    myWriteActionPending.incrementAndGet()
    if (myWriteActionsStack.isEmpty()) {
      fireBeforeWriteActionStart(listener, ts, clazz)
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
    fireWriteActionStarted(listener, ts, clazz)

    return WriteListenerInitResult(release, releaseSecondary, currentReadState, listener)
  }

  private fun endWrite(ts: ThreadState, clazz: Class<*>, initResult: WriteListenerInitResult) {
    fireWriteActionFinished(initResult.listener, ts, clazz)
    myWriteActionsStack.pop()
    if (initResult.releasePrime) {
      ts.release()
      myWriteAcquired = null
    }
    if (initResult.releaseSecondary) {
      mySecondaryPermits.get().removeLast().release()
    }
    myTopmostReadAction.set(initResult.currentReadState)
    if (initResult.releasePrime) {
      fireAfterWriteActionFinished(initResult.listener, ts, clazz)
    }
  }

  override fun executeSuspendingWriteAction(action: () -> Unit) {
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
      myWriteLockReacquisitionListener?.beforeWriteLockReacquired()
      ts.acquire(getWritePermit(ts))
      myWriteAcquired = Thread.currentThread()
      myWriteStackBase = prevBase
    }
  }

  override fun isWriteActionInProgress(): Boolean = myWriteAcquired != null

  override fun isWriteActionPending(): Boolean = myWriteActionPending.get() > 0

  override fun isWriteAccessAllowed(): Boolean = myWriteAcquired == Thread.currentThread()

  override fun hasWriteAction(actionClass: Class<*>): Boolean {
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
    logger.error("`ThreadingSupport.acquireReadActionLock` is deprecated and going to be removed soon. Use `runReadAction()` instead")
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
    logger.error("`ThreadingSupport.acquireWriteActionLock` is deprecated and going to be removed soon. Use `runWriteAction()` instead")
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

  override fun prohibitTakingLocksInsideAndRun(action: Runnable, failSoftly: Boolean, advice: String) {
    val currentValue = myLockingProhibited.get()
    myLockingProhibited.set(failSoftly to advice)
    try {
      action.run()
    }
    finally {
      myLockingProhibited.set(currentValue)
    }
  }

  override fun allowTakingLocksInsideAndRun(action: Runnable) {
    val currentValue = myLockingProhibited.get()
    myLockingProhibited.set(null)
    try {
      action.run()
    }
    finally {
      myLockingProhibited.set(currentValue)
    }
  }

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

  private fun fireBeforeReadActionStart(listener: ReadActionListener?, clazz: Class<*>) {
    try {
      listener?.beforeReadActionStart(clazz)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireReadActionStarted(listener: ReadActionListener?, clazz: Class<*>) {
    try {
      listener?.readActionStarted(clazz)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireReadActionFinished(listener: ReadActionListener?, clazz: Class<*>) {
    try {
      listener?.readActionFinished(clazz)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireAfterReadActionFinished(listener: ReadActionListener?, clazz: Class<*>) {
    try {
      listener?.afterReadActionFinished(clazz)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireBeforeWriteActionStart(listener: WriteActionListener?, ts: ThreadState, clazz: Class<*>) {
    ts.inListener = true
    try {
      listener?.beforeWriteActionStart(clazz)
    }
    catch (_: Throwable) {
    }
    finally {
      ts.inListener = false
    }
  }

  private fun fireWriteActionStarted(listener: WriteActionListener?, ts: ThreadState, clazz: Class<*>) {
    ts.inListener = true
    try {
      listener?.writeActionStarted(clazz)
    }
    catch (_: Throwable) {
    }
    finally {
      ts.inListener = false
    }
  }

  private fun fireWriteActionFinished(listener: WriteActionListener?, ts: ThreadState, clazz: Class<*>) {
    ts.inListener = true
    try {
      listener?.writeActionFinished(clazz)
    }
    catch (_: Throwable) {
    }
    finally {
      ts.inListener = false
    }
  }

  private fun fireAfterWriteActionFinished(listener: WriteActionListener?, ts: ThreadState, clazz: Class<*>) {
    ts.inListener = true
    try {
      listener?.afterWriteActionFinished(clazz)
    }
    catch (_: Throwable) {
    }
    finally {
      ts.inListener = false
    }
  }

  private fun fireBeforeWriteIntentReadActionStart(listener: WriteIntentReadActionListener?, clazz: Class<*>) {
    try {
      listener?.beforeWriteIntentReadActionStart(clazz)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireWriteIntentActionStarted(listener: WriteIntentReadActionListener?, clazz: Class<*>) {
    try {
      listener?.writeIntentReadActionStarted(clazz)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireWriteIntentActionFinished(listener: WriteIntentReadActionListener?, clazz: Class<*>) {
    try {
      listener?.writeIntentReadActionFinished(clazz)
    }
    catch (_: Throwable) {
    }
  }

  private fun afterWriteIntentReadActionFinished(listener: WriteIntentReadActionListener?, clazz: Class<*>) {
    try {
      listener?.afterWriteIntentReadActionFinished(clazz)
    }
    catch (_: Throwable) {
    }
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
    return acquireReadLockWithCompensation {
      lock.acquireReadPermit(false)
    }
  }

  private fun tryGetReadPermit(lock: RWMutexIdea): ReadPermit? {
    return lock.tryAcquireReadPermit()
  }

  @Deprecated("")
  private class ReadAccessToken : AccessToken() {
    private val capturedListener = myReadActionListener
    private val myPermit = run {
      fireBeforeReadActionStart(capturedListener, javaClass)
      val p = getReadPermit(lock)
      fireReadActionStarted(capturedListener, javaClass)
      p
    }

    override fun finish() {
      fireReadActionFinished(capturedListener, javaClass)
      myPermit.release()
      fireAfterReadActionFinished(capturedListener, javaClass)
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

  override fun runWhenWriteActionIsCompleted(action: () -> Unit) {
    return SwingUtilities.invokeLater { action() }
  }
}
