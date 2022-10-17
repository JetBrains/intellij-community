package com.intellij.openapi.rd.util

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.jetbrains.rd.framework.util.launch
import com.jetbrains.rd.framework.util.startAsync
import com.jetbrains.rd.framework.util.startChildAsync
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.isNotAlive
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.jetbrains.annotations.Nls
import java.lang.Runnable
import kotlin.coroutines.CoroutineContext

suspend fun <T> withModalProgressContext(
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

suspend fun <T> withBackgroundProgressContext(
  @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  project: Project? = null,
  lifetime: Lifetime = Lifetime.Eternal,
  action: suspend ProgressCoroutineScope.() -> T): T {
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
    launch(dispatcher) { ProgressCoroutineScope(coroutineContext, this@runModalAsync, indicator()).execute(action) }
  }
}

fun Lifetime.launchUnderBackgroundProgress(
  @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  project: Project? = null,
  action: suspend ProgressCoroutineScope.() -> Unit
): Job {
  return runBackgroundAsync(title, canBeCancelled, isIndeterminate, project) { dispatcher, indicator ->
    launch(dispatcher) { ProgressCoroutineScope(coroutineContext, this@runBackgroundAsync, indicator()).execute(action) }
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
    startAsync(dispatcher) { ProgressCoroutineScope(coroutineContext, this@runModalAsync, indicator()).execute(action) }
  }
}

fun <T> Lifetime.startUnderBackgroundProgressAsync(
  @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  project: Project? = null,
  action: suspend ProgressCoroutineScope.() -> T
): Deferred<T> {
  return runBackgroundAsync(title, canBeCancelled, isIndeterminate, project) { dispatcher, indicator ->
    startAsync(dispatcher) { ProgressCoroutineScope(coroutineContext, this@runBackgroundAsync, indicator()).execute(action) }
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
        ProgressCoroutineScope(coroutineContext, context.lifetime, indicator()).execute(runCoroutine)
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

class ProgressCoroutineScope(override val coroutineContext: CoroutineContext, val progressLifetime: Lifetime, val indicator: ProgressIndicator) : CoroutineScope {

  private val sink = object : ProgressSink {
    override fun update(text: @NlsContexts.ProgressText String?, details: @NlsContexts.ProgressDetails String?, fraction: Double?) {
      if (progressLifetime.isNotAlive) return

      progressLifetime.launch(coroutineContext) {
        if (text != null) indicator.text = text
        if (details != null) indicator.text2 = details
        if (fraction != null) indicator.fraction = fraction
      }
    }
  }

  internal suspend fun <T> execute(action: suspend ProgressCoroutineScope.() -> T): T {
    return try {
      withContext(ModalityState.defaultModalityState().asContextElement() + sink.asContextElement()) {
        action()
      }
    }
    catch (e: ProcessCanceledException) {
      throw CancellationException(e.message, e)
    }
  }

  @Deprecated("Use withText", ReplaceWith("withText(text, action)"))
  inline fun withTextAboveProgressBar(@Nls(capitalization = Nls.Capitalization.Sentence) text: String, action: ProgressCoroutineScope.() -> Unit) {
    withText(text, action)
  }

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

  @Deprecated("Use withDetails", ReplaceWith("withDetails(text, action)"))
  inline fun withTextUnderProgressBar(@Nls(capitalization = Nls.Capitalization.Sentence) text: String, action: ProgressCoroutineScope.() -> Unit) {
    withDetails(text, action)
  }

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