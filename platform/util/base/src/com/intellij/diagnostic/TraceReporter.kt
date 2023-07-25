// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface TraceReporter {
  fun start(coroutineName: String, scheduleTime: Long, parentActivity: ActivityImpl?): ActivityImpl

  fun end(activity: ActivityImpl, coroutineName: String, end: Long, lastSuspensionTime: Long)
}