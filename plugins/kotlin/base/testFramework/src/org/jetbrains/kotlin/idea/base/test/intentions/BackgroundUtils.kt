// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.test.intentions

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ExecutionException

@ApiStatus.Internal
fun <T> Project.computeOnBackground(compute: () -> T): T {
    try {
        return ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable {
            compute()
        }, "compute", true, this)
    } catch (e: ExecutionException) {
        throw e.cause!!
    }
}
