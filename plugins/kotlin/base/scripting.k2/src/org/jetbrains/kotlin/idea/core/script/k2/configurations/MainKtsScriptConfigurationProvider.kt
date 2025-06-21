// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.configurations

import com.google.common.collect.TreeMultimap
import com.google.common.graph.Traverser
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
import com.intellij.platform.util.progress.ProgressReporter
import com.intellij.platform.util.progress.reportProgress
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.containers.addIfNotNull
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.idea.base.scripting.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.*
import org.jetbrains.kotlin.idea.core.script.ScriptClassPathUtil.Companion.findVirtualFiles
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
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
    private val importedScripts = TreeMultimap.create(COMPARATOR, COMPARATOR)
    private val importedScriptsTraverser = Traverser.forTree<VirtualFile> { importedScripts.get(it) }

    fun getImportedScripts(mainKts: VirtualFile): List<VirtualFile> = importedScriptsTraverser.breadthFirst(mainKts) - mainKts

    var reporter: ProgressReporter? = null
        private set

    override fun get(virtualFile: VirtualFile): ScriptConfigurationWithSdk? = data[virtualFile]

    fun removeConfiguration(virtualFile: VirtualFile) {
        importedScripts.removeAll(virtualFile)
        data.remove(virtualFile)
    }

    override suspend fun create(virtualFile: VirtualFile, definition: ScriptDefinition): ScriptConfigurationWithSdk? {
        val mainKtsConfiguration = resolveMainKtsConfiguration(virtualFile, definition)
        if (mainKtsConfiguration.importedScripts.isNotEmpty()) {
            importedScripts.putAll(virtualFile, mainKtsConfiguration.importedScripts)
            resolveDeeply(mainKtsConfiguration.importedScripts)
        }

        data[virtualFile] = mainKtsConfiguration

        return data[virtualFile]
    }

    suspend fun resolveDeeply(scripts: Collection<VirtualFile>) {
        for (script in scripts) {
            val scriptSource = VirtualFileScriptSource(script)
            val definition = findScriptDefinition(project, scriptSource)

            val resolver = definition.getConfigurationResolver(project)
            resolver.get(script) ?: resolver.create(script, definition)
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

        val result = withBackgroundProgress(project, title = KotlinBaseScriptingBundle.message("progress.title.dependency.resolution", mainKts.name)) {
            reportProgress {
                try {
                    reporter = it
                    smartReadAction(project) {
                        refineScriptCompilationConfiguration(scriptSource, definition, project, providedConfiguration)
                    }
                } finally {
                    reporter = null
                }
            }
        }

        return ScriptConfigurationWithSdk(result, sdk)
    }

    override suspend fun updateWorkspaceModel(configurationPerFile: Map<VirtualFile, ScriptConfigurationWithSdk>) {
        val mainKtsScript = configurationPerFile.entries.firstOrNull()?.key ?: return
        for (it in getImportedScripts(mainKtsScript)) {
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
            val libraryDependencies = storageToUpdate.getDependencies(configuration, source, definition, locationName)

            val sdkDependency = configurationWithSdk.sdk?.let { SdkDependency(SdkId(it.name, it.sdkType.name)) }
            val allDependencies = listOfNotNull(sdkDependency) + libraryDependencies

            val scriptModuleId = ModuleId("${KOTLIN_SCRIPTS_MODULE_NAME}.${definition.name}.$locationName")
            val existingModule = storageToUpdate.resolve(scriptModuleId)
            if (existingModule == null) {
                storageToUpdate.addEntity(
                    ModuleEntity.Companion(scriptModuleId.name, allDependencies, source)
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
        wrapper: ScriptCompilationConfigurationWrapper, source: KotlinScriptEntitySource, definition: ScriptDefinition, locationName: String
    ): List<LibraryDependency> {
        val definitionDependency = getOrCreateDefinitionDependency(definition, project, source)
        val storage = this

        return buildList {
            if (wrapper.isUberDependencyAllowed()) {
                val classes = wrapper.configuration?.get(ScriptCompilationConfiguration.dependencies).findVirtualFiles()
                val sources =
                    wrapper.configuration?.get(ScriptCompilationConfiguration.ide.dependenciesSources).findVirtualFiles()

                addIfNotNull(storage.createUberDependency(locationName, classes, sources, source))
            } else {
                add(definitionDependency)

                val classes = wrapper.configuration?.get(ScriptCompilationConfiguration.dependencies)?.drop(1).findVirtualFiles()
                addAll(
                    classes.map {
                        getOrCreateLibrary(it.name, listOf(it.compiledLibraryRoot(project)), source)
                    })
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
        private val COMPARATOR = Comparator<VirtualFile> { left, right -> left.path.compareTo(right.path) }

        @JvmStatic
        fun getInstance(project: Project): MainKtsScriptConfigurationProvider = project.service()
    }

    class MainKtsKotlinScriptEntitySource(override val virtualFileUrl: VirtualFileUrl?) : KotlinScriptEntitySource(virtualFileUrl)
}