// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

@Internal
inline fun <T> runActivity(@NonNls name: String, task: () -> T): T {
  val activity = if (StartUpMeasurer.isEnabled()) StartUpMeasurer.startActivity(name) else null
  val result = task()
  activity?.end()
  return result
}