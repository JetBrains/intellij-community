// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project

fun <T> Project.runReadActionInSmartMode(action: () -> T): T {
    if (ApplicationManager.getApplication().isReadAccessAllowed) return action()
    return DumbService.getInstance(this).runReadActionInSmartMode<T>(action)
}

fun <T> Project.runWithAlternativeResolveEnabled(action: () -> T): T {
    @Suppress("UNCHECKED_CAST") var result: T = null as T
    DumbService.getInstance(this).withAlternativeResolveEnabled { result = action() }
    @Suppress("USELESS_CAST")
    return result as T
}

fun Project.runWhenSmart(action: () -> Unit) {
    DumbService.getInstance(this).runWhenSmart(action)
}