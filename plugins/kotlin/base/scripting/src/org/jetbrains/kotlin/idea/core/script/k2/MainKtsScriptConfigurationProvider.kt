// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.SequentialProgressReporter
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.containers.addIfNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.jetbrains.kotlin.idea.base.scripting.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.*
import org.jetbrains.kotlin.idea.core.script.k2.ScriptClassPathUtil.Companion.findVirtualFiles
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.ScriptReportSink
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm

@Service(Service.Level.PROJECT)
class MainKtsScriptConfigurationProvider(val project: Project, val coroutineScope: CoroutineScope) : ScriptRefinedConfigurationResolver,
                                                                                                     ScriptWorkspaceModelManager {
    private val data = ConcurrentHashMap<VirtualFile, ScriptConfigurationWithSdk>()

    var reporter: SequentialProgressReporter? = null
        private set

    override fun get(virtualFile: VirtualFile): ScriptConfigurationWithSdk? = data[virtualFile]

    override suspend fun create(virtualFile: VirtualFile, definition: ScriptDefinition): ScriptConfigurationWithSdk? {
        withBackgroundProgress(project, title = KotlinBaseScriptingBundle.message("progress.title.dependency.resolution")) {
            reportSequentialProgress { theReporter ->
                try {
                    reporter = theReporter
                    val mainKtsConfiguration = resolveMainKtsConfiguration(virtualFile, definition)
                    val importedScripts = mainKtsConfiguration.importedScripts
                    if (importedScripts.isNotEmpty()) {
                        val results = ConcurrentHashMap.newKeySet<ScriptConfigurationWithSdk>()
                        coroutineScope.resolveConfigurations(importedScripts, results).await()
                    }

                    data[virtualFile] = mainKtsConfiguration

                } finally {
                    reporter = null
                }
            }
        }

        return data[virtualFile]
    }

    fun CoroutineScope.resolveConfigurations(
        scripts: Collection<VirtualFile>,
        results: MutableSet<ScriptConfigurationWithSdk>
    ): Deferred<Unit> {
        return async {
            for (it in scripts) {
                val scriptSource = VirtualFileScriptSource(it)
                val definition = findScriptDefinition(project, scriptSource)

                val result = definition.getConfigurationResolver(project).create(it, definition) ?: continue
                results += result

                resolveConfigurations(result.importedScripts, results)
            }
        }
    }

    private suspend fun resolveMainKtsConfiguration(mainKts: VirtualFile, definition: ScriptDefinition): ScriptConfigurationWithSdk {
        val scriptSource = VirtualFileScriptSource(mainKts)
        val sdk = ProjectJdkTable.getInstance().allJdks.firstOrNull()

        val providedConfiguration = sdk?.homePath?.let { jdkHome ->
            definition.compilationConfiguration.with {
                jvm.jdkHome(File(jdkHome))
            }
        }

        val result = smartReadAction(project) {
            refineScriptCompilationConfiguration(scriptSource, definition, project, providedConfiguration)
        }
        project.service<ScriptReportSink>().attachReports(mainKts, result.reports)

        return ScriptConfigurationWithSdk(result, sdk)
    }

    override suspend fun updateWorkspaceModel(configurationPerFile: Map<VirtualFile, ScriptConfigurationWithSdk>) {
        val mainKtsScript = configurationPerFile.entries.firstOrNull()?.key ?: return
        val importedScripts = data[mainKtsScript]?.importedScripts ?: emptyList()
        for (it in importedScripts) {
            val scriptSource = VirtualFileScriptSource(it)
            val definition = findScriptDefinition(project, scriptSource)
            val configuration = definition.getConfigurationResolver(project).get(it) ?: continue
            definition.getWorkspaceModelManager(project).updateWorkspaceModel(mapOf(it to configuration))
        }

        val workspaceModel = project.workspaceModel
        workspaceModel.update("updating .main.kts modules") { targetStorage ->
            val updatedStorage = getUpdatedStorage(project, configurationPerFile, workspaceModel)

            targetStorage.applyChangesFrom(updatedStorage)
        }
    }

    private val ScriptConfigurationWithSdk.importedScripts
        get() = this.scriptConfiguration.valueOrNull()?.importedScripts?.mapNotNull { imported -> (imported as? VirtualFileScriptSource)?.virtualFile }
            ?: emptyList()

    private fun getUpdatedStorage(
        project: Project,
        configurationsData: Map<VirtualFile, ScriptConfigurationWithSdk>,
        workspaceModel: WorkspaceModel,
    ): MutableEntityStorage {
        val virtualFileManager = workspaceModel.getVirtualFileUrlManager()
        val storageToUpdate = MutableEntityStorage.from(workspaceModel.currentSnapshot)

        for ((scriptFile, configurationWithSdk) in configurationsData) {
            val configuration = configurationWithSdk.scriptConfiguration.valueOrNull() ?: continue

            val definition = findScriptDefinition(project, VirtualFileScriptSource(scriptFile))

            val source = MainKtsKotlinScriptEntitySource(scriptFile.toVirtualFileUrl(virtualFileManager))
            val locationName = project.scriptModuleRelativeLocation(scriptFile)
            val libraryDependencies =
                storageToUpdate.getDependencies(configuration, source, definition, locationName)

            val sdkDependency = configurationWithSdk.sdk?.let { SdkDependency(SdkId(it.name, it.sdkType.name)) }
            val allDependencies = listOfNotNull(sdkDependency) + libraryDependencies

            val scriptModuleId = ModuleId("${KOTLIN_SCRIPTS_MODULE_NAME}.${definition.name}.$locationName")
            val existingModule = storageToUpdate.resolve(scriptModuleId)
            if (existingModule == null) {
                storageToUpdate.addEntity(
                    ModuleEntity(scriptModuleId.name, allDependencies, source)
                )
            } else {
                storageToUpdate.modifyModuleEntity(existingModule) {
                    dependencies = allDependencies.toMutableList()
                }
            }
        }

        return storageToUpdate
    }

    private fun MutableEntityStorage.getDependencies(
        wrapper: ScriptCompilationConfigurationWrapper,
        source: KotlinScriptEntitySource,
        definition: ScriptDefinition,
        locationName: String
    ): List<LibraryDependency> {
        val definitionDependency = getOrCreateDefinitionDependency(definition, project, source)
        val storage = this

        return buildList {
            if (wrapper.isUberDependencyAllowed()) {
                val classes = wrapper.configuration?.get(ScriptCompilationConfiguration.dependencies).findVirtualFiles()
                val sources = wrapper.configuration?.get(ScriptCompilationConfiguration.ide.dependenciesSources).findVirtualFiles()

                addIfNotNull(storage.createUberDependency(locationName, classes, sources, source))
            } else {
                add(definitionDependency)

                val classes = wrapper.configuration?.get(ScriptCompilationConfiguration.dependencies)?.drop(1).findVirtualFiles()
                addAll(
                    classes.map {
                        getOrCreateLibrary(it.name, listOf(it.compiledLibraryRoot(project)), source)
                    }
                )
            }
        }
    }

    private fun MutableEntityStorage.createUberDependency(
        locationName: String,
        classes: List<VirtualFile>,
        sources: List<VirtualFile>,
        source: KotlinScriptEntitySource,
    ): LibraryDependency? {
        if (classes.isEmpty() && sources.isEmpty()) return null

        val classRoots = classes.map { it.compiledLibraryRoot(project) }
        val sourceRoots = sources.map { it.sourceLibraryRoot(project) }

        return createOrUpdateLibrary("$locationName dependencies", classRoots + sourceRoots, source)
    }

    private fun ScriptCompilationConfigurationWrapper.isUberDependencyAllowed(): Boolean {
        return dependenciesSources.size + dependenciesClassPath.size < 20
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): MainKtsScriptConfigurationProvider = project.service()
    }

    class MainKtsKotlinScriptEntitySource(override val virtualFileUrl: VirtualFileUrl?) : KotlinScriptEntitySource(virtualFileUrl)
}