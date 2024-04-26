// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script

import org.jetbrains.kotlin.scripting.definitions.KotlinScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource

@Deprecated("migrating to new configuration refinement: use ScriptDefinitionsSource directly instead")
interface ScriptDefinitionSourceAsContributor : ScriptDefinitionContributor, ScriptDefinitionsSource {

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("migrating to new configuration refinement: use ScriptDefinitionsSource instead")
    override fun getDefinitions(): List<KotlinScriptDefinition> = definitions.map { it.legacyDefinition }.toList()
}