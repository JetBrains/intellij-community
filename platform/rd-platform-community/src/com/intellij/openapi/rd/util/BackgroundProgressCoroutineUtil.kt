package com.intellij.openapi.rd.util

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.contextModality
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.impl.TextDetailsProgressReporter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.util.awaitCancellationAndInvoke
import com.jetbrains.rd.framework.util.launch
import com.jetbrains.rd.framework.util.startAsync
import com.jetbrains.rd.framework.util.startChildAsync
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.isNotAlive
import com.jetbrains.rd.util.reactive.WriteOnceProperty
import com.jetbrains.rd.util.reactive.adviseOnce
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.trySendBlocking
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.concurrent.atomic.AtomicBoolean
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
  val context = CoroutineProgressContext.createModal(lifetime, title, canBeCancelled, isIndeterminate, project)
  return doRunUnderProgress(context, action)
}

@Deprecated("Use withBackgroundProgress")
suspend fun <T> withBackgroundProgressContext(
  @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
  canBeCancelled: Boolean = true,
  project: Project,
  action: suspend ProgressCoroutineScope.() -> T
): T = withBackgroundProgressContext(title, canBeCancelled, true, project, Lifetime.Eternal, action)

@Deprecated("Use withBackgroundProgress")
suspend fun <T> withBackgroundProgressContext(
  @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  project: Project? = null,
  lifetime: Lifetime = Lifetime.Eternal,
  action: suspend ProgressCoroutineScope.() -> T
): T {
  if (project != null) {
    withBackgroundProgress(project, title, canBeCancelled) {
      withBackgroundContext(lifetime) {
        ProgressCoroutineScopeBridge.use(false, action)
      }
    }
  }

  val context = CoroutineProgressContext.createBackgroundable(lifetime, title, canBeCancelled, isIndeterminate, project)
  return doRunUnderProgress(context, action)
}

fun Lifetime.launchUnderModalProgress(
  @Nls(capitalization = Nls.Capitalization.Title) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  project: Project? = null,
  action: suspend ProgressCoroutineScope.() -> Unit
): Job {
  return runModalAsync(title, canBeCancelled, isIndeterminate, project) { dispatcher, indicator ->
    launch(dispatcher) { ProgressCoroutineScopeLegacy.execute(coroutineContext, this@runModalAsync, indicator(), action) }
  }
}

@Deprecated("Use launchWithBackgroundProgress")
fun Lifetime.launchUnderBackgroundProgress(
  @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  project: Project? = null,
  action: suspend ProgressCoroutineScope.() -> Unit
): Job {

  if (project != null)
    return launchBackground { withBackgroundProgressContext(title, canBeCancelled, project, action) }

  return runBackgroundAsync(title, canBeCancelled, isIndeterminate, project) { dispatcher, indicator ->
    launch(dispatcher) { ProgressCoroutineScopeLegacy.execute(coroutineContext, this@runBackgroundAsync, indicator(), action) }
  }
}

fun <T> Lifetime.startUnderModalProgressAsync(
  @Nls(capitalization = Nls.Capitalization.Title) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  project: Project? = null,
  action: suspend ProgressCoroutineScope.() -> T
): Deferred<T> {
  return runModalAsync(title, canBeCancelled, isIndeterminate, project) { dispatcher, indicator ->
    startAsync(dispatcher) { ProgressCoroutineScopeLegacy.execute(coroutineContext, this@runModalAsync, indicator(), action) }
  }
}

@Deprecated("Use startWithBackgroundProgressAsync")
fun <T> Lifetime.startUnderBackgroundProgressAsync(
  @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  project: Project? = null,
  action: suspend ProgressCoroutineScope.() -> T
): Deferred<T> {

  if (project != null)
    return startBackgroundAsync { withBackgroundProgressContext(title, canBeCancelled, project, action) }

  return runBackgroundAsync(title, canBeCancelled, isIndeterminate, project) { dispatcher, indicator ->
    startAsync(dispatcher) { ProgressCoroutineScopeLegacy.execute(coroutineContext, this@runBackgroundAsync, indicator(), action) }
  }
}

private fun <T: Job> Lifetime.runModalAsync(
  @Nls(capitalization = Nls.Capitalization.Title) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  project: Project? = null,
  startCoroutine: Lifetime.(CoroutineDispatcher, () -> ProgressIndicator) -> T): T {
  val context = CoroutineProgressContext.createModal(this, title, canBeCancelled, isIndeterminate, project)
  return doRunUnderProgressAsync(context, startCoroutine) }

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
  override val coroutineContext: CoroutineContext,
  @Deprecated("Use progress reporter api")
  val indicator: ProgressIndicator) : CoroutineScope

