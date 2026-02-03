// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("KotlinPluginDisposableUtils")

package org.jetbrains.kotlin.idea.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.PROJECT)
class KotlinPluginDisposable(val coroutineScope: CoroutineScope) : Disposable {
    @Volatile
    var disposed: Boolean = false

    companion object {
        @JvmStatic
        fun getInstance(project: Project): KotlinPluginDisposable = project.service<KotlinPluginDisposable>()
    }

    override fun dispose() {
        disposed = true
    }
}

@ApiStatus.Internal
fun <T> syncNonBlockingReadAction(project: Project, task: () -> T): T {
    return ReadAction.nonBlocking<T> { task() }
        .expireWith(KotlinPluginDisposable.getInstance(project))
        .executeSynchronously()
}