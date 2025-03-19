// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.rd.util

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.contextModality
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.StandardProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.ProgressDetails
import com.intellij.openapi.util.NlsContexts.ProgressText
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.platform.util.progress.asContextElement
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.util.awaitCancellationAndInvoke
import com.jetbrains.rd.framework.util.launch
import com.jetbrains.rd.framework.util.startAsync
import com.jetbrains.rd.framework.util.startChildAsync
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.isNotAlive
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.trySendBlocking
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import kotlin.coroutines.CoroutineContext

fun Lifetime.launchWithBackgroundProgress(
  project: Project,
  title: @NlsContexts.ProgressTitle String,
  cancellation: TaskCancellation,
  action: suspend CoroutineScope.() -> Unit
): Job = launchBackground {
  withBackgroundProgress(project, title, cancellation, action)
}

fun Lifetime.launchWithBackgroundProgress(
  project: Project,
  title: @NlsContexts.ProgressTitle String,
  canBeCancelled: Boolean = true,
  action: suspend CoroutineScope.() -> Unit
): Job = launchWithBackgroundProgress(project, title, if (canBeCancelled) TaskCancellation.cancellable() else TaskCancellation.nonCancellable(), action)

fun <T> Lifetime.startWithBackgroundProgressAsync(
  project: Project,
  title: @NlsContexts.ProgressTitle String,
  cancellation: TaskCancellation,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = startBackgroundAsync {
  withBackgroundProgress(project, title, cancellation, action)
}

fun <T> Lifetime.startWithBackgroundProgressAsync(
  project: Project,
  title: @NlsContexts.ProgressTitle String,
  canBeCancelled: Boolean = true,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = startWithBackgroundProgressAsync(project, title, if (canBeCancelled) TaskCancellation.cancellable() else TaskCancellation.nonCancellable(), action)


fun Lifetime.launchWithModalProgress(
  owner: ModalTaskOwner,
  title: @NlsContexts.ProgressTitle String,
  cancellation: TaskCancellation,
  action: suspend CoroutineScope.() -> Unit,
): Job = launchBackground {
  withModalProgress(owner, title, cancellation, action)
}

fun Lifetime.launchWithModalProgress(
  project: Project,
  title: @NlsContexts.ProgressTitle String,
  action: suspend CoroutineScope.() -> Unit,
): Job = launchBackground {
  withModalProgress(project, title, action)
}

fun Lifetime.startWithModalProgressAsync(
  owner: ModalTaskOwner,
  title: @NlsContexts.ProgressTitle String,
  cancellation: TaskCancellation,
  action: suspend CoroutineScope.() -> Unit,
): Job = startBackgroundAsync {
  withModalProgress(owner, title, cancellation, action)
}

fun Lifetime.startWithModalProgressAsync(
  project: Project,
  title: @NlsContexts.ProgressTitle String,
  action: suspend CoroutineScope.() -> Unit,
): Job = startBackgroundAsync {
  withModalProgress(project, title, action)
}

@Deprecated("Use withModalProgress")
suspend fun <T>  withModalProgressContext(
  @Nls(capitalization = Nls.Capitalization.Title) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  project: Project? = null,
  lifetime: Lifetime = Lifetime.Eternal,
  action: suspend ProgressCoroutineScope.() -> T
): T {
  val owner = if (project != null) ModalTaskOwner.project(project) else ModalTaskOwner.guess()
  val cancellation = if (canBeCancelled) TaskCancellation.cancellable() else TaskCancellation.nonCancellable()
  return withModalProgress(owner, title, cancellation) {
    withBackgroundContext(lifetime) {
      ProgressCoroutineScopeBridge.use(true, action)
    }
  }
}

@Deprecated("Use withModalProgress")
suspend fun <T>  withModalProgressContext(
  @Nls(capitalization = Nls.Capitalization.Title) title: String,
  canBeCancelled: Boolean = true,
  project: Project? = null,
  action: suspend ProgressCoroutineScope.() -> T
): T = withModalProgressContext(title, canBeCancelled, true, project, Lifetime.Eternal, action)

@Deprecated("Use withBackgroundProgress")
suspend fun <T> withBackgroundProgressContext(
  @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
  canBeCancelled: Boolean = true,
  project: Project,
  action: suspend ProgressCoroutineScope.() -> T
): T = withBackgroundProgress(project, title, canBeCancelled) {
  withBackgroundContext {
    ProgressCoroutineScopeBridge.use(false, action)
  }
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use withBackgroundProgress")
suspend fun <T> withBackgroundProgressContext(
  @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  lifetime: Lifetime = Lifetime.Eternal,
  action: suspend ProgressCoroutineScope.() -> T
): T {
  val context = CoroutineProgressContext.createBackgroundable(lifetime, title, canBeCancelled, isIndeterminate, null)
  return doRunUnderProgress(context, action)
}

@Deprecated("Use launchWithModalProgress")
fun Lifetime.launchUnderModalProgress(
  @Nls(capitalization = Nls.Capitalization.Title) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  project: Project? = null,
  action: suspend ProgressCoroutineScope.() -> Unit
): Job = launchBackground {
  withModalProgressContext(title, canBeCancelled, isIndeterminate, project, this@launchUnderModalProgress, action)
}

@Deprecated("Use launchWithBackgroundProgress")
fun Lifetime.launchUnderBackgroundProgress(
  @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  project: Project,
  action: suspend ProgressCoroutineScope.() -> Unit
): Job = launchBackground { withBackgroundProgressContext(title, canBeCancelled, project, action) }

@Deprecated("Use launchWithBackgroundProgress")
fun Lifetime.launchUnderBackgroundProgress(
  @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  action: suspend ProgressCoroutineScope.() -> Unit
): Job = runBackgroundAsync(title, canBeCancelled, isIndeterminate, null) { dispatcher, indicator ->
  launch(dispatcher) { ProgressCoroutineScopeLegacy.execute(coroutineContext, this@runBackgroundAsync, indicator(), action) }
}

@Deprecated("Use startWithModalProgressAsync")
fun <T> Lifetime.startUnderModalProgressAsync(
  @Nls(capitalization = Nls.Capitalization.Title) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  project: Project? = null,
  action: suspend ProgressCoroutineScope.() -> T
): Deferred<T> = startBackgroundAsync {
  withModalProgressContext(title, canBeCancelled, isIndeterminate, project, this@startUnderModalProgressAsync, action)
}

@Deprecated("Use startWithBackgroundProgressAsync")
fun <T> Lifetime.startUnderBackgroundProgressAsync(
  @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  project: Project,
  action: suspend ProgressCoroutineScope.() -> T
): Deferred<T> = startBackgroundAsync { withBackgroundProgressContext(title, canBeCancelled, project, action) }


@ApiStatus.ScheduledForRemoval
@Deprecated("Use startWithBackgroundProgressAsync")
fun <T> Lifetime.startUnderBackgroundProgressAsync(
  @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  action: suspend ProgressCoroutineScope.() -> T
): Deferred<T> {
  return runBackgroundAsync(title, canBeCancelled, isIndeterminate, null) { dispatcher, indicator ->
    startAsync(dispatcher) { ProgressCoroutineScopeLegacy.execute(coroutineContext, this@runBackgroundAsync, indicator(), action) }
  }
}

private fun <T: Job> Lifetime.runBackgroundAsync(
  @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  project: Project? = null,
  startCoroutine: Lifetime.(CoroutineDispatcher, () -> ProgressIndicator) -> T): T {
  val context = CoroutineProgressContext.createBackgroundable(this, title, canBeCancelled, isIndeterminate, project)
  return doRunUnderProgressAsync(context, startCoroutine)
}

private suspend fun <T> doRunUnderProgress(context: CoroutineProgressContext, runCoroutine: suspend ProgressCoroutineScope.() -> T): T {
  return coroutineScope {
    doRunUnderProgressAsync(context) { dispatcher, indicator ->
      startChildAsync(context.lifetime, dispatcher) {
        ProgressCoroutineScopeLegacy.execute(coroutineContext, context.lifetime, indicator(), runCoroutine)
      }
    }.await()
  }
}

private inline fun <T: Job> doRunUnderProgressAsync(context: CoroutineProgressContext, startCoroutine: Lifetime.(CoroutineDispatcher, () -> ProgressIndicator) -> T): T {
  return context.lifetime.startCoroutine(context.dispatcher, context.getIndicator).also {
    it.invokeOnCompletion {
      context.lifetimeDefinition.terminate()
      context.channel.close()
    }
    context.task.queue()
  }
}

private class CoroutineProgressContext(
  val lifetimeDefinition: LifetimeDefinition,
  val dispatcher: CoroutineDispatcher,
  val channel: Channel<Runnable>,
  val task: Task,
  val getIndicator: () -> ProgressIndicator) {
  val lifetime: Lifetime get() = lifetimeDefinition.lifetime

  companion object {
    fun create(lifetime: Lifetime, isIndeterminate: Boolean = true, createTask: (run: (ProgressIndicator) -> Unit) -> Task): CoroutineProgressContext {
      val channel = Channel<Runnable>(Channel.UNLIMITED)
      val dispatcher = object : CoroutineDispatcher() {

        @Volatile
        var thread: Thread? = null

        override fun dispatch(context: CoroutineContext, block: Runnable) {
          val result = channel.trySendBlocking(block)
          result.getOrThrow()
        }

        override fun isDispatchNeeded(context: CoroutineContext) = thread != Thread.currentThread()
      }

      val taskLifetimeDef = lifetime.createNested()
      lateinit var progressIndicator: ProgressIndicator

      val task = createTask { indicator ->
        indicator.isIndeterminate = isIndeterminate
        if (indicator is ProgressIndicatorEx)
          indicator.subscribeOnCancel { taskLifetimeDef.terminate() }

        taskLifetimeDef.onTerminationIfAlive { indicator.cancel() }

        progressIndicator = indicator

        try {
          runBlocking {
            dispatcher.thread = Thread.currentThread()

            while (true)
              channel.receive().run()
          }
        }
        catch (e: ClosedReceiveChannelException) {
          // ok
        }
      }

      return CoroutineProgressContext(taskLifetimeDef, dispatcher, channel, task) { progressIndicator }
    }

    fun createBackgroundable(
      lifetime: Lifetime,
      @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
      canBeCancelled: Boolean = true,
      isIndeterminate: Boolean = true,
      project: Project? = null,
    ) = create(lifetime, isIndeterminate) { run ->
      object : Task.Backgroundable(project, title, canBeCancelled, ALWAYS_BACKGROUND) {
        override fun run(indicator: ProgressIndicator) = run(indicator)
      }
    }

    fun createModal(
      lifetime: Lifetime,
      @Nls(capitalization = Nls.Capitalization.Title) title: String,
      canBeCancelled: Boolean = true,
      isIndeterminate: Boolean = true,
      project: Project? = null,
    ) = create(lifetime, isIndeterminate) { run ->
      object : Task.Modal(project, title, canBeCancelled) {
        override fun run(indicator: ProgressIndicator) = run(indicator)
      }
    }
  }
}

@Deprecated("It is a legacy api")
abstract class ProgressCoroutineScope(
  @Deprecated("Use progress reporter api")
  val indicator: ProgressIndicator)

@Deprecated("It is a legacy api")
private class ProgressCoroutineScopeBridge private constructor(coroutineContext: CoroutineContext, val bridgeIndicator: BridgeIndicator) : ProgressCoroutineScope(bridgeIndicator) {
  companion object {

    suspend fun <T> use(isModal: Boolean, action: suspend ProgressCoroutineScope.() -> T): T {
      return coroutineScope {
        val parentScope = this
        coroutineScope {
          val bridge = ProgressCoroutineScopeBridge(coroutineContext, BridgeIndicator(coroutineContext, isModal))

          reportRawProgress { reporter ->
            bridge.bridgeIndicator.reporter = reporter

            val job = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
              awaitCancellationAndInvoke {
                if (!parentScope.isActive)
                  bridge.bridgeIndicator.cancel()
              }
            }

            try {
              bridge.action()
            }
            finally {
              job.cancel()
            }
          }
        }
      }
    }
  }
}

private interface BridgeIndicatorBase : ProgressIndicatorEx{
  var reporter:  RawProgressReporter
}

private class BridgeIndicator(val coroutineContext: CoroutineContext, private val isModalFlag: Boolean) : BridgeIndicatorBase by BridgeIndicatorEx(coroutineContext) {

  override fun getModalityState(): ModalityState = coroutineContext.contextModality() ?: ModalityState.nonModal()

  override fun isModal(): Boolean {
    return isModalFlag
  }
}

private class BridgeIndicatorEx(val coroutineContext: CoroutineContext) : AbstractProgressIndicatorExBase(), StandardProgressIndicator, BridgeIndicatorBase {
  override lateinit var reporter:  RawProgressReporter

  override fun cancel() {
    coroutineContext.cancel()
    super.cancel()
  }

  override fun setText(text: String?) {
    reporter.text(text)
    super.setText(text)
  }

  override fun setText2(text: String?) {
    reporter.details(text)
    super.setText2(text)
  }

  override fun setFraction(fraction: Double) {
    isIndeterminate = false
    reporter.fraction(fraction)
    super.setFraction(fraction)
  }

  override fun setIndeterminate(indeterminate: Boolean) {
    if (indeterminate) {
      reporter.fraction(null)
    }
    else {
      reporter.fraction(0.0)
    }

    super.setIndeterminate(indeterminate)
  }
}

@ApiStatus.Internal
@Deprecated("It is a legacy api")
class ProgressCoroutineScopeLegacy private constructor(indicator: ProgressIndicator) : ProgressCoroutineScope(indicator) {

  companion object {
    internal suspend fun <T> execute(coroutineContext: CoroutineContext, progressLifetime: Lifetime, indicator: ProgressIndicator, action: suspend ProgressCoroutineScope.() -> T): T {
      return try {
        val reporter = object : RawProgressReporter {
          override fun text(text: @ProgressText String?) {
            if (progressLifetime.isNotAlive) return

            progressLifetime.launch(coroutineContext) {
              if (text != null) indicator.text = text
            }
          }

          override fun details(details: @ProgressDetails String?) {
            if (progressLifetime.isNotAlive) return

            progressLifetime.launch(coroutineContext) {
              if (details != null) indicator.text2 = details
            }
          }

          override fun fraction(fraction: Double?) {
            if (progressLifetime.isNotAlive) return

            progressLifetime.launch(coroutineContext) {
              if (fraction != null) indicator.fraction = fraction
            }
          }
        }

        coroutineScope {
          withContext(ModalityState.defaultModalityState().asContextElement() + reporter.asContextElement()) {
            ProgressCoroutineScopeLegacy(indicator).action()
          }
        }
      }
      catch (e: ProcessCanceledException) {
        throw CancellationException(e.message, e)
      }
    }
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use progress reporter api")
  inline fun withText(@Nls(capitalization = Nls.Capitalization.Sentence) text: String, action: ProgressCoroutineScope.() -> Unit) {
    val oldText = indicator.text
    try {
      indicator.text = text
      action()
    }
    finally {
      indicator.text = oldText
    }
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use progress reporter api")
  inline fun withDetails(@Nls(capitalization = Nls.Capitalization.Sentence) text: String, action: ProgressCoroutineScope.() -> Unit) {
    val oldText = indicator.text2
    try {
      indicator.text2 = text
      action()
    }
    finally {
      indicator.text2 = oldText
    }
  }
}