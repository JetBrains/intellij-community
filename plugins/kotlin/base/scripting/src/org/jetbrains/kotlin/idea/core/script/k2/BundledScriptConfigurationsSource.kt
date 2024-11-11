// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.core.script.*
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.ScriptReportSink
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import java.io.File
import java.nio.file.Path
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm

open class BundledScriptConfigurationsSource(override val project: Project, val coroutineScope: CoroutineScope) :
    ScriptConfigurationsSource<BaseScriptModel>(project) {

    override fun getScriptDefinitionsSource(): ScriptDefinitionsSource? =
        project.scriptDefinitionsSourceOfType<BundledScriptDefinitionSource>()

    override fun getScriptConfigurations(virtualFile: VirtualFile): ResultWithDiagnostics<ScriptCompilationConfigurationWrapper>? {
        val currentData = super.getScriptConfigurations(virtualFile)

        if (currentData is ResultWithDiagnostics.Success) {
            return currentData
        }

        if (KotlinScriptLazyResolveProhibitionCondition.prohibitLazyResolve(project, virtualFile)) return null

        coroutineScope.launch {
            updateDependenciesAndCreateModules(setOf(BaseScriptModel(virtualFile)))
        }

        return super.getScriptConfigurations(virtualFile)
    }

    override suspend fun updateConfigurations(scripts: Iterable<BaseScriptModel>) {
        val sdk = ProjectRootManager.getInstance(project).projectSdk

        val configurations = scripts.associate {
            val scriptSource = VirtualFileScriptSource(it.virtualFile)
            val definition = findScriptDefinition(project, scriptSource)

            val providedConfiguration = sdk?.homePath
                ?.let {
                    definition.compilationConfiguration.with {
                        jvm.jdkHome(File(it))
                    }
                }

            it.virtualFile to smartReadAction(project) {
                refineScriptCompilationConfiguration(scriptSource, definition, project, providedConfiguration)
            }
        }

        configurations.forEach { (script, result) ->
            project.service<ScriptReportSink>().attachReports(script, result.reports)
        }

        val scriptConfigurations = ScriptConfigurations(
            configurations,
            sdks = sdk?.homePath?.let<@NonNls String, Map<Path, Sdk>> { mapOf(Path.of(it) to sdk) } ?: emptyMap()
        )

        data.getAndAccumulate(scriptConfigurations) { left, right -> left + right }
    }

    override suspend fun updateModules(storage: MutableEntityStorage?) {
        val updatedStorage = getUpdatedStorage(
            project, data.get()
        ) { KotlinBundledScriptModuleEntitySource(it) }

        project.workspaceModel.update("Updating MainKts Kotlin Scripts modules") {
            it.replaceBySource(
                { source -> source is KotlinBundledScriptModuleEntitySource },
                updatedStorage
            )
        }
    }

    open class KotlinBundledScriptModuleEntitySource(override val virtualFileUrl: VirtualFileUrl?) :
        KotlinScriptEntitySource(virtualFileUrl)
}