// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("IncorrectCancellationExceptionHandling")

package com.intellij.openapi.application.rw

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.concurrency.installThreadContext
import com.intellij.diagnostic.ThreadDumper
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.AsyncExecutionService
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ReadAndWriteScope
import com.intellij.openapi.application.ReadConstraint
import com.intellij.openapi.application.ReadResult
import com.intellij.openapi.application.ReadWriteActionSupport
import com.intellij.openapi.application.ThreadingSupport
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.TransactionGuardImpl
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.contextModality
import com.intellij.openapi.application.defaultModalityImpl
import com.intellij.openapi.application.impl.AsyncExecutionServiceImpl
import com.intellij.openapi.application.impl.InternalThreading
import com.intellij.openapi.application.lambdaToComputable
import com.intellij.openapi.application.useBackgroundWriteAction
import com.intellij.openapi.application.useBlockingEdtWriteActionImplementation
import com.intellij.openapi.application.useTrueSuspensionForWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.ObjectUtils
import com.intellij.util.application
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.ContinuationInterceptor
import kotlin.io.path.writeText
import kotlin.math.absoluteValue
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

@ApiStatus.Internal
class PlatformReadWriteActionSupport : ReadWriteActionSupport {

  private val retryMarker: Any = ObjectUtils.sentinel("rw action")

  private val edtWriteActionAcquisitionDispatcher = Dispatchers.IO.limitedParallelism(1, "EDT write action acquisition dispatcher")
  private val backgroundWriteActionDispatcher = Dispatchers.IO.limitedParallelism(1, "Background write action dispatcher")
  private val backgroundWriteActionDumpDispatcher =
    Dispatchers.IO.limitedParallelism(1, "Dispatcher for dumping threads and coroutines for background write action")
  private val shouldAbortPendingActions = AtomicBoolean(false)

  init {
    // init the write action counter listener
    ApplicationManager.getApplication().service<AsyncExecutionService>()
  }

  override fun smartModeConstraint(project: Project): ReadConstraint {
    check(!LightEdit.owns(project)) {
      "ReadConstraint.inSmartMode() can't be used in LightEdit mode, check that LightEdit.owns(project)==false before calling"
    }
    return SmartModeReadConstraint(project)
  }

  override fun committedDocumentsConstraint(project: Project): ReadConstraint {
    return CommittedDocumentsConstraint(project)
  }

  override suspend fun <X> executeReadAction(
    constraints: List<ReadConstraint>,
    undispatched: Boolean,
    blocking: Boolean,
    action: () -> X,
  ): X {
    return InternalReadAction(constraints, undispatched, blocking, action).runReadAction()
  }

  override fun <X, E : Throwable> computeCancellableUnsafe(action: ThrowableComputable<X, E>): X {
    return cancellableReadAction {
      action.compute()
    }
  }


  private sealed interface ReadResultImpl<out R> : ReadResult<R> {
    class WriteAction<out V>(val action: () -> V) : ReadResultImpl<V>
    class Value<out V>(val value: V) : ReadResultImpl<V>
  }

  private object ReadAndWriteScopeImpl : ReadAndWriteScope {
    override fun <R> value(value: R): ReadResultImpl<R> = ReadResultImpl.Value(value)
    override fun <R> writeAction(action: () -> R): ReadResultImpl<R> = ReadResultImpl.WriteAction(action)
  }

  override suspend fun <X> executeReadAndWriteAction(
    constraints: Array<out ReadConstraint>,
    runWriteActionOnEdt: Boolean,
    undispatched: Boolean,
    action: ReadAndWriteScope.() -> ReadResult<X>,
  ): X {
    while (true) {
      val (readResult: ReadResult<X>, stamp: Long) = executeReadAction(constraints.toList(),
                                                                       undispatched = undispatched,
                                                                       blocking = false) {
        Pair(ReadAndWriteScopeImpl.action(), AsyncExecutionServiceImpl.getWriteActionCounter())
      }
      require(readResult is ReadResultImpl<X>) {
        "Unexpected implementation of `ReadResult`: Expected ReadResultImpl, got ${readResult::class.simpleName}"
      }
      when (readResult) {
        is ReadResultImpl.Value -> {
          return readResult.value
        }
        is ReadResultImpl.WriteAction -> {
          val lock = application.threadingSupport
          val writeResult = if (runWriteActionOnEdt || lock == null) {
            executeWriteActionOnEdt(stamp, readResult.action)
          }
          else {
            try {
              InternalThreading.incrementBackgroundWriteActionCount()
              executeWriteActionOnBackgroundWithAtomicCheck(lock, stamp, readResult.action)
            }
            finally {
              InternalThreading.decrementBackgroundWriteActionCount()
            }
          }
          if (writeResult !== retryMarker) {
            @Suppress("UNCHECKED_CAST")
            return writeResult as X
          }
        }
      }
    }
  }

