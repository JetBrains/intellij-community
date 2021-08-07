// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress

import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class JobProgress(internal val job: Job) : Progress {

  override fun isCancelled(): Boolean = job.isCancelled

  override fun checkCancelled(): Unit = job.ensureActive()
}
