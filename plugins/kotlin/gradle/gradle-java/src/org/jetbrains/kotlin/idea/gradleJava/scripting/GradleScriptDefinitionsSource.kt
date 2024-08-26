// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.scripting

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.core.script.SCRIPT_DEFINITIONS_SOURCES
import org.jetbrains.kotlin.idea.core.script.k2.K2ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.util.concurrent.atomic.AtomicReference

class GradleScriptDefinitionsSource(val project: Project) : ScriptDefinitionsSource {
    private val _definitions: AtomicReference<List<ScriptDefinition>> = AtomicReference(listOf())

    override val definitions: Sequence<ScriptDefinition>
        get() = _definitions.get().asSequence()

    /**
     * Force-wrap legacy definitions into `ScriptDefinition.FromConfigurations` when updating.
     */
    fun updateDefinitions(templateDefinitions: List<ScriptDefinition>) {
        _definitions.set(templateDefinitions)
        K2ScriptDefinitionProvider.getInstance(project).reloadDefinitionsFromSources()
    }

    companion object {
        fun getInstance(project: Project): GradleScriptDefinitionsSource? =
            SCRIPT_DEFINITIONS_SOURCES.getExtensions(project)
                .filterIsInstance<GradleScriptDefinitionsSource>().firstOrNull()
                .safeAs<GradleScriptDefinitionsSource>()
    }
}