// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class RwLockHolder(writeThread: Thread) {
  @JvmField
  internal val lock: ReadMostlyRWLock = ReadMostlyRWLock(writeThread)
}