// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("DumbModeUtils")

package org.jetbrains.kotlin.idea.base.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import org.jetbrains.annotations.ApiStatus

val Project.isInDumbMode: Boolean
    @ApiStatus.Internal get() = DumbService.getInstance(this).isDumb

@ApiStatus.Internal
fun <T> Project.runReadActionInSmartMode(action: () -> T): T {
    if (ApplicationManager.getApplication().isReadAccessAllowed) return action()
    return DumbService.getInstance(this).runReadActionInSmartMode(Computable(action))
}

@ApiStatus.Internal
fun <T> Project.runWithAlternativeResolveEnabled(action: () -> T): T {
    @Suppress("UNCHECKED_CAST") var result: T = null as T
    DumbService.getInstance(this).withAlternativeResolveEnabled { result = action() }
    @Suppress("USELESS_CAST")
    return result as T
}

@ApiStatus.Internal
fun Project.runWhenSmart(action: () -> Unit) {
    DumbService.getInstance(this).runWhenSmart(action)
}