// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

inline fun <T> Activity?.runChild(name: String, task: () -> T): T {
  val activity = this?.startChild(name)
  val result = task()
  activity?.end()
  return result
}

inline fun <T> ParallelActivity?.run(name: String, task: () ->T): T {
  val activity = this?.start(name)
  val result = task()
  activity?.end()
  return result
}

inline fun <T> runActivity(name: String, task: () -> T): T {
  val activity = StartUpMeasurer.start(name)
  val result = task()
  activity.end()
  return result
}