// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.backend

import com.intellij.platform.structureView.impl.ShowStructurePopupRequest
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

internal class BackendInitiatedStructurePopupService {
  private val showPopupRequestFlow by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    MutableSharedFlow<ShowStructurePopupRequest>(
      extraBufferCapacity = 1,
      onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
  }

  fun getShowPopupRequestFlow(): Flow<ShowStructurePopupRequest> {
    return showPopupRequestFlow
  }

  suspend fun emitShowPopupRequest(request: ShowStructurePopupRequest) {
    showPopupRequestFlow.emit(request)
  }
}
