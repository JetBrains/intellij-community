// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.concurrency.currentThreadContext
import com.intellij.core.rwmutex.*
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.ComputationState.Companion.thisLevelPermit
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.util.coroutines.internal.runSuspend
import com.intellij.util.ReflectionUtil
import com.intellij.util.containers.Stack
import com.jetbrains.rd.util.forEachReversed
import kotlinx.coroutines.Runnable
import org.jetbrains.annotations.ApiStatus
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The locking state that is shared by all computations belonging to the same level.
 */
private class ComputationState(
  /**
   * A set of write-intent permits of **previous** locks (from `0` to `n - 1` included)
   * We need them for obtaining write permit on [thisLevelLock] while cancelling lower-level actions.
   */
  private val lowerLevelPermits: Array<WriteIntentPermit>,

  /**
   * The lock of level `n`. This lock is intended to be shared between all computations belonging to the same level.
   */
  private val thisLevelLock: RWMutexIdea,

  /**
   * An artifact of parallelizing the read lock. The code parallelized from the read lock
   * has no way of upgrading further, so it is safe to share read access with whole remaining computation tree.
   */
  private val parallelizedReadPermit: ReadPermit?,
) {

  companion object {
    /**
     * Current permit that is given to a thread.
     * We need to have this in a thread local, because thread context often gets reset, but we still need to proceed with locking actions.
     * Example:
     * ```
     * WriteIntentReadAction.run {
     *   dispatchAllInvocationEvents { // <- this method starts processing event queue, hence we reset the context there
     *     WriteIntentReadAction.run { // since the context is reset, we need to obtain the higher-level permit;
     *                                 // otherwise there would be a deadlock on a new attempt to acquire a write-intent permit
     *     }
     *   }
     * }
     * ```
     */
    private val thisLevelPermit: ThreadLocal<Permit> = ThreadLocal.withInitial { null }

    /**
     * This field is set after starting a write action.
     *
     * It is needed to support [NestedLocksThreadingSupport.executeSuspendingWriteAction].
     * During the execution of suspending WA, we release and re-acquire write permits.
     *
     * Invariants: these fields must be accessed only from EDT, so all usages are intended to be serialized.
     * We don't add excessive memory barriers here for the sake of performance.
     */
    private var publishedPermits: ExposedWritePermitData? = null
  }

  fun level(): Int {
    return lowerLevelPermits.size
  }

  fun isParallelizedReadPermit(): Boolean {
    return parallelizedReadPermit != null
  }

  fun getThisThreadPermit(): Permit? {
    if (parallelizedReadPermit != null) {
      return parallelizedReadPermit
    }
    else {
      return thisLevelPermit.get()
    }
  }

  /**
   * Obtains a write permit if nothing is being held by this thread
   */
  fun acquireWritePermit() {
    // we need to start with the write-intent permit here, because write permit may be released at some point
    // (most likely by `suspendingWriteAction`), and we must not allow other write action to start
    val writeIntentPermit = runSuspend {
      thisLevelLock.acquireWriteIntentPermit()
    }
    return upgradeWritePermit(writeIntentPermit, null)
  }

  /**
   * Obtains a write permit if current thread holds a write-intent permit
   */
  fun upgradeWritePermit(permit: WriteIntentPermit) {
    return upgradeWritePermit(permit, permit)
  }

  private fun upgradeWritePermit(permit: WriteIntentPermit, original: WriteIntentPermit?) {
    val finalPermit = runSuspend {
      permit.acquireWritePermit()
    }

    // we need to acquire writes on the whole stack of lower-level write-intent permits,
    // since we want to cancel all lower-level running read actions
    val writePermits = Array(level()) {
      runSuspend {
        lowerLevelPermits[it].acquireWritePermit()
      }
    }
    check(publishedPermits == null) { "'upgradeWritePermit' must not be preceded by another 'upgradeWritePermit'" }
    // exposing write permit data for possible suspending WA
    publishedPermits = ExposedWritePermitData(lowerLevelPermits, writePermits, finalPermit, permit, original)
    thisLevelPermit.set(finalPermit)
  }

  fun releaseWritePermit() {
    val currentPublishedPermits = checkNotNull(publishedPermits) { "'releaseWrite' must be called only after 'acquireWritePermit' or 'upgradeWritePermit'" }
    thisLevelPermit.set(currentPublishedPermits.oldPermit)
    publishedPermits = null
    val writePermits = currentPublishedPermits.writePermitStack
    // forEachReversed is the most performant loop in kotlin, it uses indexes instead of iterators
    writePermits.forEachReversed {
      it.release()
    }
    currentPublishedPermits.finalWritePermit.release()
    if (currentPublishedPermits.oldPermit == null) {
      // it means that we were asked to acquire write permit without existing write-intent
      currentPublishedPermits.originalWriteIntentPermit.release()
    }
  }

  /**
   * Normally, management of [thisLevelPermit] should happen in acquire-release functions.
   * If something uses [hack_setThisLevelPermit], then it is a hack
   */
  @Suppress("FunctionName") // this function is for hackers
  fun hack_setThisLevelPermit(permit: Permit?) {
    thisLevelPermit.set(permit)
  }

  @Suppress("FunctionName") // this function is for hackers
  fun hack_getPublishedWriteData(): ExposedWritePermitData? {
    return publishedPermits
  }

  @Suppress("FunctionName") // this function is for hackers
  fun hack_setPublishedPermitData(newData: ExposedWritePermitData?) {
    publishedPermits = newData
  }

  /**
   * Obtains a write-intent permit if the current thread does not hold anything
   */
  fun acquireWriteIntentPermit(): WriteIntentPermit {
    val permit = runSuspend {
      thisLevelLock.acquireWriteIntentPermit()
    }
    thisLevelPermit.set(permit)
    return permit
  }

  /**
   * Releases a write-intent permit acquired in [acquireWriteIntentPermit].
   * Must be preceded by [acquireWriteIntentPermit] on this thread.
   */
  fun releaseWriteIntentPermit(writeIntentPermit: WriteIntentPermit) {
    thisLevelPermit.set(null)
    return writeIntentPermit.release()
  }

  /**
   * Obtains a write-intent permit if the current thread does not hold anything
   */
  fun acquireReadPermit(): ReadPermit {
    val permit = acquireReadLockWithCompensation {
      thisLevelLock.acquireReadPermit(false)
    }
    thisLevelPermit.set(permit)
    return permit
  }

  /**
   * same as [acquireReadPermit], but returns `null` if acquisition failed
   */
  fun tryAcquireReadPermit(): ReadPermit? {
    val permit = runSuspend {
      thisLevelLock.tryAcquireReadPermit()
    }
    if (permit != null) {
      thisLevelPermit.set(permit)
    }
    return permit
  }

  /**
   * Releases a read permit acquired in [acquireReadPermit] or [tryAcquireReadPermit].
   */
  fun releaseReadPermit(readPermit: ReadPermit) {
    check(thisLevelPermit.get() === readPermit) { "Attempt to release of a read permit that was not acquired in the current thread" }
    thisLevelPermit.set(null)
    return readPermit.release()
  }

  /**
   * Starts parallelization of the write-intent permit.
   * We introduce a new level of locking here
   */
  fun parallelizeWriteIntent(thisLevelPermit: WriteIntentPermit): Pair<ComputationState, AccessToken> {
    val existingPermit = Companion.thisLevelPermit.get()
    check(existingPermit == thisLevelPermit) { "Internal error: attempt to parallelize a foreign write-intent permit" }
    Companion.thisLevelPermit.set(null)
    return ComputationState(lowerLevelPermits + thisLevelPermit, RWMutexIdea(), null) to object : AccessToken() {
      override fun finish() {
        Companion.thisLevelPermit.set(existingPermit)
      }
    }
  }

  /**
   * Starts parallelization of the write-intent permit.
   * We simply grant the read access to all computations.
   */
  fun parallelizeRead(thisLevelReadPermit: ReadPermit): Pair<ComputationState, AccessToken> {
    return ComputationState(lowerLevelPermits, thisLevelLock, thisLevelReadPermit) to AccessToken.EMPTY_ACCESS_TOKEN
  }

  override fun toString(): String {
    return "ComputationState(level=${level()},thisLevelLock=$thisLevelLock,isParallelizedRead=${parallelizedReadPermit != null})"
  }
}

