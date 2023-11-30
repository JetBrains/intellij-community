// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("DeprecatedCallableAddReplaceWith")

package org.jetbrains.kotlin.idea.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.ScheduledForRemoval
@Deprecated("Use org.jetbrains.kotlin.idea.base.util.runReadActionInSmartMode()")
fun <T> Project.runReadActionInSmartMode(action: () -> T): T {
    if (ApplicationManager.getApplication().isReadAccessAllowed) return action()
    return DumbService.getInstance(this).runReadActionInSmartMode<T>(action)
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use org.jetbrains.kotlin.idea.base.util.runWithAlternativeResolveEnabled()")
fun <T> Project.runWithAlternativeResolveEnabled(action: () -> T): T {
    @Suppress("UNCHECKED_CAST") var result: T = null as T
    DumbService.getInstance(this).withAlternativeResolveEnabled { result = action() }
    @Suppress("USELESS_CAST")
    return result as T
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use org.jetbrains.kotlin.idea.base.util.runWhenSmart()")
fun Project.runWhenSmart(action: () -> Unit) {
    DumbService.getInstance(this).runWhenSmart(action)
}