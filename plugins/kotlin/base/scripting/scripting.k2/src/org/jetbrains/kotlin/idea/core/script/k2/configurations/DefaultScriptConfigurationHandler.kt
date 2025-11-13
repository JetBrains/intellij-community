// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.configurations

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.idea.core.script.k2.asEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptConfigurationProviderExtension
import org.jetbrains.kotlin.idea.core.script.k2.modules.updateKotlinScriptEntities
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.ScriptReportSink
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import java.io.File
import kotlin.script.experimental.api.*
import kotlin.script.experimental.api.ScriptDiagnostic.Companion.unspecifiedError
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm

@Service(Service.Level.PROJECT)
class DefaultScriptConfigurationHandler(
    val project: Project, val coroutineScope: CoroutineScope
) : ScriptConfigurationProviderExtension {

    override suspend fun create(virtualFile: VirtualFile, definition: ScriptDefinition): ScriptCompilationConfigurationResult {
        val definitionJdk = definition.compilationConfiguration[ScriptCompilationConfiguration.jvm.jdkHome]
        val configuration = getInitialConfiguration(definitionJdk, definition)

        val scriptSource = VirtualFileScriptSource(virtualFile)

        val result = smartReadAction(project) {
            try {
                refineScriptCompilationConfiguration(scriptSource, definition, project, configuration)
            } catch (e: Throwable) {
                ResultWithDiagnostics.Failure(
                    ScriptDiagnostic(
                        code = unspecifiedError,
                        exception = e,
                        message = "Failed to refine script",
                        sourcePath = scriptSource.locationId
                    )
                )
            }
        }

        fun MutableEntityStorage.updatedStorage() {
            val configuration = result.valueOrNull()?.configuration ?: return

            val libraryIds = generateScriptLibraryEntities(configuration, definition, project)
            libraryIds.filterNot {
                this.contains(it)
            }.forEach { (classes, sources) ->
                this addEntity KotlinScriptLibraryEntity(classes, sources, DefaultScriptEntitySource)
            }

            this addEntity KotlinScriptEntity(
                virtualFile.virtualFileUrl, libraryIds.toList(), DefaultScriptEntitySource
            ) {
                this.configuration = configuration.asEntity()
                this.sdkId = configuration.sdkId
            }
        }

        project.updateKotlinScriptEntities(DefaultScriptEntitySource) {
            val builder = it.toSnapshot().toBuilder()
            if (builder.getVirtualFileUrlIndex().findEntitiesByUrl(virtualFile.virtualFileUrl).none()) {
                builder.updatedStorage()
                it.applyChangesFrom(builder)
            }
        }

        project.service<ScriptReportSink>().attachReports(virtualFile, result.reports)

        return result
    }

    private fun getInitialConfiguration(
        definitionJdk: File?,
        definition: ScriptDefinition
    ): ScriptCompilationConfiguration = if (definitionJdk != null) definition.compilationConfiguration
    else {
        val projectSdk = ProjectRootManager.getInstance(project).projectSdk?.homePath
        definition.compilationConfiguration.with {
            projectSdk?.let {
                jvm.jdkHome(File(it))
            }
        }
    }

    private val urlManager: VirtualFileUrlManager
        get() = project.workspaceModel.getVirtualFileUrlManager()

    private val VirtualFile.virtualFileUrl: VirtualFileUrl
        get() = toVirtualFileUrl(urlManager)

    companion object {
        @JvmStatic
        fun getInstance(project: Project): DefaultScriptConfigurationHandler = project.service()
    }

    object DefaultScriptEntitySource : EntitySource
}