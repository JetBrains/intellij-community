// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction

fun <R> executeOnPooledThreadInReadAction(action: () -> R): R? {
    var exception: Exception? = null
    val result = ApplicationManager.getApplication().executeOnPooledThread<R> {
        try {
            runReadAction(action)
        } catch (e: Exception) {
            exception = e
            null
        }
    }.get()

    exception?.run {
        throw this
    }
    return result
}