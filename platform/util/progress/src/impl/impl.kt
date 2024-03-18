// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.util.progress.StepState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal val LOG: Logger = Logger.getInstance("#com.intellij.platform.util.progress.impl")

internal val initialState: StepState = StepState(null, null, null)

internal val doneState: StepState = StepState(1.0, null, null)

internal fun Flow<StepState>.pushTextToDetails(text: ProgressText): Flow<StepState> = map {
  it.copy(
    text = text,
    details = it.text,
  )
}

internal typealias ProgressText = @com.intellij.openapi.util.NlsContexts.ProgressText String

internal typealias ScopedLambda<T> = suspend CoroutineScope.() -> T