  private suspend fun <T> executeWriteActionOnEdt(originalStamp: Long, action: () -> T): /*T or retryMarker */ Any? {
    return withContext(Dispatchers.EDT) {
      val writeStamp = AsyncExecutionServiceImpl.getWriteActionCounter()
      if (originalStamp == writeStamp) {
        @Suppress("ForbiddenInSuspectContextMethod")
        application.runWriteAction(lambdaToComputable(action))
      }
      else {
        retryMarker
      }
    }
  }

  private suspend fun <T> executeWriteActionOnBackgroundWithAtomicCheck(
    lock: ThreadingSupport,
    originalStamp: Long,
    action: () -> T,
  ): /*T or retryMarker */ Any? {
    val dispatcher = backgroundWriteActionDispatcher
    return withContext(dispatcher) {
      val execResult = lock.runWriteActionWithExecutor(
        action,
        { publishedBackgroundWriteActionJobs.add(it) },
        { publishedBackgroundWriteActionJobs.remove(it) },
        {
          val writeStamp = AsyncExecutionServiceImpl.getWriteActionCounter()
          originalStamp == writeStamp
        }) { actualAction, job ->
        val result = publishedBackgroundWriteActionJobs.remove(job)
        if (!result) {
          return@runWriteActionWithExecutor ThreadingSupport.ExecutorResult.Retry
        }
        if (job.isCancelled) {
          ThreadingSupport.ExecutorResult.Retry
        }
        else {
          ThreadingSupport.ExecutorResult.Completion(actualAction())
        }
      }
      when (execResult) {
        is ThreadingSupport.WriteActionResult.Completion<T> -> execResult.value
        ThreadingSupport.WriteActionResult.Denied -> retryMarker
      }
    }
  }


  private object DeniedSentinel

