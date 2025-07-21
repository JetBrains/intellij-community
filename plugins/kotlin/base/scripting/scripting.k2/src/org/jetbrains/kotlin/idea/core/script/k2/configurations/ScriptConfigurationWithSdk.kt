// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.configurations

import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import kotlin.script.experimental.api.ResultWithDiagnostics

typealias ScriptConfiguration = ResultWithDiagnostics<ScriptCompilationConfigurationWrapper>

data class ScriptConfigurationWithSdk(
    val scriptConfiguration: ScriptConfiguration,
    val sdk: Sdk?,
) {
    companion object {
        val EMPTY: ScriptConfigurationWithSdk =  ScriptConfigurationWithSdk(ResultWithDiagnostics.Failure(), null)
    }
}