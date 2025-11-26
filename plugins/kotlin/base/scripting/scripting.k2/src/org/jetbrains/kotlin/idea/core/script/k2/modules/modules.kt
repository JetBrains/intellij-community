// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.modules

import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptDiagnostic.Severity
import kotlin.script.experimental.api.SourceCode

data class ScriptingHostConfigurationEntity(val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScriptingHostConfigurationEntity

        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int = data.contentHashCode()
}

data class ScriptEvaluationConfigurationEntity(val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScriptEvaluationConfigurationEntity

        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int = data.contentHashCode()
}

data class ScriptCompilationConfigurationEntity(val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScriptCompilationConfigurationEntity

        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int = data.contentHashCode()
}

data class ScriptDiagnosticData(
    val code: Int,
    val message: String,
    val severity: Severity = Severity.ERROR,
    val sourcePath: String? = null,
    val location: SourceCode.Location? = null,
    val exceptionMessage: String? = null
) {
    fun toScriptDiagnostic(): ScriptDiagnostic = ScriptDiagnostic(
        code, message, severity, sourcePath, location, Throwable(exceptionMessage)
    )
}

fun ScriptDiagnostic.toData(): ScriptDiagnosticData =
    ScriptDiagnosticData(code, message, severity, sourcePath, location, exception?.message)