@Deprecated("It is a legacy api")
private class ProgressCoroutineScopeBridge private constructor(coroutineContext: CoroutineContext, val bridgeIndicator: BridgeIndicator) : ProgressCoroutineScope(coroutineContext, bridgeIndicator) {
  companion object {

    suspend fun <T> use(isModal: Boolean, action: suspend ProgressCoroutineScope.() -> T): T {
      return coroutineScope {
        val parentScope = this
        coroutineScope {
          val bridge = ProgressCoroutineScopeBridge(coroutineContext, BridgeIndicator(coroutineContext, isModal))
          try {
            bridge.initializationTask.await()
            assert(bridge.bridgeIndicator.reporterIsSet)
            bridge.action()
          }
          finally {
            bridge.close(!parentScope.isActive)
          }
        }
      }
    }
  }

  private val needToCancel = AtomicBoolean(true)
  val initializationTask = CompletableDeferred<Unit>()

  fun close(needToCancelFlag: Boolean) {
    needToCancel.set(needToCancelFlag)
    job.cancel()
  }

  private val job = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
    withRawProgressReporter {
      bridgeIndicator.reporter = rawProgressReporter!!
      initializationTask.complete(Unit)
      awaitCancellationAndInvoke {
        if (needToCancel.get())
          bridgeIndicator.cancel()
      }
    }
  }
}

class BridgeIndicator(val coroutineContext: CoroutineContext, private val isModalFlag: Boolean) : EmptyProgressIndicatorBase(coroutineContext.contextModality() ?: ModalityState.nonModal()), StandardProgressIndicator {

  private var isCancelledFlag = false
  lateinit var reporter:  RawProgressReporter

  val reporterIsSet get() = this::reporter.isInitialized

  override fun isModal(): Boolean {
    return isModalFlag
  }

  override fun cancel() {
    isCancelledFlag = true
    coroutineContext.cancel()
  }

  override fun isCanceled(): Boolean {
    return isCancelledFlag
  }

  override fun setText(text: String?) {
    reporter.text(text)
  }

  override fun setText2(text: String?) {
    reporter.details(text)
  }

  override fun setFraction(fraction: Double) {
    reporter.fraction(fraction)
  }

  override fun setIndeterminate(indeterminate: Boolean) {
    if (indeterminate) {
      reporter.fraction(null)
    }
    else {
      reporter.fraction(0.0)
    }
  }
}

@Deprecated("It is a legacy api")
class ProgressCoroutineScopeLegacy private constructor(coroutineContext: CoroutineContext, indicator: ProgressIndicator) : ProgressCoroutineScope(coroutineContext, indicator) {

  companion object {
    internal suspend fun <T> execute(coroutineContext: CoroutineContext, progressLifetime: Lifetime, indicator: ProgressIndicator, action: suspend ProgressCoroutineScope.() -> T): T {
      return try {
        val sink = object : ProgressSink {
          override fun update(text: @NlsContexts.ProgressText String?, details: @NlsContexts.ProgressDetails String?, fraction: Double?) {
            if (progressLifetime.isNotAlive) return

            progressLifetime.launch(coroutineContext) {
              if (text != null) indicator.text = text
              if (details != null) indicator.text2 = details
              if (fraction != null) indicator.fraction = fraction
            }
          }
        }

        coroutineScope {
          TextDetailsProgressReporter(this).use { reporter ->
            withContext(ModalityState.defaultModalityState().asContextElement() + sink.asContextElement() + reporter.asContextElement()) {
              ProgressCoroutineScopeLegacy(this.coroutineContext, indicator).action()
            }
          }
        }
      }
      catch (e: ProcessCanceledException) {
        throw CancellationException(e.message, e)
      }
    }
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use withText", ReplaceWith("withText(text, action)"))
  inline fun withTextAboveProgressBar(@Nls(capitalization = Nls.Capitalization.Sentence) text: String, action: ProgressCoroutineScope.() -> Unit) {
    withText(text, action)
  }

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
  @Deprecated("Use withDetails", ReplaceWith("withDetails(text, action)"))
  inline fun withTextUnderProgressBar(@Nls(capitalization = Nls.Capitalization.Sentence) text: String, action: ProgressCoroutineScope.() -> Unit) {
    withDetails(text, action)
  }

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