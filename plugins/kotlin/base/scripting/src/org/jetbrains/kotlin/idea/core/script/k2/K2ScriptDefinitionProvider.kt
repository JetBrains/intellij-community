// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.core.script.SCRIPT_DEFINITIONS_SOURCES
import org.jetbrains.kotlin.scripting.definitions.LazyScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm

/**
 * Holds uploaded cache definitions.
 * Returns default definition if update did not happen.
 */
class K2ScriptDefinitionProvider(val project: Project) : LazyScriptDefinitionProvider() {
    private val allDefinitions: AtomicReference<List<ScriptDefinition>> = AtomicReference(emptyList())

    init {
        reloadDefinitionsFromSources()
    }

    fun getAllDefinitions(): List<ScriptDefinition> = allDefinitions.get()

    public override val currentDefinitions: Sequence<ScriptDefinition>
        get() {
            val settingsByDefinitionId =
                ScriptDefinitionPersistentSettings.getInstance(project).getIndexedSettingsPerDefinition()

            return allDefinitions.get()
                .filter { settingsByDefinitionId[it.definitionId]?.setting?.enabled != false }
                .sortedBy { settingsByDefinitionId[it.definitionId]?.index ?: it.order }
                .asSequence()
        }

    fun reloadDefinitionsFromSources() {
        val scriptDefinitions = SCRIPT_DEFINITIONS_SOURCES.getExtensions(project)
            .flatMap { it.definitions }

        allDefinitions.set(scriptDefinitions)
        clearCache()
    }

    override fun getDefaultDefinition(): ScriptDefinition = project.defaultDefinition

    companion object {
        fun getInstance(project: Project): K2ScriptDefinitionProvider =
            project.service<ScriptDefinitionProvider>() as K2ScriptDefinitionProvider
    }
}

val Project.defaultDefinition: ScriptDefinition
    get() {
        val classPath = listOf(
            KotlinArtifacts.kotlinScriptRuntime,
            KotlinArtifacts.kotlinStdlib,
            KotlinArtifacts.kotlinReflect
        )

        val javaHomePath = ProjectRootManager.getInstance(this).projectSdk?.takeIf { it.sdkType is JavaSdkType }?.homePath

        val compilationConfiguration = ScriptCompilationConfiguration.Default.with {
            javaHomePath?.let {
                jvm.jdkHome(File(it))
            }
            dependencies(JvmDependency(classPath))
            displayName("Bundled Script Definition")
            hostConfiguration(defaultJvmScriptingHostConfiguration)
        }

        return BundledScriptDefinition(
            defaultJvmScriptingHostConfiguration,
            compilationConfiguration,
            ScriptEvaluationConfiguration.Default,
        ).apply {
            order = Int.MAX_VALUE
        }
    }

class BundledScriptDefinition(
    hostConfiguration: ScriptingHostConfiguration, compilationConfiguration: ScriptCompilationConfiguration,
    evaluationConfiguration: ScriptEvaluationConfiguration?
) : ScriptDefinition.FromConfigurations(
    hostConfiguration,
    compilationConfiguration,
    evaluationConfiguration
) {
    override val canDefinitionBeSwitchedOff: Boolean = false
    override val isDefault: Boolean = true
}

class BundledScriptDefinitionSource(val project: Project) : ScriptDefinitionsSource {
    override val definitions: Sequence<ScriptDefinition> = sequenceOf(project.defaultDefinition)
}