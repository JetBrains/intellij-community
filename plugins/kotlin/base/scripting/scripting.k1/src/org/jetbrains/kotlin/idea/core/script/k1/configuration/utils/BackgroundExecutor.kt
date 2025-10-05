// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k1.configuration.utils

import com.intellij.openapi.vfs.VirtualFile

interface BackgroundExecutor {
    fun ensureScheduled(key: VirtualFile, actions: () -> Unit)
}