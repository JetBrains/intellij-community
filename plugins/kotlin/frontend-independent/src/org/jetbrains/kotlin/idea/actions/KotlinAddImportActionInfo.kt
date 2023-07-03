// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.actions

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.quickfix.AutoImportVariant

/** Test hooks allowing inspection of data used for KotlinAddImportAction. **/
object KotlinAddImportActionInfo {
    interface ExecuteListener {
        fun onExecute(variants: List<AutoImportVariant>)
    }

    @Volatile
    var executeListener: ExecuteListener? = null

    @TestOnly
    fun setExecuteListener(disposable: Disposable, listener: ExecuteListener) {
        assert(executeListener == null)
        executeListener = listener
        Disposer.register(disposable) { executeListener = null }
    }
}