// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

inline fun <T> Activity?.runChild(name: String, task: () -> T): T {
  val activity = this?.startChild(name)
  val result = task()
  activity?.end()
  return result
}

inline fun <T> runActivity(@NonNls name: String, category: ActivityCategory = ActivityCategory.DEFAULT, task: () -> T): T {
  val activity = if (StartUpMeasurer.isEnabled()) StartUpMeasurer.startActivity(name, category) else null
  val result = task()
  activity?.end()
  return result
}

@Internal
inline fun CoroutineScope.launchAndMeasure(
  activityName: String,
  context: CoroutineContext = EmptyCoroutineContext,
  crossinline block: suspend CoroutineScope.() -> Unit
): Job {
  return launch(context) {
    runActivity(activityName) {
      block()
    }
  }
}