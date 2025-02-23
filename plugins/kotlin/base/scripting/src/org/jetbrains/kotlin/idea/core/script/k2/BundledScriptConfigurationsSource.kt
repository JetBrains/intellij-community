// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.core.script.*
import org.jetbrains.kotlin.idea.core.script.ucache.relativeName
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptReportSink
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import java.io.File
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm

open class BundledScriptConfigurationsSource(override val project: Project, val coroutineScope: CoroutineScope) :
    ScriptConfigurationsSource<BaseScriptModel>(project) {

    override fun getScriptDefinitionsSource(): ScriptDefinitionsSource? =
        project.scriptDefinitionsSourceOfType<BundledScriptDefinitionSource>()

    override fun getConfigurationWithSdk(virtualFile: VirtualFile): ScriptConfigurationWithSdk? {
        val current = data.get()[virtualFile]

        if (current?.scriptConfiguration is ResultWithDiagnostics.Success) {
            return current
        }

        if (KotlinScriptLazyResolveProhibitionCondition.prohibitLazyResolve(project, virtualFile)) return null

        coroutineScope.launch {
            project.waitForSmartMode()

            val definition = findScriptDefinition(project, VirtualFileScriptSource(virtualFile))
            val suitableDefinitions = getScriptDefinitionsSource()?.definitions ?: return@launch
            if (suitableDefinitions.none { it.definitionId == definition.definitionId }) return@launch

            updateDependenciesAndCreateModules(setOf(BaseScriptModel(virtualFile)))
        }

        return data.get()[virtualFile]
    }

    override suspend fun updateConfigurations(scripts: Iterable<BaseScriptModel>) {
        val sdk = ProjectRootManager.getInstance(project).projectSdk ?: ProjectJdkTable.getInstance().allJdks.firstOrNull()

        val configurations = scripts.associate {
            val scriptSource = VirtualFileScriptSource(it.virtualFile)
            val definition = findScriptDefinition(project, scriptSource)

            val providedConfiguration = sdk?.homePath?.let {
                definition.compilationConfiguration.with {
                    jvm.jdkHome(File(it))
                }
            }

            val result = smartReadAction(project) {
                refineScriptCompilationConfiguration(scriptSource, definition, project, providedConfiguration)
            }

            project.service<ScriptReportSink>().attachReports(it.virtualFile, result.reports)

            it.virtualFile to ScriptConfigurationWithSdk(result, sdk)
        }

        data.getAndAccumulate(configurations) { left, right -> left + right }
    }

    override suspend fun updateModules(storage: MutableEntityStorage?) {
        val updatedStorage = getUpdatedStorage(
            project, data.get()
        ) { KotlinBundledScriptModuleEntitySource(it) }

        project.workspaceModel.update("updating .kts modules") {
            it.replaceBySource(
                { source -> source is KotlinBundledScriptModuleEntitySource }, updatedStorage
            )
        }
    }

    private fun getUpdatedStorage(
        project: Project,
        configurationsData: Map<VirtualFile, ScriptConfigurationWithSdk>,
        entitySourceSupplier: (virtualFileUrl: VirtualFileUrl) -> KotlinScriptEntitySource,
    ): MutableEntityStorage {
        val updatedStorage = MutableEntityStorage.create()

        for ((scriptFile, configurationWithSdk) in configurationsData) {

            val definition = findScriptDefinition(project, VirtualFileScriptSource(scriptFile))

            val definitionScriptModuleName = "$KOTLIN_SCRIPTS_MODULE_NAME.${definition.name}"
            val locationName = scriptFile.relativeName(project).replace(VfsUtilCore.VFS_SEPARATOR_CHAR, ':')

            val virtualFileManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
            val source = entitySourceSupplier(scriptFile.toVirtualFileUrl(virtualFileManager))

            val definitionDependency =
                updatedStorage.getDefinitionLibraryEntity(definition, project, KotlinBundledScriptModuleEntitySource(null))?.let {
                    LibraryDependency(it.symbolicId, false, DependencyScope.COMPILE)
                }

            val sdkDependency = configurationWithSdk.sdk?.let { SdkDependency(SdkId(it.name, it.sdkType.name)) }
            val allDependencies = listOfNotNull(sdkDependency, definitionDependency)

            updatedStorage.addEntity(ModuleEntity("$definitionScriptModuleName.$locationName", allDependencies, source))
        }

        return updatedStorage
    }

    open class KotlinBundledScriptModuleEntitySource(override val virtualFileUrl: VirtualFileUrl?) :
        KotlinScriptEntitySource(virtualFileUrl)
}