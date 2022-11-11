// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

fun showYesNoCancelDialog(
    key: String,
    project: Project,
    @NlsContexts.DialogMessage message: String,
    @NlsContexts.DialogTitle title: String,
    icon: Icon,
    default: Int?
): Int {
    return if (!isUnitTestMode()) {
        Messages.showYesNoCancelDialog(project, message, title, icon)
    } else {
        callInTestMode(key, default)
    }
}

private val dialogResults = ConcurrentHashMap<String, Any>()

@TestOnly
fun setDialogsResult(key: String, result: Any) {
    dialogResults[key] = result
}

@TestOnly
fun clearDialogsResults() {
    dialogResults.clear()
}

private fun <T : Any?> callInTestMode(key: String, default: T?): T {
    val result = dialogResults[key]
    if (result != null) {
        dialogResults.remove(key)
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    if (default != null) {
        return default
    }

    throw IllegalStateException("Can't call '$key' dialog in test mode")
}

