// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.modules

import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptDiagnostic.Severity
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.SourceCode.Position

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
    val severity: SeverityData,
    val sourcePath: String?,
    val locationData: LocationData?,
    val exceptionMessage: String?,
) {
    fun map(): ScriptDiagnostic = ScriptDiagnostic(
        code, message, severity.map(), sourcePath, locationData?.map(), Throwable(exceptionMessage)
    )
}

fun ScriptDiagnostic.map(): ScriptDiagnosticData = ScriptDiagnosticData(
    code = code,
    message = message,
    severity = severity.map(),
    sourcePath = sourcePath,
    locationData = location?.map(),
    exceptionMessage = message,
)

enum class SeverityData { DEBUG, INFO, WARNING, ERROR, FATAL }

private fun SeverityData.map(): Severity = when (this) {
    SeverityData.DEBUG -> Severity.DEBUG
    SeverityData.INFO -> Severity.INFO
    SeverityData.WARNING -> Severity.WARNING
    SeverityData.ERROR -> Severity.ERROR
    SeverityData.FATAL -> Severity.FATAL
}

private fun Severity.map(): SeverityData = when (this) {
    Severity.DEBUG -> SeverityData.DEBUG
    Severity.INFO -> SeverityData.INFO
    Severity.WARNING -> SeverityData.WARNING
    Severity.ERROR -> SeverityData.ERROR
    Severity.FATAL -> SeverityData.FATAL
}

data class PositionData(val line: Int, val col: Int, val absolutePos: Int? = null) {
    fun map(): Position = Position(line, col, absolutePos)
}

private fun Position.map(): PositionData = PositionData(line, col, absolutePos)

data class LocationData(val start: PositionData, val end: PositionData? = null) {
    fun map(): SourceCode.Location = SourceCode.Location(start.map(), end?.map())
}

private fun SourceCode.Location.map(): LocationData = LocationData(start.map(), end?.map())
