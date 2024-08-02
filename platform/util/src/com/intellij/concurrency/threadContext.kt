// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ThreadContext")
@file:Experimental

package com.intellij.concurrency

import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.Cancellation
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.*
import kotlinx.coroutines.*
import kotlinx.coroutines.internal.intellij.IntellijCoroutines
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.Callable
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private val LOG: Logger = Logger.getInstance("#com.intellij.concurrency")

/**
 * This class contains an overriding coroutine context for [IntellijCoroutines.currentThreadCoroutineContext].
 *
 * ## Rule
 * The rule of selection is the following:
 * [context] is taken only if [snapshot] is equal to [IntellijCoroutines.currentThreadCoroutineContext] __as a pointer__.
 *
 * The idea is that we can perform a one-direction transition from the suspending to the non-suspending execution context.
 * When the transition occurs, we remember [IntellijCoroutines.currentThreadCoroutineContext] of this transition,
 * and all later thread context modifications happen witnessed by this remembered coroutine context.
 * If [IntellijCoroutines.currentThreadCoroutineContext] changes, then the overriding thread context is no longer valid,
 * hence we prioritize [IntellijCoroutines.currentThreadCoroutineContext] again.
 *
 * ## Motivation
 * In suspending code, thread context must be taken from coroutines:
 * ```kotlin
 * val a : A
 * withContext(a) {
 *   assertEquals(a, currentThreadContext()[A])
 * }
 * ```
 *
 * But the explicit installation of thread context takes precedence:
 * ```kotlin
 * withContext(a) {
 *   installThreadContext(b).use {
 *     assertEquals(b, currentThreadContext())
 *   }
 * }
 * ```
 *
 * In some cases, within one thread coroutine context may have priority again:
 * ```kotlin
 * installThreadContext(b).use {
 *   scope.launch(start = CoroutineStart.UNDISPATCHED) {
 *     assertNotEquals(b, currentThreadContext())
 *   }
 * }
 * ```
 */
private data class InstalledThreadContext(
  /**
   * - [snapshot] === `null`: [IntellijCoroutines.currentThreadCoroutineContext] is not installed,
   *   i.e., the computation does not originate in coroutines;
   * - [snapshot] !== `null`: we override [IntellijCoroutines.currentThreadCoroutineContext] which is equal to [snapshot].
   */
  val snapshot: CoroutineContext?,
  /**
   * The overriding coroutine context.
   * It can be explicitly reset, so `null` is a permitted value.
   */
  val context: CoroutineContext?
)

private val INITIAL_THREAD_CONTEXT = InstalledThreadContext(null, null)

private val tlCoroutineContext: ThreadLocal<InstalledThreadContext> = ThreadLocal.withInitial {
  INITIAL_THREAD_CONTEXT
}

private inline fun currentThreadContextOrFallback(getter: (CoroutineContext?) -> CoroutineContext?): CoroutineContext? {
  if (!useImplicitBlockingContext) {
    return tlCoroutineContext.get().context
  }
  @OptIn(InternalCoroutinesApi::class)
  val suspendingContext = IntellijCoroutines.currentThreadCoroutineContext()
  val (snapshot, overridingContext) = tlCoroutineContext.get()
  if (suspendingContext === snapshot) {
    return overridingContext
  }
  else {
    return getter(suspendingContext)
  }
}

@VisibleForTesting
@TestOnly
@ApiStatus.Internal
fun currentThreadContextOrNull(): CoroutineContext? {
  return currentThreadContextOrFallback { null }
}


/**
 * @return current thread context
 */
fun currentThreadContext(): CoroutineContext {
  checkContextInstalled()
  return currentThreadContextOrFallback { it?.minusKey(ContinuationInterceptor) } ?: EmptyCoroutineContext
}

private fun checkContextInstalled() {
  if (isCheckContextAssertions
      && LoadingState.APP_STARTED.isOccurred
      && currentThreadContextOrFallback { it } == null
      && !isKnownViolator()) {
    LOG.warn("Missing thread context", Throwable())
  }
}

