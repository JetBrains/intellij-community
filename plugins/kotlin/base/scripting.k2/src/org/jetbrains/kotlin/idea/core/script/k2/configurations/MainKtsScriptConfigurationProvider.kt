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
import com.intellij.platform.util.progress.reportProgressScope
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.idea.base.scripting.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.KOTLIN_SCRIPTS_MODULE_NAME
import org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationWithSdk
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptRefinedConfigurationResolver
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptWorkspaceModelManager
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.isNonScript
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm

@Service(Service.Level.PROJECT)
class MainKtsScriptConfigurationProvider(val project: Project, val coroutineScope: CoroutineScope) : ScriptRefinedConfigurationResolver,
                                                                                                     ScriptWorkspaceModelManager {
    private val data = ConcurrentHashMap<VirtualFile, ScriptConfigurationWithSdk>()
    private val visitedScripts = TreeMultimap.create(COMPARATOR, COMPARATOR)
    private val visitedScriptsTraverser = Traverser.forTree<VirtualFile> { visitedScripts.get(it) }

    fun getImportedScripts(mainKts: VirtualFile): List<VirtualFile> = visitedScriptsTraverser.breadthFirst(mainKts) - mainKts

    var reporter: ProgressReporter? = null
        private set

    override fun get(virtualFile: VirtualFile): ScriptConfigurationWithSdk? = data[virtualFile]

    override fun remove(virtualFile: VirtualFile) {
        visitedScripts.removeAll(virtualFile)
        data.remove(virtualFile)
    }

    override suspend fun create(virtualFile: VirtualFile, definition: ScriptDefinition): ScriptConfigurationWithSdk? {
        val mainKtsConfiguration = resolveMainKtsConfiguration(virtualFile, definition)
        val scriptsToResolve = mainKtsConfiguration.importedScripts - visitedScripts.keys()
        if (scriptsToResolve.isNotEmpty()) {
            visitedScripts.putAll(virtualFile, scriptsToResolve)
            resolveDeeply(scriptsToResolve)
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
            reportProgressScope {
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

    private val ScriptConfigurationWithSdk.importedScripts: List<VirtualFile>
        get() {
            val importedScripts = this.scriptConfiguration.valueOrNull()?.importedScripts ?: return emptyList()
            return importedScripts.mapNotNull { (it as? VirtualFileScriptSource)?.virtualFile }.filterNot { it.isNonScript() }
        }

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
            val libraryDependencies = storageToUpdate.addDependencies(configuration, source, definition, locationName, project)

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

    companion object {
        private val COMPARATOR = Comparator<VirtualFile> { left, right -> left.path.compareTo(right.path) }

        @JvmStatic
        fun getInstance(project: Project): MainKtsScriptConfigurationProvider = project.service()
    }

    class MainKtsKotlinScriptEntitySource(override val virtualFileUrl: VirtualFileUrl?) : KotlinScriptEntitySource(virtualFileUrl)
}