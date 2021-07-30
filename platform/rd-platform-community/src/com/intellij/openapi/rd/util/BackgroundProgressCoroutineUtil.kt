// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

suspend fun <T> withBackgroundProgressContext(
  @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  project: Project? = null,
  lifetime: Lifetime = Lifetime.Eternal,
  action: suspend BackgroundProgressCoroutineScope.() -> T
) = lifetime.startUnderBackgroundProgressAsync(title, canBeCancelled, isIndeterminate, project, action).await()

fun Lifetime.launchUnderBackgroundProgress(
  @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  project: Project? = null,
  action: suspend BackgroundProgressCoroutineScope.() -> Unit
): Job {
  return runAsync(title, canBeCancelled, isIndeterminate, project) { dispatcher, indicator ->
    launch(dispatcher) { BackgroundProgressCoroutineScope(coroutineContext, this@runAsync, indicator()).action() }
  }
}

fun <T> Lifetime.startUnderBackgroundProgressAsync(
  @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  project: Project? = null,
  action: suspend BackgroundProgressCoroutineScope.() -> T
): Deferred<T> {
  return runAsync(title, canBeCancelled, isIndeterminate, project) { dispatcher, indicator ->
    startAsync(dispatcher) { BackgroundProgressCoroutineScope(coroutineContext, this@runAsync, indicator()).action() }
  }
}

private fun <T: Job> Lifetime.runAsync(
  @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
  canBeCancelled: Boolean = true,
  isIndeterminate: Boolean = true,
  project: Project? = null,
  startCoroutine: Lifetime.(CoroutineDispatcher, () -> ProgressIndicator) -> T): T {
  val channel = Channel<Runnable>(Channel.UNLIMITED)
  val dispatcher = object : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) = channel.sendBlocking(block)
  }

  val taskLifetimeDef = createNested()
  lateinit var progressIndicator: ProgressIndicator

  val task = object : Task.Backgroundable(project, title, canBeCancelled, ALWAYS_BACKGROUND) {
    override fun run(indicator: ProgressIndicator) {
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
  }

  return taskLifetimeDef.startCoroutine(dispatcher) { progressIndicator }.also {
    it.invokeOnCompletion {
      taskLifetimeDef.terminate()
      channel.close()
    }
    task.queue()
  }
}

class BackgroundProgressCoroutineScope(override val coroutineContext: CoroutineContext, val progressLifetime: Lifetime, val indicator: ProgressIndicator) : CoroutineScope {
  inline fun withTextAboveProgressBar(@Nls(capitalization = Nls.Capitalization.Sentence) text: String, action: BackgroundProgressCoroutineScope.() -> Unit) {
    val oldText = indicator.text
    try {
      indicator.text = text
      action()
    }
    finally {
      indicator.text = oldText
    }
  }

  inline fun withTextUnderProgressBar(@Nls(capitalization = Nls.Capitalization.Sentence) text: String, action: BackgroundProgressCoroutineScope.() -> Unit) {
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