// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.scratch.actions

import org.jetbrains.kotlin.idea.core.script.scratch.KotlinScratchExecutor
import org.jetbrains.kotlin.idea.core.script.scratch.KotlinScratchFile

object ScratchCompilationSupport {
    private data class FileExecutor(val file: KotlinScratchFile, val executor: KotlinScratchExecutor)
    @Volatile
    private var fileExecutor: FileExecutor? = null

    fun isInProgress(file: KotlinScratchFile): Boolean = fileExecutor?.file == file
    fun isAnyInProgress(): Boolean = fileExecutor != null

    fun start(file: KotlinScratchFile, executor: KotlinScratchExecutor) {
        fileExecutor = FileExecutor(file, executor)
    }

    fun stop() {
        fileExecutor = null
    }

    fun forceStop() {
        fileExecutor?.executor?.stop()

        stop()
    }
}