private class ComputationStateContextElement(val computationState: ComputationState) : CoroutineContext.Element {
  override val key: CoroutineContext.Key<*>
    get() = ComputationStateContextElement

  companion object : CoroutineContext.Key<ComputationStateContextElement>

  override fun toString(): String {
    return "$computationState"
  }
}


@Suppress("ArrayInDataClass")
private data class ExposedWritePermitData(
  /**
   * The stack of write-intent permits that got upgraded to write permits
   */
  val writeIntentStack: Array<WriteIntentPermit>,
  /**
   * Obtained from [writeIntentStack]
   */
  val writePermitStack: Array<WritePermit>,
  /**
   * The highest-level write permit
   */
  val finalWritePermit: WritePermit,
  /**
   * The write-intent permit that was used to obtain [finalWritePermit]
   */
  val originalWriteIntentPermit: WriteIntentPermit,
  /**
   * The original permit that was existing on the current thread
   */
  val oldPermit: WriteIntentPermit?,
)

/**
 * This class implements a technique that we call "nested locks" (a.k.a. "n-locks", where "n" stands for "natural number").
 *
 * The purpose of n-locks is to support the scenario of "lock parallelization".
 * Consider the following code:
 * ```kotlin
 * withContext(Dispatchers.EDT) {
 *   writeIntentReadAction { // 1
 *     runWithModalProgressBlocking {
 *       launch(Dispatchers.Default) {
 *         backgroundWriteAction { // 2
 *         }
 *       }
 *     }
 *   }
 * }
 * ```
 * If we have one lock that acquires a write-intent permit (1), then, in order to perform the background WA (2), we would need to pass
 * the write-intent permit to the background thread. However, we must also maintain mutual exclusion for the following cases:
 * ```kotlin
 * runWithModalProgressBlocking {
 *   launch(Dispatchers.Default) {
 *      backgroundWriteAction {}
 *   }
 *   launch(Dispatchers.Default) {
 *     backgroundWriteAction {}
 *   }
 *   launch(Dispatchers.Default) {
 *     readAction {}
 *   }
 * }
 * ```
 *
 * This scenario can be handled by the introduction of another lock, that works within modal progress.
 * Abstracting a little, here we deal not with just a modal progress, but with an attempt to parallelize inside a taken lock.
 * This is what we mean by "lock parallelization".
 *
 * Another obstacle is that this situation can be scaled:
 * ```kotlin
 * runWithModalProgressBlocking {
 *   withContext(Dispatchers.EDT) {
 *     runWithModalProgressBlocking {
 *     }
 *   }
 * }
 * ```
 * It means that we need to maintain several locks, one for each parallelization attempt.
 *
 * Each new layer of locks is denoted as "level", where level `0` stands for the global shared lock, and bigger levels correspond to new parallelization layers.
 *
 * There is also an alternative way to parallelize locks: since we know that it is safe to execute read actions in parallel, we can simply
 * grant read access to all computations when lock parallelization is requested:
 * ```
 * readAction {
 *   runBlockingCancellable {
 *     launch(Default) {
 *       readAction {} // succeeds regardless of pending write actions
 *     }
 *   }
 * }
 * ```
 * This read-parallelization would break the contracts in `writeIntentReadAction`, but it is safe to apply for read actions.
 * It actually helps to resolve certain kind of deadlocks.
 */
