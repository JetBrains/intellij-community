// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script

import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.scripting.definitions.KotlinScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource

@Deprecated("migrating to new configuration refinement: use ScriptDefinitionsSource internally and kotlin.script.experimental.intellij.ScriptDefinitionsProvider as a providing extension point")
interface ScriptDefinitionContributor {

    @Deprecated("migrating to new configuration refinement: drop usages")
    val id: String

    @Deprecated("migrating to new configuration refinement: use ScriptDefinitionsSource instead")
    fun getDefinitions(): List<KotlinScriptDefinition>

    @Deprecated("migrating to new configuration refinement: drop usages")
    fun isReady() = true

    companion object {
        val EP_NAME: ProjectExtensionPointName<ScriptDefinitionContributor> =
          ProjectExtensionPointName("org.jetbrains.kotlin.scriptDefinitionContributor")

        inline fun <reified T> find(project: Project) =
            EP_NAME.getPoint(project).extensionList.filterIsInstance<T>().firstOrNull()
    }
}

fun ScriptDefinitionContributor.asSource(): ScriptDefinitionsSource =
    if (this is ScriptDefinitionsSource) this
    else ScriptDefinitionSourceFromContributor(this)
