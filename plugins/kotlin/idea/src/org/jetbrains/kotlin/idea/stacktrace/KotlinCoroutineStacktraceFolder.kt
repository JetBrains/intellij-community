// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stacktrace

import com.intellij.execution.ConsoleFolding
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.KotlinBundle

class KotlinCoroutineStacktraceFolder: ConsoleFolding() {
    override fun shouldBeAttachedToThePreviousLine(): Boolean {
        return false
    }

    override fun getPlaceholderText(project: Project, lines: MutableList<String>): String? {
        return KotlinBundle.message("coroutines.stacktrace.folded.label", lines.size)
    }

    override fun shouldFoldLine(project: Project, line: String): Boolean {
        return line.contains(COROUTINES_STACKTRACE_PREFIX)
    }

    companion object {
        internal const val COROUTINES_STACKTRACE_PREFIX: @NonNls String = "at kotlinx.coroutines."
    }
}