private val VIOLATORS : List<String> = listOf(
  /*
   * EDT-level checks, operating on lower level than contexts
   */
  "com.intellij.diagnostic",
  "com.intellij.openapi.wm.impl",
  "com.intellij.model.SideEffectGuard",
  "com.intellij.openapi.editor.impl",
  "com.intellij.ui.components",
  "com.intellij.openapi.progress.util",
  "com.intellij.openapi.application.impl.NonBlockingReadActionImpl\$Submission.reschedule",
  "com.intellij.openapi.keymap.impl.SystemShortcuts",
  "com.intellij.ide.IdeKeyboardFocusManager",
  "com.intellij.execution.process.ProcessIOExecutorService",
  "com.intellij.util.animation",
  "com.intellij.util.ui",
  "com.intellij.ide.ui.popup",
  "com.intellij.ui",
  "org.jetbrains.io",
  "com.intellij.javascript.webSymbols.nodejs.WebTypesNpmLoader",
  "com.intellij.tasks",
  "com.intellij.util.concurrency.Invoker",
  /**
   * Platform runnables within NBRA, there is no user logic there
   */
  "com.intellij.openapi.application.constraints",
  /*
   * TODO, is not needed on current stage
   */
  "com.intellij.openapi.actionSystem",
  /*
   * TODO
   */
  "org.jetbrains.idea.maven.server",
  /*
   * mostly FUS, can operate without contexts (maybe TODO)
   */
  "com.intellij.internal.statistic",
  /*
   * Logging does not need contexts (for now), can be ignored
   */
  "com.intellij.openapi.diagnostic.Logger",
  /*
   * We can tolerate the absence of context in 'getExtensions'
   */
  "com.intellij.openapi.extensions.impl.ExtensionPointImpl.getExtensionList",
  "com.intellij.openapi.extensions.impl.ExtensionPointImpl.getExtensions",
  /**
   * Definitely TODO
   */
  "com.intellij.serviceContainer.LazyExtensionInstance.createInstance",
  "com.intellij.ui.icons",
  "com.intellij.ui.tree",
  /*
   * TODO
   */
  "com.intellij.openapi.project.SmartModeScheduler.onStateChanged",
  /*
   * Swing-related code is not supported now
   */
  "javax.swing.JComponent.paint",
  "com.intellij.openapi.application.impl.LaterInvocator.leaveModal",
  "com.intellij.openapi.application.impl.LaterInvocator.invokeAndWait",
  "com.intellij.util.animation.JBAnimator.animate",
  /*
   * TODO
   */
  "com.intellij.util.messages.impl.SimpleMessageBusConnectionImpl.disconnect",
  /*
   * TODO
   */
  "com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl.scheduleUpdateRunnable",
  "com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl.stopProcess",
  "com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl",
  /*
   * TODO
   */
  "com.intellij.openapi.wm.impl.WindowCloseListener.windowClosing",
  "com.intellij.ide.ApplicationActivationStateManager.updateState",
  /*
   * Gentle flusher's initialization is scheduled before app loads completely, so we do not have context on the moment of scheduling
   */
  "com.intellij.openapi.util.io.GentleFlusherBase",
)

private fun isKnownViolator() : Boolean {
  val stackTrace = Throwable().stackTrace.map { it.className + "." + it.methodName }
  return VIOLATORS.any { badTrace -> stackTrace.any { it.startsWith(badTrace) } }
}

private val shouldWarnAccidentalCancellation = SystemProperties.getBooleanProperty("ide.warn.accidental.cancellation", false)

/**
 * In the IntelliJ codebase, there are some areas that are not supposed to meet [com.intellij.openapi.progress.ProcessCanceledException],
 * but they do not have any syntactic markup preventing cancellation.
 * We call these areas "implicitly non-cancellable".
 * Since the introduction of implicit blocking context ([IJPL-445](https://youtrack.jetbrains.com/issue/IJPL-445/Reconsider-blockingContext)),
 * every implicitly non-cancellable section has become cancellable.
 * This can introduce hard-to-debug regressions when some part of the code accidentally dies due to cancellation.
 *
 * A natural example is the cleanup phase of the resource management frameworks
 * ([com.intellij.openapi.util.Disposer], [Job] or [com.jetbrains.rd.util.lifetime.Lifetime]).
 * It is often assumed that the code executed at the end of the lifecycle is non-cancellable,
 * but it was not enforced by the platform before.
 *
 * To fix this regression, two options are available:
 * - Easy, but not recommended: the authors of the code need to make the implicitly non-cancellable section explicit.
 *   It can be achieved by [Cancellation.executeInNonCancelableSection].
 * - Difficult, but recommended: refactor the code in a way that the implicit non-cancellable does not use heavy platform functions
 *   that are checked for cancellation.
 */
