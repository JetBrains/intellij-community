// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import java.util.concurrent.Callable

internal inline fun <R> nonBlocking(project: Project, crossinline block: () -> R, crossinline uiContinuation: (R) -> Unit) {
    if (isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment) {
        val result = block()
        uiContinuation(result)
    } else {
        ReadAction.nonBlocking(Callable {
            block()
        })
            .inSmartMode(project)
            .expireWith(KotlinPluginDisposable.getInstance(project))
            .finishOnUiThread(ModalityState.current()) { result ->
                uiContinuation(result)
            }.submit(AppExecutorUtil.getAppExecutorService())
    }
}