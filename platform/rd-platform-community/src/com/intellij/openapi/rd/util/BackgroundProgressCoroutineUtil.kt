package com.intellij.openapi.rd.util

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.jetbrains.rd.framework.util.launch
import com.jetbrains.rd.framework.util.startAsync
import com.jetbrains.rd.util.lifetime.Lifetime
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.sendBlocking
import org.jetbrains.annotations.Nls
import java.lang.Runnable
import kotlin.coroutines.CoroutineContext

suspend fun <T> withModalProgressContext(
  @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  project: Project? = null,
  lifetime: Lifetime = Lifetime.Eternal,
  action: suspend ProgressCoroutineScope.() -> T
) = lifetime.startUnderModalProgressAsync(title, canBeCancelled, isIndeterminate, project, action).await()

suspend fun <T> withBackgroundProgressContext(
  @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  project: Project? = null,
  lifetime: Lifetime = Lifetime.Eternal,
  action: suspend ProgressCoroutineScope.() -> T
) = lifetime.startUnderBackgroundProgressAsync(title, canBeCancelled, isIndeterminate, project, action).await()

fun Lifetime.launchUnderModalProgress(
  @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  project: Project? = null,
  action: suspend ProgressCoroutineScope.() -> Unit
): Job {
  return runModalAsync(title, canBeCancelled, isIndeterminate, project) { dispatcher, indicator ->
    launch(dispatcher) { ProgressCoroutineScope(coroutineContext, this@runModalAsync, indicator()).action() }
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
    launch(dispatcher) { ProgressCoroutineScope(coroutineContext, this@runBackgroundAsync, indicator()).action() }
  }
}

fun <T> Lifetime.startUnderModalProgressAsync(
  @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  project: Project? = null,
  action: suspend ProgressCoroutineScope.() -> T
): Deferred<T> {
  return runModalAsync(title, canBeCancelled, isIndeterminate, project) { dispatcher, indicator ->
    startAsync(dispatcher) { ProgressCoroutineScope(coroutineContext, this@runModalAsync, indicator()).action() }
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
    startAsync(dispatcher) { ProgressCoroutineScope(coroutineContext, this@runBackgroundAsync, indicator()).action() }
  }
}

private fun <T: Job> Lifetime.runModalAsync(
  @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  project: Project? = null,
  startCoroutine: Lifetime.(CoroutineDispatcher, () -> ProgressIndicator) -> T): T {
  return runAsync(isIndeterminate, startCoroutine) { run ->
    object : Task.Modal(project, title, canBeCancelled) {
      override fun run(indicator: ProgressIndicator) = run(indicator)
    }
  }
}

private fun <T: Job> Lifetime.runBackgroundAsync(
  @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  project: Project? = null,
  startCoroutine: Lifetime.(CoroutineDispatcher, () -> ProgressIndicator) -> T): T {
  return runAsync(isIndeterminate, startCoroutine) { run ->
    object : Task.Backgroundable(project, title, canBeCancelled, ALWAYS_BACKGROUND) {
      override fun run(indicator: ProgressIndicator) = run(indicator)
    }
  }
}

private fun <T: Job> Lifetime.runAsync(
  isIndeterminate: Boolean = true,
  startCoroutine: Lifetime.(CoroutineDispatcher, () -> ProgressIndicator) -> T,
  createTask: (run: (ProgressIndicator) -> Unit) -> Task): T {
  val channel = Channel<Runnable>(Channel.UNLIMITED)
  val dispatcher = object : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) = channel.sendBlocking(block)
  }

  val taskLifetimeDef = createNested()
  lateinit var progressIndicator: ProgressIndicator

  val task = createTask { indicator ->
    indicator.isIndeterminate = isIndeterminate
    if (indicator is ProgressIndicatorEx)
      indicator.subscribeOnCancel { taskLifetimeDef.terminate() }

    progressIndicator = indicator

    try {
      runBlocking {
        while (true)
          channel.receive().run()
      }
    } catch (e: ClosedReceiveChannelException) {
      // ok
    }
  }

  return taskLifetimeDef.startCoroutine(dispatcher) { progressIndicator }.also {
    it.invokeOnCompletion {
      taskLifetimeDef.terminate()
      channel.close()
    }
    task.queue()
  }
}

class ProgressCoroutineScope(override val coroutineContext: CoroutineContext, val progressLifetime: Lifetime, val indicator: ProgressIndicator) : CoroutineScope {
  inline fun withTextAboveProgressBar(@Nls(capitalization = Nls.Capitalization.Sentence) text: String, action: ProgressCoroutineScope.() -> Unit) {
    val oldText = indicator.text
    try {
      indicator.text = text
      action()
    }
    finally {
      indicator.text = oldText
    }
  }

  inline fun withTextUnderProgressBar(@Nls(capitalization = Nls.Capitalization.Sentence) text: String, action: ProgressCoroutineScope.() -> Unit) {
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