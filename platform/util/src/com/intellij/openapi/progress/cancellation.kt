// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Internal

package com.intellij.openapi.progress

import com.intellij.openapi.util.ThrowableComputable
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.coroutineContext

fun <X> withJob(job: Job, action: () -> X): X = Cancellation.withJob(job, ThrowableComputable(action))

suspend fun <X> withJob(action: () -> X): X = withJob(coroutineContext.job, action)
