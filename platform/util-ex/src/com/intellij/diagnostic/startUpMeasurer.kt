// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import org.jetbrains.annotations.NonNls

inline fun <T> Activity?.runChild(name: String, task: () -> T): T {
  val activity = this?.startChild(name)
  val result = task()
  activity?.end()
  return result
}

inline fun <T> runActivity(@NonNls name: String, category: ActivityCategory = ActivityCategory.DEFAULT, task: () -> T): T {
  val activity = StartUpMeasurer.startActivity(name, category)
  val result = task()
  activity.end()
  return result
}