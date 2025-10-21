// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.shared

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.gradle.scripting.shared.actions.ShowKotlinGradleDslLogs
import org.jetbrains.kotlin.idea.core.script.shared.ScriptDiagnosticFixProvider
import kotlin.script.experimental.api.ScriptDiagnostic

class GradleScriptDiagnosticFixProvider : ScriptDiagnosticFixProvider {
    override fun provideFixes(annotation: ScriptDiagnostic): List<IntentionAction> {
        if (gradleMessagesForQuickFix.any { annotation.message.contains(it) }) {
            return listOf(ShowKotlinGradleDslLogs())
        }
        return emptyList()
    }

    private val gradleMessagesForQuickFix = arrayListOf(
        "This script caused build configuration to fail",
        "see IDE logs for more information",
        "Script dependencies resolution failed"
    )
}