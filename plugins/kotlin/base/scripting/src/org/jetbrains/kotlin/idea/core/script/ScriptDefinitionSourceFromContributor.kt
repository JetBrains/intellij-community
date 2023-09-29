// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script

import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

@Deprecated("migrating to new configuration refinement: convert all contributors to ScriptDefinitionsSource/ScriptDefinitionsProvider")
class ScriptDefinitionSourceFromContributor(
  val contributor: ScriptDefinitionContributor,
  private val hostConfiguration: ScriptingHostConfiguration = defaultJvmScriptingHostConfiguration
) : ScriptDefinitionsSource {
    override val definitions: Sequence<ScriptDefinition>
        get() =
            if (contributor is ScriptDefinitionsSource) contributor.definitions
            else contributor.getDefinitions().asSequence().map { ScriptDefinition.FromLegacy(hostConfiguration, it) }

    override fun equals(other: Any?): Boolean {
        return contributor.id == (other as? ScriptDefinitionSourceFromContributor)?.contributor?.id
    }

    override fun hashCode(): Int {
        return contributor.id.hashCode()
    }
}