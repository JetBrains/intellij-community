// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.script.k2

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.SCRIPT_CONFIGURATIONS_SOURCES
import org.jetbrains.kotlin.idea.core.script.getUpdatedStorage
import org.jetbrains.kotlin.idea.core.script.k2.BaseScriptModel
import org.jetbrains.kotlin.idea.core.script.k2.ScriptConfigurations
import org.jetbrains.kotlin.idea.core.script.k2.ScriptConfigurationsSource
import org.jetbrains.kotlin.idea.core.script.scriptDefinitionsSourceOfType
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptReportSink
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.io.File
import java.nio.file.Path
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm


class MainKtsScriptConfigurationsSource(override val project: Project) : ScriptConfigurationsSource<BaseScriptModel>(project) {
    override fun getScriptDefinitionsSource(): ScriptDefinitionsSource? =
        project.scriptDefinitionsSourceOfType<MainKtsScriptDefinitionSource>()

    override suspend fun resolveDependencies(scripts: Iterable<BaseScriptModel>): ScriptConfigurations {
        val projectSdk = ProjectJdkTable.getInstance().allJdks.firstOrNull()

        val configurations = scripts.associate {
            val scriptSource = VirtualFileScriptSource(it.virtualFile)
            val definition = findScriptDefinition(project, scriptSource)

            val javaHome = projectSdk?.homePath

            val providedConfiguration = javaHome?.let {
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

        return data.get().compose(
            ScriptConfigurations(
                configurations,
                sdks = projectSdk?.homePath?.let { mapOf(Path.of(it) to projectSdk) } ?: emptyMap()
            ))
    }

    override suspend fun updateModules(configurationsData: ScriptConfigurations, storage: MutableEntityStorage?) {
        val updatedStorage = getUpdatedStorage(
            project, configurationsData
        ) { KotlinCustomScriptModuleEntitySource(it) }

        val scriptFiles = configurationsData.configurations.keys.toSet()

        project.workspaceModel.update("Updating MainKts Kotlin Scripts modules") {
            it.replaceBySource(
                { source ->
                    (source as? KotlinCustomScriptModuleEntitySource)?.let {
                        scriptFiles.contains(it.virtualFileUrl?.virtualFile)
                    } == true
                }, updatedStorage
            )
        }
    }

    companion object {
        fun getInstance(project: Project): MainKtsScriptConfigurationsSource? =
            SCRIPT_CONFIGURATIONS_SOURCES.getExtensions(project).filterIsInstance<MainKtsScriptConfigurationsSource>().firstOrNull()
                .safeAs<MainKtsScriptConfigurationsSource>()
    }

    open class KotlinCustomScriptModuleEntitySource(override val virtualFileUrl: VirtualFileUrl?) : KotlinScriptEntitySource(virtualFileUrl)
}