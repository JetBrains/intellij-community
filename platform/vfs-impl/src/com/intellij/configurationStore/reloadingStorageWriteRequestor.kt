// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal

package com.intellij.configurationStore

import org.jetbrains.annotations.ApiStatus.Internal

/** Used in constructed configuration store events to trigger VFS content reloading for files updated via NIO. */
@Internal
@JvmField
val RELOADING_STORAGE_WRITE_REQUESTOR: StorageManagerFileWriteRequestor = object : StorageManagerFileWriteRequestor { }
