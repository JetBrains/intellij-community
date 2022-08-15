// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)

package com.intellij.diagnostic

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Put this into coroutine context to enable [CoroutineName]-based time measuring of this coroutine and its children.
 *
 * Example:
 * ```
 * runBlocking(rootTask()) {
 *   launch(CoroutineName("task 1")) {
 *     ...
 *   }
 *   val stuff = async(CoroutineName("init stuff") {
 *     ...
 *   }
 * }
 * ```
 */
fun rootTask(): CoroutineContext = MeasureCoroutineTime

/**
 * This function forces child activity for [withContext] blocks,
 * which don't fork [CopyableThreadContextElement] under normal conditions.
 *
 * Example:
 * ```
 * launch(CoroutineName("init stuff")) {
 *   ...
 *   withContext(subtask("init stuff step 1") {
 *     ...
 *   }
 *   ...
 *   withContext(Dispatchers.EDT + subtask("init stuff step 2") {
 *     ...
 *   }
 *   ...
 * }
 */
@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
suspend fun subtask(name: String): CoroutineContext {
  val measurer = coroutineContext[CoroutineTimeMeasurerKey]
  val childMeasurer = measurer?.copyForChild() ?: EmptyCoroutineContext
  return CoroutineName(name) + childMeasurer
}

private object CoroutineTimeMeasurerKey : CoroutineContext.Key<CoroutineTimeMeasurer>

private val noActivity: Activity = object : Activity {
  override fun end(): Unit = error("must not be invoked")
  override fun setDescription(description: String): Unit = error("must not be invoked")
  override fun endAndStart(name: String): Activity = error("must not be invoked")
  override fun startChild(name: String): Activity = error("must not be invoked")
  override fun updateThreadName(): Unit = error("must not be invoked")
}

private object MeasureCoroutineTime : CopyableThreadContextElement<Unit> {

  override val key: CoroutineContext.Key<*> get() = CoroutineTimeMeasurerKey

  override fun updateThreadContext(context: CoroutineContext): Unit = error("must not be invoked")
  override fun restoreThreadContext(context: CoroutineContext, oldState: Unit): Unit = error("must not be invoked")
  override fun copyForChild(): CopyableThreadContextElement<Unit> = CoroutineTimeMeasurer(null)
  override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext = error("must not be invoked")

  override fun toString(): String = "MeasureCoroutineTime"
}

private class CoroutineTimeMeasurer(
  private val parentActivity: ActivityImpl?,
) : CopyableThreadContextElement<Unit> {

  override val key: CoroutineContext.Key<*> get() = CoroutineTimeMeasurerKey

  private val creationTime: Long = StartUpMeasurer.getCurrentTime()
  private val currentActivity: AtomicReference<Activity> = AtomicReference(noActivity)

  @Volatile
  private var lastSuspensionTime: Long = -1

  override fun toString(): String {
    val activity = currentActivity.get()
    return when {
      activity != null -> (activity as ActivityImpl).name
      parentActivity != null -> "uninitialized child of ${parentActivity.name}"
      else -> "uninitialized"
    }
  }

  override fun updateThreadContext(context: CoroutineContext) {
    if (currentActivity.get() != noActivity) {
      // already initialized
      return
    }
    val coroutineName = context[CoroutineName]?.name
    if (parentActivity != null) {
      if (coroutineName == null || parentActivity.name == coroutineName) {
        // name was not specified for child => propagate parent activity
        currentActivity.set(parentActivity)
        return
      }
    }
    if (coroutineName == null) {
      // no parent and no name => don't report
      currentActivity.set(noActivity)
      return
    }
    // report time between scheduling and start of the execution
    val activity = ActivityImpl("${coroutineName}: scheduled", creationTime, parentActivity)
      // start main activity right away
      .endAndStart(coroutineName)
    currentActivity.set(activity)

    val job = context.job
    job.invokeOnCompletion {
      // After the last suspension, the coroutine was waiting for its children => end main activity
      activity.endAndStart(lastSuspensionTime, "${coroutineName}: completing")
        // This invokeOnCompletion handler is executed after all children are completed => end 'completing' activity
        .end()
    }
  }

  override fun restoreThreadContext(context: CoroutineContext, oldState: Unit) {
    lastSuspensionTime = StartUpMeasurer.getCurrentTime()
  }

  override fun copyForChild(): CopyableThreadContextElement<Unit> {
    val activity = checkNotNull(currentActivity.get()) {
      "updateThreadContext was never called"
    }
    if (activity == noActivity) {
      return CoroutineTimeMeasurer(null)
    }
    else {
      return CoroutineTimeMeasurer(activity as ActivityImpl)
    }
  }

  override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext {
    return overwritingElement
  }
}
