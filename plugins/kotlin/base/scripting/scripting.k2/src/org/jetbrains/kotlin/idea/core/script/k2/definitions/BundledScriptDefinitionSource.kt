// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.script.shared.definition.BundledScriptDefinition
import org.jetbrains.kotlin.idea.core.script.shared.definition.getBundledScriptDefinition
import org.jetbrains.kotlin.idea.core.script.v1.kotlinScriptTemplate
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import kotlin.script.experimental.api.with

class BundledScriptDefinitionSource(val project: Project) : ScriptDefinitionsSource {
    override val definitions: Sequence<ScriptDefinition> = sequenceOf(project.defaultDefinition)
}

internal val Project.defaultDefinition: ScriptDefinition
    get() {
        val project = this
        val (compilationConfiguration, evaluationConfiguration) = getBundledScriptDefinition(project)
        val updatedConfiguration = compilationConfiguration.with {
            kotlinScriptTemplate {
                id = "default-kts"
                title = ".kts"
                description = KotlinBundle.message("action.new.script.description.kts")
            }
        }

        return BundledScriptDefinition(updatedConfiguration, evaluationConfiguration)
    }