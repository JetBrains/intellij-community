// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.highlighting

import org.jetbrains.kotlin.idea.test.Directives
import kotlin.script.experimental.api.ScriptCompilationConfiguration

/**
 * This interface is used to override script compilation configuration for test scripts using test directives.
 * Initial [ScriptCompilationConfiguration] is the same as for ScriptingSupport in K2.
 */
fun interface TestScriptConfigurationRefiner {
    fun refineScriptCompilationConfiguration(globalDirectives: Directives, configuration: ScriptCompilationConfiguration): ScriptCompilationConfiguration
}