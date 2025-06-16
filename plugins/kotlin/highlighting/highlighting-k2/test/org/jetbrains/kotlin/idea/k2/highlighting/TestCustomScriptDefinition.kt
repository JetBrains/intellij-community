// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.highlighting

import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.host.ScriptingHostConfiguration

/**
 * Is used to prioritize custom [ScriptCompilationConfiguration] in the test scenario.
 * @see [AbstractK2BundledCompilerPluginsInScriptHighlightingMetaInfoTest]
 */
internal class TestCustomScriptDefinition(
    compilationConfiguration: ScriptCompilationConfiguration,
    override val evaluationConfiguration: ScriptEvaluationConfiguration?
) : ScriptDefinition.FromConfigurations(
    ScriptingHostConfiguration {},
    compilationConfiguration,
    evaluationConfiguration
) {
    init {
        order = Int.MIN_VALUE
    }
}