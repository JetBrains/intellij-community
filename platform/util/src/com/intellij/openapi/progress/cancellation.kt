// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress

import com.intellij.openapi.util.ThrowableComputable
import kotlinx.coroutines.Job

fun <X> withJob(job: Job, action: () -> X): X = Cancellation.withJob(job, ThrowableComputable(action))
