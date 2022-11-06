// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.util

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.kotlin.idea.util.application.isDispatchThread
import com.intellij.openapi.application.runReadAction

fun <T : Any> runInReadActionWithWriteActionPriorityWithPCE(f: () -> T): T =
    runInReadActionWithWriteActionPriority(f) ?: throw ProcessCanceledException()

fun <T : Any> runInReadActionWithWriteActionPriority(f: () -> T): T? {
    if (isDispatchThread()) {
        return f()
    }

    var r: T? = null
    val complete = ProgressIndicatorUtils.runInReadActionWithWriteActionPriority {
        r = f()
    }

    if (!complete) return null
    return r!!
}

fun <T : Any> Project.runSynchronouslyWithProgress(@NlsContexts.ProgressTitle progressTitle: String, canBeCanceled: Boolean, action: () -> T): T? {
    var result: T? = null
    ProgressManager.getInstance().runProcessWithProgressSynchronously({ result = action() }, progressTitle, canBeCanceled, this)
    return result
}

fun <T : Any> Project.runSynchronouslyWithProgressIfEdt(@NlsContexts.ProgressTitle progressTitle: String, canBeCanceled: Boolean, action: () -> T): T? =
    if (isDispatchThread()) {
        runSynchronouslyWithProgress(progressTitle, canBeCanceled) {
            runReadAction { action() }
        }
    } else {
        action()
    }