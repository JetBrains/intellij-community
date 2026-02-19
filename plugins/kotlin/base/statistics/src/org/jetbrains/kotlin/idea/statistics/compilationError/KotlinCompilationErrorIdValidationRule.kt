// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.statistics.compilationError

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule

class KotlinCompilationErrorIdValidationRule : CustomValidationRule() {
    override fun getRuleId(): String = "kotlin.compilation.error.id"

    override fun doValidate(data: String, context: EventContext): ValidationResultType {
        return if (allowedCompilationErrorsIds.contains(data))
            ValidationResultType.ACCEPTED
        else
            ValidationResultType.REJECTED
    }
}

private val allowedCompilationErrorsIds: Set<String> = buildSet {
    val classesWithDiagnostics = listOf(
        "org.jetbrains.kotlin.diagnostics.Errors",
        "org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm",
        "org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors",
        "org.jetbrains.kotlin.fir.analysis.diagnostics.js.FirJsErrors",
        "org.jetbrains.kotlin.fir.analysis.diagnostics.jvm.FirJvmErrors",
        "org.jetbrains.kotlin.fir.analysis.diagnostics.native.FirNativeErrors",
        "org.jetbrains.kotlin.fir.builder.FirSyntaxErrors",
        "org.jetbrains.kotlin.fir.analysis.diagnostics.wasm.FirWasmErrors",
        "org.jetbrains.kotlin.fir.analysis.diagnostics.web.common.FirWebCommonErrors",
    )

    for (className in classesWithDiagnostics) {
        // K2 classes don't have public fields, so we should check private ones
        val fields = Class.forName(className).declaredFields
        for (field in fields) {
            // K2 diagnostics are declared as delegated properties, so we have to drop this suffix
            val diagnosticName = field.name.removeSuffix("\$delegate")
            add(diagnosticName)
        }
    }
}
