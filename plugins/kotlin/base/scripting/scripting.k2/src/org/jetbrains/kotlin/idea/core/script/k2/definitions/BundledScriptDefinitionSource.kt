// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.core.script.shared.definition.BundledScriptDefinition
import org.jetbrains.kotlin.idea.core.script.shared.definition.javaHomePath
import org.jetbrains.kotlin.idea.core.script.shared.definition.scriptClassPath
import org.jetbrains.kotlin.idea.core.script.v1.NewScriptFileInfo
import org.jetbrains.kotlin.idea.core.script.v1.kotlinScriptTemplateInfo
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.createScriptDefinitionFromTemplate
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm
import kotlin.script.templates.standard.ScriptTemplateWithArgs

class BundledScriptDefinitionSource(val project: Project) : ScriptDefinitionsSource {
    override val definitions: Sequence<ScriptDefinition> = sequenceOf(project.defaultDefinition)
}

internal val Project.defaultDefinition: ScriptDefinition
    get() {
        val project = this
        val (compilationConfiguration, evaluationConfiguration) = createScriptDefinitionFromTemplate(
            KotlinType(ScriptTemplateWithArgs::class),
            defaultJvmScriptingHostConfiguration,
            compilation = {
                project.javaHomePath()?.let {
                    jvm.jdkHome(it)
                }
                dependencies(JvmDependency(scriptClassPath))
                displayName("Default Kotlin Script")
                hostConfiguration(defaultJvmScriptingHostConfiguration)
                ide.dependenciesSources(JvmDependency(KotlinArtifacts.kotlinStdlibSources))
                ide.kotlinScriptTemplateInfo(NewScriptFileInfo().apply {
                    id = "default-kts"
                    title = ".kts"
                })
            }
        )

        return BundledScriptDefinition(compilationConfiguration, evaluationConfiguration)
    }