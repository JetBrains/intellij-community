// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import kotlinx.coroutines.CoroutineScope
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

    override fun getConfiguration(virtualFile: VirtualFile): ResultWithDiagnostics<ScriptCompilationConfigurationWrapper>? {
        val current = data.get().configurations[virtualFile]

        if (current is ResultWithDiagnostics.Success) {
            return current
        }

        if (KotlinScriptLazyResolveProhibitionCondition.prohibitLazyResolve(project, virtualFile)) return null

        DependencyResolutionService.getInstance(project).resolveInBackground {
            updateDependenciesAndCreateModules(setOf(BaseScriptModel(virtualFile)))
        }

        return data.get().configurations[virtualFile]
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

            it.virtualFile to smartReadAction(project) {
                refineScriptCompilationConfiguration(scriptSource, definition, project, providedConfiguration)
            }
        }

        configurations.forEach { (script, result) ->
            project.service<ScriptReportSink>().attachReports(script, result.reports)
        }

        val scriptConfigurations = ScriptConfigurations(
            configurations,
            sdks = sdk?.homePath?.let<@NonNls String, Map<Path, Sdk>> { mapOf(Path.of(it) to sdk) } ?: emptyMap())

        data.getAndAccumulate(scriptConfigurations) { left, right -> left + right }
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
        configurationsData: ScriptConfigurations,
        entitySourceSupplier: (virtualFileUrl: VirtualFileUrl) -> KotlinScriptEntitySource,
    ): MutableEntityStorage {
        val updatedStorage = MutableEntityStorage.create()
        val projectPath = project.basePath?.let { Path.of(it) } ?: return updatedStorage

        for (scriptFile in configurationsData.configurations.keys) {
            val basePath = projectPath.toFile()
            val file = Path.of(scriptFile.path).toFile()
            val relativeLocation = FileUtil.getRelativePath(basePath, file) ?: continue

            val definition = findScriptDefinition(project, VirtualFileScriptSource(scriptFile))

            val definitionScriptModuleName = "$KOTLIN_SCRIPTS_MODULE_NAME.${definition.name}"
            val locationName = relativeLocation.replace(VfsUtilCore.VFS_SEPARATOR_CHAR, ':')
            val moduleName = "$definitionScriptModuleName.$locationName"

            val sdkDependency = configurationsData.sdks.values.firstOrNull()?.let { SdkDependency(SdkId(it.name, it.sdkType.name)) }

            val virtualFileManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
            val source = entitySourceSupplier(scriptFile.toVirtualFileUrl(virtualFileManager))

            val definitionDependency = updatedStorage.getDefinitionLibraryEntity(definition, project, KotlinBundledScriptModuleEntitySource(null))?.let {
                LibraryDependency(it.symbolicId, false, DependencyScope.COMPILE)
            }

            val allDependencies = listOfNotNull(sdkDependency, definitionDependency)

            updatedStorage.addEntity(ModuleEntity(moduleName, allDependencies, source))
        }

        return updatedStorage
    }

    open class KotlinBundledScriptModuleEntitySource(override val virtualFileUrl: VirtualFileUrl?) :
        KotlinScriptEntitySource(virtualFileUrl)
}