@ApiStatus.Internal
internal object NestedLocksThreadingSupport : ThreadingSupport {
  private val logger = Logger.getInstance(NestedLocksThreadingSupport::class.java)

  private const val SPIN_TO_WAIT_FOR_LOCK: Int = 100

  /**
   * The global lock that is a default choice when lock parallelization is absent
   */
  private val zeroLevelComputationState = ComputationState(emptyArray(), RWMutexIdea(), null)

  /**
   * A stack of computation states for the thread that is allowed to hold the Write-Intent lock
   * This variable is `null` on all threads except EDT.
   * It is needed because the thread context is often getting reset on EDT, but we still need to get the last relevant computation state.
   */
  private val statesOfWIThread: ThreadLocal<MutableList<ComputationState>?> = ThreadLocal.withInitial { null }

  private var myReadActionListener: ReadActionListener? = null
  private var myWriteActionListener: WriteActionListener? = null
  private var myWriteIntentActionListener: WriteIntentReadActionListener? = null
  private var myLockAcquisitionListener: LockAcquisitionListener? = null
  private var mySuspendingWriteActionListener: SuspendingWriteActionListener? = null
  private var myLegacyProgressIndicatorProvider: LegacyProgressIndicatorProvider? = null

  private val myWriteActionsStack = Stack<Class<*>>()
  private var myWriteStackBase = 0

