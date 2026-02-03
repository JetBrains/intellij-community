// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.shared

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.extensions.ExtensionPointName
import kotlin.script.experimental.api.ScriptDiagnostic

interface ScriptDiagnosticFixProvider {

    fun provideFixes(annotation: ScriptDiagnostic): List<IntentionAction>

    companion object {
        val EP_NAME: ExtensionPointName<ScriptDiagnosticFixProvider> =
            ExtensionPointName.create<ScriptDiagnosticFixProvider>("org.jetbrains.kotlin.scriptDiagnosticFixProvider")
    }
}