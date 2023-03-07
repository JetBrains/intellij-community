// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.openapi.vfs.newvfs.events.VFileEvent

interface VFileEventApplicationListener {
  fun beforeApply(event: VFileEvent) {}
  fun afterApply(event: VFileEvent, throwable: Throwable?) {}
}