// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.script

import com.intellij.openapi.application.Application
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.configuration.cache.ScriptConfigurationSnapshot
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty
import kotlin.script.experimental.api.ScriptDiagnostic

@set: org.jetbrains.annotations.TestOnly
var Application.isScriptChangesNotifierDisabled by NotNullableUserDataProperty(
    Key.create("SCRIPT_CHANGES_NOTIFIER_DISABLED"),
    true
)

internal val logger = Logger.getInstance("#org.jetbrains.kotlin.idea.script")

fun scriptingDebugLog(file: KtFile, message: () -> String) {
    scriptingDebugLog(file.originalFile.virtualFile, message)
}

fun scriptingDebugLog(file: VirtualFile? = null, message: () -> String) {
    if (logger.isDebugEnabled) {
        logger.debug("[KOTLIN_SCRIPTING] ${file?.let { file.path + " "} ?: ""}" + message())
    }
}

fun scriptingInfoLog(message: String) {
    logger.info("[KOTLIN_SCRIPTING] $message")
}

fun scriptingWarnLog(message: String, throwable: Throwable? = null) {
    logger.warn("[KOTLIN_SCRIPTING] $message", throwable)
}

fun scriptingWarnLog(message: String, throwable: Throwable?) {
    logger.warn("[KOTLIN_SCRIPTING] $message", throwable)
}

fun scriptingErrorLog(message: String, throwable: Throwable?) {
    logger.error("[KOTLIN_SCRIPTING] $message", throwable)
}

fun logScriptingConfigurationErrors(file: VirtualFile, snapshot: ScriptConfigurationSnapshot) {
    if (snapshot.configuration == null) {
        scriptingWarnLog("Script configuration for file $file was not loaded")
        for (report in snapshot.reports) {
            if (report.severity >= ScriptDiagnostic.Severity.ERROR) {
                scriptingWarnLog(report.message, report.exception)
            }
        }
    }
}