internal fun warnAccidentalCancellation() {
  if (!shouldWarnAccidentalCancellation) {
    return
  }
  if (Cancellation.isInNonCancelableSection()) {
    return
  }
  @OptIn(InternalCoroutinesApi::class)
  val kotlinCoroutineContext = IntellijCoroutines.currentThreadCoroutineContext()
  val (snapshot, _) = tlCoroutineContext.get()
  if (snapshot === kotlinCoroutineContext) {
    // someone installed the context before, so no regressions here are expected
    return
  }
  if (kotlinCoroutineContext?.get(Job.Key)?.isActive == false) {
    // the code is executing under a canceled Job, which means that the first checkCancelled will throw.
    // this was not the case before, as the old thread context was not explicitly set.
    LOG.warn("""Detected a cancellation in an implicit non-cancellable section.
The code executing here will be aborted because of cancellation.
If this behavior is unexpected, please consult the documentation for com.intellij.concurrency.ThreadContext.warnAccidentalCancellation.""",
             Throwable("Querying stacktrace"))
  }
}

/**
 * Resets the current thread context to initial value.
 *
 * @return handle to restore the previous thread context
 */
fun resetThreadContext(): AccessToken {
  return withThreadLocal(tlCoroutineContext) { _ ->
    @OptIn(InternalCoroutinesApi::class)
    val currentSnapshot = IntellijCoroutines.currentThreadCoroutineContext()
    InstalledThreadContext(currentSnapshot, null)
  }
}

/**
 * Installs [coroutineContext] as the current thread context.
 * If [replace] is `false` (default) and the current thread already has context, then this function logs an error.
 *
 * @return handle to restore the previous thread context
 */
fun installThreadContext(coroutineContext: CoroutineContext, replace: Boolean = false): AccessToken {
  return withThreadLocal(tlCoroutineContext) { previousContext ->
    @OptIn(InternalCoroutinesApi::class)
    val currentSnapshot = IntellijCoroutines.currentThreadCoroutineContext()
    if (!replace && previousContext.snapshot === currentSnapshot && previousContext.context != null) {
      LOG.error("Thread context was already set: $previousContext. \n Most likely, you are using 'runBlocking' instead of 'runBlockingCancellable' somewhere in the asynchronous stack.")
    }
    InstalledThreadContext(currentSnapshot, coroutineContext)
  }
}

/**
 * This context is not supposed to be captured ever.
 * Use case: pass modality state to service initialization;
 * this modality state must not be captured in scheduled tasks and/or
 * listeners which originate in service constructor or service's `loadState()`.
 */
private val tlTemporaryContext: ThreadLocal<CoroutineContext?> = ThreadLocal()

@Internal
fun currentTemporaryThreadContextOrNull(): CoroutineContext? {
  return tlTemporaryContext.get()
}

@Internal
fun installTemporaryThreadContext(coroutineContext: CoroutineContext): AccessToken {
  return withThreadLocal(tlTemporaryContext) { _ ->
    coroutineContext
  }
}

/**
 * Updates given [variable] with a new value obtained by applying [update] to the current value.
 * Returns a token which must be [closed][AccessToken.close] to revert the [variable] to the previous value.
 * The token implementation ensures that nested updates and reverts are mirrored:
 * [update0, update1, ... updateN, revertN, ... revert1, revert0].
 * Unordered updates, such as [update0, update1, revert0, revert1] will result in [IllegalStateException].
 *
 * Example usage:
 * ```
 * withThreadLocal(ourCounter) { value ->
 *   value + 1
 * }.use {
 *   ...
 * }
 *
 * // or, if the new value does not depend on the current one
 * withThreadLocal(ourCounter) { _ ->
 *   42
 * }.use {
 *   ...
 * }
 * ```
 *
 * TODO ? move to more appropriate package before removing `@Internal`
 */