  /**
   * We need to keep track of pending write actions on each level.
   * The underlying array rarely changes in its size, but reasonably frequently changes its values.
   * That's why we don't use COW list here: value updates may be frequent.
   * Also, we'd like to have some sort of random access, that's why we don't use any lock-free list.
   *
   * When we enlarge this array, we copy all underlying [AtomicInteger] by reference, so that everyone who reads old values of elements
   * will have the same data as if we managed to extend the array before all reads.
   */
  @Suppress("RemoveExplicitTypeArguments")
  private val myWriteActionPending = AtomicReference<Array<AtomicInteger>>(Array(1) { AtomicInteger(0) })
  private var myNoWriteActionCounter = AtomicInteger()

  private val myReadActionsInThread = ThreadLocal.withInitial { 0 }
  private val myLockingProhibited: ThreadLocal<Pair<Boolean, String>?> = ThreadLocal.withInitial { null }

  // todo: reimplement with listeners in IJPL-177760
  private val myTopmostReadAction = ThreadLocal.withInitial { false }

  @Volatile
  private var myWriteAcquired: Thread? = null

  override fun getPermitAsContextElement(baseContext: CoroutineContext, shared: Boolean): Pair<CoroutineContext, AccessToken> {
    if (!isLockStoredInContext) {
      return EmptyCoroutineContext to AccessToken.EMPTY_ACCESS_TOKEN
    }

    val currentComputationStateElement = baseContext[ComputationStateContextElement]
    // we suppose that the caller passes `baseContext` that is actually correct
    val currentComputationState = currentComputationStateElement?.computationState
                                  ?: statesOfWIThread.get()?.lastOrNull()
                                  ?: zeroLevelComputationState

    if (!shared) {
      return (currentComputationStateElement ?: ComputationStateContextElement(currentComputationState)) to AccessToken.EMPTY_ACCESS_TOKEN
    }

    // now we need to parallelize the existing permit
    val currentPermit = currentComputationState.getThisThreadPermit()
    when (currentPermit) {
      null -> {
        // this is equivalent to a pure `runBlockingCancellable` on a thread that does not hold locks
        // we can simply share the current lock

        // here we do elvis expression to save an allocation
        return (currentComputationStateElement ?: ComputationStateContextElement(currentComputationState)) to AccessToken.EMPTY_ACCESS_TOKEN
      }
      is ReadPermit -> {
        // This is equivalent to `runBlockingCancellable` under `readAction`.
        // We must ensure that the computation in `runBlockingCancellable` has read access;
        // Otherwise there can be deadlocks where `runBlockingCancellable` is canceled by a pending write which cannot start
        // because some computation inside `runBlockingCancellable` waits for read access in a blocking way
        val (newComputationState, cleanup) = currentComputationState.parallelizeRead(currentPermit)
        return ComputationStateContextElement(newComputationState) to cleanup
      }
      is WriteIntentPermit -> {
        // we are attempting to share a write-intent lock
        // it means that we are entering a new level of locking
        val (newComputationState, cleanup) = currentComputationState.parallelizeWriteIntent(currentPermit)
        statesOfWIThread.set(statesOfWIThread.get() ?: mutableListOf())
        statesOfWIThread.get()?.add(newComputationState)

        // we need to extend the array atomically
        // currently, there will never be any contenders: write-intent is allowed only on EDT, so this loop completes in one iteration.
        // but here we also insert a memory barrier by working with an atomic reference, so every other thread now sees
        // the updated value of an array.
        do {
          val currentPendingWaArray = myWriteActionPending.get()
          val newArray = currentPendingWaArray + AtomicInteger(0)
        }
        while (!myWriteActionPending.compareAndSet(currentPendingWaArray, newArray))

        return ComputationStateContextElement(newComputationState) to object : AccessToken() {
          override fun finish() {
            do {
              val currentPendingWaArray = myWriteActionPending.get()

              @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog") // the suggested method adds useless nullability
              val newArray = Arrays.copyOf(currentPendingWaArray, currentPendingWaArray.size - 1)
            }
            while (!myWriteActionPending.compareAndSet(currentPendingWaArray, newArray))
            statesOfWIThread.get()?.removeLast()
            cleanup.finish()
          }
        }
      }
      is WritePermit -> {
        // The parallelization of a write action is a very tricky topic.
        // It is difficult to get right, and we are not sure about the valid semantics yet.
        // Unfortunately, our old tests rely on this concept.
        // We forbid write lock parallelization in production (see `runBlockingCancellable`), and instead allow it only in tests
        val currentPermits = checkNotNull(currentComputationState.hack_getPublishedWriteData()) { "Parallelization of write permit must happen when write lock is acquired" }
        val currentWriteIntentPermit = currentPermits.originalWriteIntentPermit
        // the idea of write lock parallelization is that we release the **top-level** write permit,
        // capture read permit, and parallelize it.
        // so this is the parallelization of the second kind.
        currentPermit.release()
        val newPermit = currentComputationState.acquireReadPermit()
        val (newState, cleanup) = currentComputationState.parallelizeRead(newPermit)
        return ComputationStateContextElement(newState) to object : AccessToken() {
          override fun finish() {
            cleanup.finish()
            currentComputationState.releaseReadPermit(newPermit)
            // we need to reacquire the previously released write permit
            val newWritePermit = runSuspend {
              currentWriteIntentPermit.acquireWritePermit()
            }
            currentComputationState.hack_setThisLevelPermit(newWritePermit)
            currentComputationState.hack_setPublishedPermitData(currentPermits.copy(finalWritePermit = newWritePermit))
          }
        }
      }
    }
  }

