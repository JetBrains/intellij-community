// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.util.Computable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.ScheduledForRemoval
@Deprecated("Use 'com.intellij.openapi.progress.util.BackgroundTaskUtil' instead")
object ProgressIndicatorUtils {
    @ApiStatus.ScheduledForRemoval
    @Deprecated(
        "Use 'com.intellij.openapi.progress.util.BackgroundTaskUtil.runUnderDisposeAwareIndicator()' instead",
        ReplaceWith(
            "BackgroundTaskUtil.runUnderDisposeAwareIndicator(parent, Computable { computable() })",
            imports = ["com.intellij.openapi.progress.util.BackgroundTaskUtil", "com.intellij.openapi.util.Computable"]
        )
    )
    fun <T> runUnderDisposeAwareIndicator(parent: Disposable, computable: () -> T): T {
        return BackgroundTaskUtil.runUnderDisposeAwareIndicator(parent, Computable { computable() })
    }
}