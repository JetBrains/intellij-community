// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.vfs.newvfs.events.VFileEvent

fun interface ApplicationVFileEventsTracker {
  fun interface VFileEventTracker {
    fun completeEventTracking()
  }
  fun trackEvent(event: VFileEvent): VFileEventTracker
}