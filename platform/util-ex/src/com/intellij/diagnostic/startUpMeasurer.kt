// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

inline fun <T> Activity?.runChild(name: String, task: () -> T): T {
  val activity = this?.startChild(name)
  val result = task()
  activity?.end()
  return result
}

inline fun <T> runMainActivity(name: String, task: () -> T) = runActivity(name, ActivityCategory.MAIN, task)

inline fun <T> runActivity(name: String, category: ActivityCategory = ActivityCategory.APP_INIT, task: () -> T): T {
  val activity = createActivity(name, category)
  val result = task()
  activity.end()
  return result
}

@PublishedApi
internal fun createActivity(name: String, category: ActivityCategory): Activity {
  return when (category) {
    ActivityCategory.MAIN -> StartUpMeasurer.startMainActivity(name)
    else -> StartUpMeasurer.startActivity(name, ActivityCategory.APP_INIT)
  }
}