  override fun isParallelizedReadAction(context: CoroutineContext): Boolean {
    return isLockStoredInContext && context[ComputationStateContextElement]?.computationState?.isParallelizedReadPermit() == true
  }

  /**
   * Needs for the following situations:
   * ```
   * WriteAction.run {
   *   ReadAction.run {
   *     runBlockingCancellable {
   *     }
   *   }
   * }
   * ```
   * yes, we have it in production, and we decided to permit this kind of parallelization for now. At least it is clear that users want to parallelize read permit.
   */
  override fun isInTopmostReadAction(): Boolean {
    // once a read action was requested
    return myTopmostReadAction.get()
  }

  private fun getComputationState(): ComputationState {
    // here we retrieve [statesOfWiThread] first in opposition to `getPermitAsContextElement`,
    // because current thread context may be leaked from some outer computation, and we are interested in precisely latest
    // state of WI thread
    return statesOfWIThread.get()?.lastOrNull()
           ?: currentThreadContext()[ComputationStateContextElement]?.computationState
           ?: zeroLevelComputationState
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

    val computationState = getComputationState()
    val currentPermit = computationState.getThisThreadPermit()
    var permitToRelease: WriteIntentPermit? = null
    when (currentPermit) {
      null -> {
        permitToRelease = computationState.acquireWriteIntentPermit()
      }
      is ReadPermit -> {
        error("WriteIntentReadAction can not be called from ReadAction")
      }
      is WriteIntentPermit, is WritePermit -> {}
    }

    try {
      fireWriteIntentActionStarted(listener, computation.javaClass)
      return computation()
    }
    finally {
      fireWriteIntentActionFinished(listener, computation.javaClass)
      if (permitToRelease != null) {
        computationState.releaseWriteIntentPermit(permitToRelease)
      }
      myTopmostReadAction.set(currentReadState)
      afterWriteIntentReadActionFinished(listener, computation.javaClass)
    }
  }