  /**
   * EDT write action is intended to run on the UI thread.
   *
   * Since the UI thread is a single-threaded executor, the naive implementation is prone to deadlocks:
   * we can initiate a pending locking action, suspend, and then the next computation would block the executor on a blocking locking action --
   * like a blocking read action.
   * This fact makes the implementation quite sophisticated -- we acquire a write permit on a background executor,
   * and then we perform synchronous transition to the EDT via custom-made `invokeAndWait`.
   * To avoid the aforementioned deadlock, we allow retrying the EDT write action until it succeeds.
   *
   * By doing this, we effectively destroy write-bias of the Read-Write Lock. While it sounds dangerous -- we are open to starvation --
   * our domain dictates that the responsiveness of the UI is of the utmost priority,
   * hence we allow to stall writes if the UI thread wants to run a reading operation.
   */
  override suspend fun <T> runEdtWriteAction(action: () -> T): T {
    if (useBlockingEdtWriteActionImplementation) {
      @Suppress("ForbiddenInSuspectContextMethod")
      return withContext(Dispatchers.EDT) {
        ApplicationManager.getApplication().runWriteAction(lambdaToComputable<T>(action))
      }
    }
    val lock = application.threadingSupport!!
    val dispatcher = edtWriteActionAcquisitionDispatcher
    val outerContext = currentCoroutineContext()
    val outerModality = defaultModalityImpl()
    return withContext(dispatcher) {
      while (true) {
        val singleWriteActionResult = lock.runWriteActionWithExecutor<T, Result<T>>(
          action = action,
          onJobPublished = { publishedEdtWriteActionJobs.add(it) },
          onJobNotNeeded = { publishedEdtWriteActionJobs.remove(it) },
          shouldProceedWithWriteAction = { !shouldAbortPendingActions.get() })
        { actualAction, job ->
          // this is a background thread
          // but we have write access now

          // `resultDeferred` is the carrier of the final result.
          // completion with a `Result` means that the [action] has completed -- either successfully or with an exception
          // completion with a `Result` of `DeniedSentinel` means that there is a need to do a retry with external means -- we want to reschedule this computation with the EventQueue
          // cancellation means that the execution needs to be retried by the internal means -- the lock will be released to make progress in some other place
          val resultDeferred: CompletableDeferred<Result<Any? /* DeniedSentinel or T */>> = CompletableDeferred(null)
          // affinity guard: we allow execution no more than once
          val execAllowed = AtomicBoolean(true)
          @OptIn(InternalCoroutinesApi::class)
          job.invokeOnCompletion(onCancelling = true) {
            // either we promptly cancel the action for retry, or execute it once
            if (!execAllowed.getAndSet(false)) {
              return@invokeOnCompletion
            }
            resultDeferred.cancel()
          }
          val outerResult: AtomicReference<Result<T>?> = AtomicReference(null)
          @Suppress("OPT_IN_USAGE")
          GlobalScope.async(context = outerContext + Dispatchers.UiWithModelAccess + ModalityState.any().asContextElement()) {
            if (!execAllowed.getAndSet(false)) {
              return@async
            }
            // This condition handles the following case:
            // before the event with the edt write action starts executing, someone else entered modality without lock parallelization
            // in this case we would not be able to run the write action, because this computation would get delayed until modal computation ends
            // but the modal computation itself might not progress because it might need write-intent lock which is prevented by this edt WA
            // so we do a trick: we schedule each computation with `ModalityState.any`, but do a check whether the modality is compatible inside.
            // if it is not, we abort the entire edt write action to release the underlying locks,
            // and retry it once again when the modality states are more forgiving
            if (!ModalityState.current().accepts(outerModality)) {
              resultDeferred.complete(Result.success(DeniedSentinel))
              return@async
            }
            // so we are allowed to run; let's not allow anyone to cancel us
            publishedEdtWriteActionJobs.remove(job)
            try {
              // context management is quite tricky here
              // we need to take the coroutine context from the launched coroutine to preserve the correct job
              // we also need to remove the coroutine dispatcher to be able to run `runBlockingCancellable` inside with a thread-local event loop
              // also, due to our tricks with modality, this coroutine runs under `any`, so the modality also needs to be corrected
              val newContext = coroutineContext.minusKey(ContinuationInterceptor.Key) +
                               ((outerContext.contextModality() ?: ModalityState.nonModal()).asContextElement())
              @Suppress("DEPRECATION") // let's not add unnecessary stack traces
              installThreadContext(newContext).use {
                // we are in ModalityState.any, so it is not allowed to run write actions
                // but we have now corrected the modality, so let's explicitly allow running write actions
                (TransactionGuard.getInstance() as TransactionGuardImpl).performUserActivity {
                  val result = actualAction()
                  outerResult.set(Result.success(result))
                }
              }
            }
            catch (t: Throwable) {
              coroutineContext.job.cancel()
              // we are making this computation non-throwing to better communicate between internal exceptions and exceptions originating from `action`
              outerResult.set(Result.failure(t))
            }
          }.invokeOnCompletion {
            // we need to wait until the execution of `actualAction` before assigning the result
            // to avoid early returns from `async`
            val outerResult = outerResult.get()
            if (outerResult != null) {
              resultDeferred.complete(outerResult)
            }
          }
          try {
            val resultValue = resultDeferred.asCompletableFuture().get()
            if (resultValue.isSuccess && resultValue.getOrThrow() is DeniedSentinel) {
              ThreadingSupport.ExecutorResult.Denied
            }
            else {
              @Suppress("UNCHECKED_CAST")
              ThreadingSupport.ExecutorResult.Completion(resultValue as Result<T>)
            }
          }
          catch (_: CancellationException) {
            ThreadingSupport.ExecutorResult.Retry
          }
        }
        when (singleWriteActionResult) {
          is ThreadingSupport.WriteActionResult.Completion<Result<T>> -> return@withContext singleWriteActionResult.value.getOrThrow()
          ThreadingSupport.WriteActionResult.Denied -> {
            // `Denied` originates in issues with modality or when someone decided to delay execution of this edt wa
            // To fix modalities, we reschedule this computation with the context modality so that it can be retried in a correct modality
            // To fix request for delay, we put ourselves to the end of the NonBlockingFlushQueue and we let the existing events to run
            withContext(Dispatchers.EDT) {}
            continue
          }
        }
      }
      @Suppress("KotlinUnreachableCode")
      error("unreachable")
    }
  }

