// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.time.Duration.Companion.milliseconds

@Experimental
@Internal
@OptIn(FlowPreview::class)
fun singleAlarm(debounce: Int, coroutineScope: CoroutineScope, runnable: Runnable): MutableSharedFlow<Unit> {
  val requests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  coroutineScope.launch {
    requests
      .debounce(debounce.milliseconds)
      .collectLatest {
        runnable.run()
      }
  }
  return requests
}
