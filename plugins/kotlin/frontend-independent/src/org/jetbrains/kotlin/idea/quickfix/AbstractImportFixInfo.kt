// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
object AbstractImportFixInfo {
    @Volatile
    var IGNORE_MODULE_ERROR: Boolean = false

    @TestOnly
    fun ignoreModuleError(disposable: Disposable) {
        IGNORE_MODULE_ERROR = true
        Disposer.register(disposable) { IGNORE_MODULE_ERROR = false }
    }

}