  private fun signalWriteActionNeedsToBeRetried(target: MutableSet<Job>) {
    val exception = ThreadingSupport.RetryLockAcquisitionException()
    val entries = target.toMutableList()
    while (entries.isNotEmpty()) {
      try {
        val entry = entries.removeLast()
        target.remove(entry)
        entry.cancel(exception)
      }
      catch (_: NoSuchElementException) {
        break
      }
    }
  }

  fun signalBackgroundWriteActionNeedsToBeRetried() {
    signalWriteActionNeedsToBeRetried(publishedBackgroundWriteActionJobs)
  }

  fun signalSuspendedEdtWriteActionNeedsToBeRetried() {
    shouldAbortPendingActions.set(true)
    signalWriteActionNeedsToBeRetried(publishedEdtWriteActionJobs)
  }

  fun signalSuspendedEdtWriteActionCanProceed() {
    shouldAbortPendingActions.set(false)
  }

  private val publishedBackgroundWriteActionJobs: MutableSet<Job> = ConcurrentCollectionFactory.createConcurrentSet()
  private val publishedEdtWriteActionJobs: MutableSet<Job> = ConcurrentCollectionFactory.createConcurrentSet()


  override suspend fun <T> runWriteAction(action: () -> T): T {
    val context = if (useBackgroundWriteAction) {
      backgroundWriteActionDispatcher
    }
    else {
      Dispatchers.EDT
    }

    return withContext(context) {
      val dumpJob = if (useBackgroundWriteAction) launch(backgroundWriteActionDumpDispatcher) {
        delay(10.seconds)
        val dump = ThreadDumper.getThreadDumpInfo(ThreadDumper.getThreadInfos(), false)
        val dumpDir = PathManager.getLogDir().resolve("bg-wa")
        val file = dumpDir.resolve("thread-dump-${Random.nextInt().absoluteValue}.txt")
        try {
          Files.createDirectories(dumpDir)
          Files.createFile(file)
          file.writeText(dump.rawDump)
          logger<ApplicationManager>().warn(
            """Cannot execute background write action in 10 seconds. Thread dump is stored in ${file.toUri()}""")
        }
        catch (_: IOException) {
          logger<ApplicationManager>().warn(
            """Cannot execute background write action in 10 seconds.
Thread dump:
${dump.rawDump}""")
        }

      }
      else null
      val application = ApplicationManager.getApplication()
      val lock = application.threadingSupport
      try {
        if (useBackgroundWriteAction && useTrueSuspensionForWriteAction && lock != null) {
          InternalThreading.incrementBackgroundWriteActionCount()
          try {
            lock.runWriteActionWithExecutor(action, { job ->
              publishedBackgroundWriteActionJobs.add(job)
            }, { publishedBackgroundWriteActionJobs.remove(it) }) { actualAction, job ->
              val result = publishedBackgroundWriteActionJobs.remove(job)
              if (!result) {
                return@runWriteActionWithExecutor ThreadingSupport.ExecutorResult.Retry
              }
              if (job.isCancelled) {
                ThreadingSupport.ExecutorResult.Retry
              }
              else {
                ThreadingSupport.ExecutorResult.Completion(actualAction())
              }
            }
          }
          finally {
            InternalThreading.decrementBackgroundWriteActionCount()
          }
        }
        else {
          @Suppress("ForbiddenInSuspectContextMethod")
          application.runWriteAction(lambdaToComputable(action))
        }
      }
      finally {
        dumpJob?.cancel()
      }
    }
  }
}