  override fun isWriteIntentLocked(): Boolean {
    val currentState = getComputationState()
    val currentPermit = currentState.getThisThreadPermit()
    return currentPermit is WriteIntentPermit || currentPermit is WritePermit
  }

  override fun isReadAccessAllowed(): Boolean {
    val currentState = getComputationState()
    val currentPermit = currentState.getThisThreadPermit()
    return currentPermit != null
  }

  override fun <T> runUnlockingIntendedWrite(action: () -> T): T {
    return action()
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

  private fun smartAcquireReadPermit(state: ComputationState): ReadPermit {
    var permit = state.tryAcquireReadPermit()
    if (permit != null) {
      return permit
    }

    myReadActionListener?.fastPathAcquisitionFailed()

    // Check for cancellation
    val indicator = myLegacyProgressIndicatorProvider?.obtainProgressIndicator()
    // Nothing to check or cannot be canceled
    if (indicator == null || Cancellation.isInNonCancelableSection()) {
      return state.acquireReadPermit()
    }

    // Spin & sleep with checking for cancellation
    var iter = 0
    do {
      indicator.checkCanceled()
      if (iter++ < SPIN_TO_WAIT_FOR_LOCK) {
        Thread.yield()
        permit = state.tryAcquireReadPermit()
      }
      else {
        permit = state.acquireReadPermit() // getReadPermitTimed (1) // millis
      }
    }
    while (permit == null)
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

    val computationState = getComputationState()
    val currentPermit = computationState.getThisThreadPermit()
    var readPermitToRelease: ReadPermit? = null

    when (currentPermit) {
      is ReadPermit, is WritePermit, is WriteIntentPermit -> {}
      null -> {
        readPermitToRelease = smartAcquireReadPermit(computationState)
      }
    }

    // For diagnostic purposes register that we in read action, even if we use stronger lock
    myReadActionsInThread.set(myReadActionsInThread.get() + 1)

    try {
      fireReadActionStarted(listener, clazz)
      val rv = action()
      return rv
    }
    finally {
      fireReadActionFinished(listener, clazz)

      myReadActionsInThread.set(myReadActionsInThread.get() - 1)
      if (readPermitToRelease != null) {
        computationState.releaseReadPermit(readPermitToRelease)
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

    val computationState = getComputationState()
    val currentPermit = computationState.getThisThreadPermit()
    var readPermitToRelease: ReadPermit? = null

    when (currentPermit) {
      null -> {
        readPermitToRelease = computationState.tryAcquireReadPermit()
        if (readPermitToRelease == null) {
          return false
        }
      }
      is ReadPermit, is WritePermit, is WriteIntentPermit -> {}
    }

    // For diagnostic purposes register that we in read action, even if we use stronger lock
    myReadActionsInThread.set(myReadActionsInThread.get() + 1)

    try {
      fireReadActionStarted(listener, action.javaClass)
      action.run()
      return true
    }
    finally {
      fireReadActionFinished(listener, action.javaClass)

      myReadActionsInThread.set(myReadActionsInThread.get() - 1)
      if (readPermitToRelease != null) {
        computationState.releaseReadPermit(readPermitToRelease)
      }

      myTopmostReadAction.set(currentReadState)
      fireAfterReadActionFinished(listener, action.javaClass)
    }
  }

  override fun isReadLockedByThisThread(): Boolean {
    return getComputationState().getThisThreadPermit() is ReadPermit
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
  override fun removeSuspendingWriteActionListener(listener: SuspendingWriteActionListener) {
    if (mySuspendingWriteActionListener != listener)
      error("SuspendingWriteActionListener is not registered")
    mySuspendingWriteActionListener = null
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
      error("WriteActionListener is not registered")
    myLockAcquisitionListener = null
  }

  override fun <T> runWriteAction(clazz: Class<*>, action: () -> T): T {
    val computationState = getComputationState()
    val releases = startWrite(computationState, clazz)
    return try {
      action()
    }
    finally {
      endWrite(computationState, clazz, releases)
    }
  }

  private fun startWrite(computationState: ComputationState, clazz: Class<*>): WriteListenerInitResult2 {
    val listener = myWriteActionListener
    // Read permit is incompatible
    check(computationState.getThisThreadPermit() !is ReadPermit) { "WriteAction can not be called from ReadAction" }

    // Check that write action is not disabled
    // NB: It is before all cancellations will be run via fireBeforeWriteActionStart
    // It is change for old behavior, when ProgressUtilService checked this AFTER all cancellations.
    if (!useBackgroundWriteAction && myNoWriteActionCounter.get() > 0) {
      // TODO TODO TODO use coroutine context elements
      throwCannotWriteException()
    }

    handleLockAccess("write lock")

    startPendingWriteAction(computationState)

    if (myWriteActionsStack.isEmpty()) {
      // TODO TODO TODO handle different-level write actions
      fireBeforeWriteActionStart(listener, clazz)
    }

    val currentReadState = myTopmostReadAction.get()
    myTopmostReadAction.set(false)

    val permit = computationState.getThisThreadPermit()

    var shouldRelease = false

    when (permit) {
      null -> {
        shouldRelease = true
        processWriteLockAcquisition {
          computationState.acquireWritePermit()
        }
      }
      is ReadPermit -> {
        endPendingWriteAction(computationState)
        error("WriteAction can not be called from ReadAction")
      }
      is WriteIntentPermit -> {
        shouldRelease = true
        processWriteLockAcquisition {
          computationState.upgradeWritePermit(permit)
        }
        checkWriteFromRead("Write", "Write Intent")
      }
      is WritePermit -> {
        checkWriteFromRead("Write", "Write")
      }
    }

    myWriteAcquired = Thread.currentThread()
    endPendingWriteAction(computationState)

    myWriteActionsStack.push(clazz)
    fireWriteActionStarted(listener, clazz)

    return WriteListenerInitResult2(shouldRelease, currentReadState, listener)
  }

  private fun startPendingWriteAction(state: ComputationState) {
    val stateLevel = state.level()
    myWriteActionPending.get()[stateLevel].incrementAndGet()
  }

  private fun endPendingWriteAction(state: ComputationState) {
    val stateLevel = state.level()
    myWriteActionPending.get()[stateLevel].decrementAndGet()
  }

  private data class WriteListenerInitResult2(
    val shouldRelease: Boolean,
    val currentReadState: Boolean,
    val listener: WriteActionListener?,
  )

  private fun endWrite(computationState: ComputationState, clazz: Class<*>, initResult: WriteListenerInitResult2) {
    fireWriteActionFinished(initResult.listener, clazz)
    myWriteActionsStack.pop()
    if (initResult.shouldRelease) {
      computationState.releaseWritePermit()
      myWriteAcquired = null
    }
    myTopmostReadAction.set(initResult.currentReadState)
    if (initResult.shouldRelease) {
      fireAfterWriteActionFinished(initResult.listener, clazz)
    }
  }

  override fun executeSuspendingWriteAction(action: () -> Unit) {
    val state = getComputationState()
    val permit = state.getThisThreadPermit()
    if (permit is WriteIntentPermit) {
      action()
      return
    }

    check(permit is WritePermit) { "Suspending write action must be called under write lock or write-intent lock" }
    val prevBase = myWriteStackBase
    myWriteStackBase = myWriteActionsStack.size
    myWriteAcquired = null
    val exposedPermitData = checkNotNull(state.hack_getPublishedWriteData()) {
      "Suspending write action was requested, but the thread did not start write action properly"
    }
    state.hack_setPublishedPermitData(null)
    exposedPermitData.writePermitStack.forEachReversed {
      it.release()
    }
    val rootWriteIntentPermit = exposedPermitData.originalWriteIntentPermit
    permit.release()
    state.hack_setThisLevelPermit(rootWriteIntentPermit)
    try {
      action()
    }
    finally {
      mySuspendingWriteActionListener?.beforeWriteLockReacquired()
      val newWritePermit = runSuspend {
        rootWriteIntentPermit.acquireWritePermit()
      }
      state.hack_setThisLevelPermit(newWritePermit)
      val newWritePermits = Array(exposedPermitData.writeIntentStack.size) {
        runSuspend {
          exposedPermitData.writeIntentStack[it].acquireWritePermit()
        }
      }
      state.hack_setPublishedPermitData(exposedPermitData.copy(writePermitStack = newWritePermits, finalWritePermit = newWritePermit))
      myWriteAcquired = Thread.currentThread()
      myWriteStackBase = prevBase
    }
  }

  override fun isWriteActionInProgress(): Boolean = myWriteAcquired != null

  override fun isWriteActionPending(): Boolean {
    return myWriteActionPending.get().last().get() > 0
  }

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
    val computationState = getComputationState()
    val currentPermit = computationState.getThisThreadPermit()
    if (currentPermit is WritePermit) {
      throw IllegalStateException("Write Action can not request Read Access Token")
    }
    if (currentPermit is ReadPermit || currentPermit is WriteIntentPermit) {
      return AccessToken.EMPTY_ACCESS_TOKEN
    }
    return object : AccessToken() {
      private val capturedListener = myReadActionListener
      private val myPermit = run {
        fireBeforeReadActionStart(capturedListener, javaClass)
        val p = computationState.acquireReadPermit()
        fireReadActionStarted(capturedListener, javaClass)
        p
      }

      override fun finish() {
        fireReadActionFinished(capturedListener, javaClass)
        computationState.releaseReadPermit(myPermit)
        fireAfterReadActionFinished(capturedListener, javaClass)
      }
    }
  }

  @Deprecated("Use `runWriteAction`, `WriteAction.run`, or `WriteAction.compute` instead")
  override fun acquireWriteActionLock(marker: Class<*>): AccessToken {
    logger.error("`ThreadingSupport.acquireWriteActionLock` is deprecated and going to be removed soon. Use `runWriteAction()` instead")
    return WriteAccessToken(marker)
  }

  override fun prohibitWriteActionsInside(): AccessToken {
    myNoWriteActionCounter.incrementAndGet()
    return object : AccessToken() {
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

  override fun allowTakingLocksInsideAndRun(action: java.lang.Runnable) {
    val currentValue = myLockingProhibited.get()
    myLockingProhibited.set(null)
    try {
      action.run()
    }
    finally {
      myLockingProhibited.set(currentValue)
    }
  }

  override fun getLockingProhibitedAdvice(): String? {
    return myLockingProhibited.get()?.second
  }


  override fun isInsideUnlockedWriteIntentLock(): Boolean {
    return false
  }

  private fun <T> processWriteLockAcquisition(acquisitor: () -> T): T {
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

  private fun fireBeforeWriteActionStart(listener: WriteActionListener?, clazz: Class<*>) {
    try {
      listener?.beforeWriteActionStart(clazz)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireWriteActionStarted(listener: WriteActionListener?, clazz: Class<*>) {
    try {
      listener?.writeActionStarted(clazz)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireWriteActionFinished(listener: WriteActionListener?, clazz: Class<*>) {
    try {
      listener?.writeActionFinished(clazz)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireAfterWriteActionFinished(listener: WriteActionListener?, clazz: Class<*>) {
    try {
      listener?.afterWriteActionFinished(clazz)
    }
    catch (_: Throwable) {
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

  @Deprecated("")
  private class WriteAccessToken(private val clazz: Class<*>) : AccessToken() {
    val compState = getComputationState()
    val releases = startWrite(compState, clazz)

    init {
      markThreadNameInStackTrace()
    }

    override fun finish() {
      try {
        endWrite(compState, clazz, releases)
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
