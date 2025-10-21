// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.v1

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtFile

@ApiStatus.Internal
val logger: Logger = Logger.getInstance("#org.jetbrains.kotlin.idea.script")

@ApiStatus.Internal
fun scriptingDebugLog(file: KtFile, message: () -> String) {
    scriptingDebugLog(file.originalFile.virtualFile, message)
}

@ApiStatus.Internal
fun scriptingDebugLog(file: VirtualFile? = null, message: () -> String) {
    if (logger.isDebugEnabled) {
        logger.debug("[KOTLIN_SCRIPTING] ${file?.let { file.path + " " } ?: ""}" + message())
    }
}

@ApiStatus.Internal
fun scriptingInfoLog(message: String) {
    logger.info("[KOTLIN_SCRIPTING] $message")
}

@ApiStatus.Internal
fun scriptingWarnLog(message: String) {
    logger.warn("[KOTLIN_SCRIPTING] $message")
}

fun scriptingWarnLog(message: String, throwable: Throwable?) {
    logger.warn("[KOTLIN_SCRIPTING] $message", throwable)
}

fun scriptingErrorLog(message: String, throwable: Throwable?) {
    logger.error("[KOTLIN_SCRIPTING] $message", throwable)
}