@Internal
fun <T> withThreadLocal(variable: ThreadLocal<T>, update: (value: T) -> T): AccessToken {
  val previousValue = variable.get()
  val newValue = update(previousValue)
  if (newValue === previousValue) {
    return AccessToken.EMPTY_ACCESS_TOKEN
  }
  variable.set(newValue)
  return object : AccessToken() {
    override fun finish() {
      val currentValue = variable.get()
      variable.set(previousValue)
      check(currentValue === newValue) {
        "Value was not reset correctly. Expected: $newValue, actual: $currentValue"
      }
    }
  }
}

/**
 * Returns a `Runnable` instance, which saves [currentThreadContext] and,
 * when run, installs the saved context and runs original [runnable] within the installed context.
 * ```
 * val executor = Executors.newSingleThreadExecutor()
 * val context = currentThreadContext()
 * executor.submit {
 *   installThreadContext(context).use {
 *     runnable.run()
 *   }
 * }
 * // is roughly equivalent to
 * executor.submit(captureThreadContext(runnable))
 * ```
 *
 * Before installing the saved context, the returned `Runnable` asserts that there is no context already installed in the thread.
 * This check effectively forbids double capturing, e.g. `captureThreadContext(captureThreadContext(runnable))` will fail.
 * This method should be used with executors from [java.util.concurrent.Executors] or with [java.util.concurrent.CompletionStage] methods.
 * Do not use this method with executors returned from [com.intellij.util.concurrency.AppExecutorUtil], they already capture the context.
 */
@ApiStatus.Internal
fun captureThreadContext(runnable: Runnable): Runnable {
  return captureRunnableThreadContext(runnable)
}

/**
 * Same as [captureThreadContext] but for [Supplier]
 */
@ApiStatus.Internal
fun <T> captureThreadContext(s : Supplier<T>) : Supplier<T> {
  val c = captureCallableThreadContext(s::get)
  return Supplier(c::call)
}

/**
 * Same as [captureThreadContext] but for [Consumer]
 */
@ApiStatus.Internal
fun <T> captureThreadContext(c : Consumer<T>) : Consumer<T> {
  val f = capturePropagationContext(c::accept)
  return Consumer(f::apply)
}

/**
 * Same as [captureThreadContext] but for [Function]
 */
@ApiStatus.Internal
fun <T, U> captureThreadContext(f : Function<T, U>) : Function<T, U> {
  return capturePropagationContext(f)
}

/**
 * We do not want to mark any custom context elements as internal at all.
 * However, currently it is required until the platform team fixes context invariants.
 * In this doc comment, we shall list all elements that are currently internal. If you want to add something here,
 * please consult with the platform team.
 *
 * - `ComponentManagerElement`, because not all entry points to coroutine come from containers at the moment.
 */
@Internal
interface InternalCoroutineContextKey<T : CoroutineContext.Element> : CoroutineContext.Key<T>

/**
 * Strips off internal elements from thread contexts.
 * If you need to compare contexts by equality, most likely you need to use this method.
 */
@ApiStatus.Internal
fun getContextSkeleton(context: CoroutineContext): Set<CoroutineContext.Element> {
  checkContextInstalled()
  return context.fold(HashSet()) { acc, element ->
    when (element.key) {
      Job -> Unit
      // It is normal to have multiple `BlockingJob` in the context.
      // But treating them as separate leads to non-mergeable updates in MergingUpdateQueue
      // An ideal solution would be to provide a way to merge several thread contexts under one, but there is no real need in it yet.
      BlockingJob -> Unit
      CoroutineName -> Unit
      is InternalCoroutineContextKey<*> -> Unit
      @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE") kotlinx.coroutines.CoroutineId -> Unit
      else -> acc.add(element)
    }
    acc
  }
}

/**
 * Same as [captureCallableThreadContext] but for [Callable].
 */
@ApiStatus.Internal
fun <V> captureThreadContext(callable: Callable<V>): Callable<V> {
  return captureCallableThreadContext(callable)
}
