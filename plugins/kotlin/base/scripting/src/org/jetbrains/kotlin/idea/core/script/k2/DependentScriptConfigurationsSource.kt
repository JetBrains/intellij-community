// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.*
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.containers.addIfNotNull
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.idea.core.script.*
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager.Companion.toVfsRoots
import org.jetbrains.kotlin.idea.core.script.ucache.relativeName
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.ScriptReportSink
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import java.io.File
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm


class DependentScriptConfigurationsSource(override val project: Project, val coroutineScope: CoroutineScope) :
    ScriptConfigurationsSource<BaseScriptModel>(project) {

    private val loadPersistedConfigurations by lazy {
        val scriptUrls = ScriptsWithLoadedDependenciesStorage.getInstance(project).state.scripts
        val manager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
        val scripts = scriptUrls.mapNotNull { manager.getOrCreateFromUrl(it).virtualFile }.toSet()

        if (scripts.isEmpty()) return@lazy
        DependencyResolutionService.getInstance(project).resolveInBackground {
            updateDependenciesAndCreateModules(scripts.map { BaseScriptModel(it) })
        }
    }

    override fun getConfigurationWithSdk(virtualFile: VirtualFile): ScriptConfigurationWithSdk? {
        loadPersistedConfigurations
        val current = data.get()[virtualFile]

        if (current != null) return current

        if (virtualFile.hasNoDependencies()) {
            DependencyResolutionService.getInstance(project).resolveInBackground {
                handleNoDependencies(virtualFile)
            }

            return data.get()[virtualFile]
        }

        return null
    }

    private suspend fun handleNoDependencies(script: VirtualFile) {
        val sourceCode = VirtualFileScriptSource(script)
        val definition = findScriptDefinition(project, sourceCode)
        val result = ResultWithDiagnostics.Success(
            ScriptCompilationConfigurationWrapper.FromCompilationConfiguration(
                sourceCode, definition.compilationConfiguration
            )
        )

        val projectSdk = ProjectJdkTable.getInstance().allJdks.firstOrNull()

        val entry = mapOf(script to ScriptConfigurationWithSdk(result, projectSdk))
        data.getAndAccumulate(entry) { left, right -> left + right }

        ScriptConfigurationsProviderImpl.getInstance(project).notifySourceUpdated()

        updateModules()

        val filesInEditors = readAction {
            FileEditorManager.getInstance(project).allEditors.mapTo(hashSetOf(), FileEditor::getFile)
        }

        ScriptDependenciesModificationTracker.getInstance(project).incModificationCount()
        HighlightingSettingsPerFile.getInstance(project).incModificationCount()

        if (script in filesInEditors) {
            if (project.isOpen && !project.isDisposed) {
                readAction {
                    val ktFile = script.toPsiFile(project) as? KtFile ?: error("Cannot convert to PSI file: $script")
                    DaemonCodeAnalyzer.getInstance(project).restart(ktFile)
                }
            }
        }
    }

    override fun getScriptDefinitionsSource(): ScriptDefinitionsSource? =
        project.scriptDefinitionsSourceOfType<MainKtsScriptDefinitionSource>()

    override suspend fun updateConfigurations(scripts: Iterable<BaseScriptModel>) {
        val sdk = ProjectJdkTable.getInstance().allJdks.firstOrNull()

        val configurations = scripts.associate {
            val scriptSource = VirtualFileScriptSource(it.virtualFile)
            val definition = findScriptDefinition(project, scriptSource)

            val providedConfiguration = sdk?.homePath?.let { jdkHome ->
                definition.compilationConfiguration.with {
                    jvm.jdkHome(File(jdkHome))
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

    private fun updatePersistentState(configurations: Map<VirtualFile, ScriptConfigurationWithSdk>) {
        val safeToLoadScripts = configurations.filterValues {
            it.scriptConfiguration is ResultWithDiagnostics.Success
        }.map {
            it.key.url
        }

        ScriptsWithLoadedDependenciesStorage.getInstance(project).loadState(ScriptsWithLoadedDependenciesState(safeToLoadScripts))
    }

    override suspend fun updateModules(storage: MutableEntityStorage?) {
        val updatedStorage = getUpdatedStorage(project, data.get())

        project.workspaceModel.update("updating .main.kts modules") { targetStorage ->
            targetStorage.replaceBySource(
                { it is KotlinDependentScriptModuleEntitySource }, updatedStorage
            )
        }

        updatePersistentState(data.get())
    }

    private fun VirtualFile.hasNoDependencies(): Boolean {
        val ktFile = this.toPsiFile(project) as? KtFile ?: return false
        return ktFile.annotationEntries.none { it.text.contains("DependsOn") } //TODO use analyze for this
    }

    private suspend fun getUpdatedStorage(
        project: Project,
        configurationsData: Map<VirtualFile, ScriptConfigurationWithSdk>,
    ): MutableEntityStorage {
        val virtualFileManager = project.serviceAsync<WorkspaceModel>().getVirtualFileUrlManager()

        val libraryFactory = LibraryDependencyFactory(virtualFileManager) {
            KotlinDependentScriptModuleEntitySource(it)
        }

        val updatedStorage = MutableEntityStorage.create()

        for ((scriptFile, configurationWithSdk) in configurationsData) {
            val configuration = configurationWithSdk.scriptConfiguration.valueOrNull() ?: continue

            val definition = findScriptDefinition(project, VirtualFileScriptSource(scriptFile))

            val definitionScriptModuleName = "$KOTLIN_SCRIPTS_MODULE_NAME.${definition.name}"
            val locationName = scriptFile.relativeName(project).replace(VfsUtilCore.VFS_SEPARATOR_CHAR, ':')

            val source = KotlinDependentScriptModuleEntitySource(scriptFile.toVirtualFileUrl(virtualFileManager))
            val libraryDependencies =
                updatedStorage.addLibraryDependencies(configuration, source, definition, locationName, libraryFactory)

            val sdkDependency = configurationWithSdk.sdk?.let { SdkDependency(SdkId(it.name, it.sdkType.name)) }
            val allDependencies = listOfNotNull(sdkDependency) + libraryDependencies

            updatedStorage.addEntity(ModuleEntity("$definitionScriptModuleName.$locationName", allDependencies, source))
        }

        return updatedStorage
    }

    private suspend fun MutableEntityStorage.addLibraryDependencies(
        configurationWrapper: ScriptCompilationConfigurationWrapper,
        source: KotlinScriptEntitySource,
        definition: ScriptDefinition,
        locationName: String,
        libraryFactory: LibraryDependencyFactory
    ): List<LibraryDependency> {
        val definitionLibraryEntity = getDefinitionLibraryEntity(definition, project, source)
        val rootsToSkip = definitionLibraryEntity?.roots?.map { it.url }?.toSet() ?: emptySet()

        val urlManager = project.serviceAsync<WorkspaceModel>().getVirtualFileUrlManager()
        val storage = this

        val classes =
            toVfsRoots(configurationWrapper.dependenciesClassPath).filterNot { it.toVirtualFileUrl(urlManager) in rootsToSkip }
                .sortedBy { it.name }.distinct()

        return buildList {
            addIfNotNull(definitionLibraryEntity?.let { LibraryDependency(it.symbolicId, false, DependencyScope.COMPILE) })

            if (configurationWrapper.isUberDependencyAllowed()) {
                val sources =
                    toVfsRoots(configurationWrapper.dependenciesSources).filterNot { it.toVirtualFileUrl(urlManager) in rootsToSkip }
                        .sortedBy { it.name }
                addIfNotNull(storage.createUberDependency(locationName, classes, sources, source))

            } else {
                addAll(classes.map {
                    LibraryDependency(libraryFactory.get(it, storage).symbolicId, false, DependencyScope.COMPILE)
                })
            }
        }
    }

    private suspend fun MutableEntityStorage.createUberDependency(
        locationName: String,
        classes: List<VirtualFile>,
        sources: List<VirtualFile>,
        source: KotlinScriptEntitySource,
    ): LibraryDependency? {
        if (classes.isEmpty() && sources.isEmpty()) return null

        val fileUrlManager = project.serviceAsync<WorkspaceModel>().getVirtualFileUrlManager()

        val classRoots = classes.map {
            LibraryRoot(it.toVirtualFileUrl(fileUrlManager), LibraryRootTypeId.COMPILED)
        }

        val sourceRoots = sources.map {
            LibraryRoot(it.toVirtualFileUrl(fileUrlManager), LibraryRootTypeId.SOURCES)
        }

        val dependencyLibrary =
            addEntity(LibraryEntity("$locationName dependencies", LibraryTableId.ProjectLibraryTableId, classRoots + sourceRoots, source))
        return LibraryDependency(dependencyLibrary.symbolicId, false, DependencyScope.COMPILE)
    }

    private fun ScriptCompilationConfigurationWrapper.isUberDependencyAllowed(): Boolean {
        return dependenciesSources.size + dependenciesClassPath.size < 20
    }
}

@Service(Service.Level.PROJECT)
@State(name = "ScriptWithLoadedDependenciesStorage", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
private class ScriptsWithLoadedDependenciesStorage :
    SimplePersistentStateComponent<ScriptsWithLoadedDependenciesState>(ScriptsWithLoadedDependenciesState()) {

    companion object {
        fun getInstance(project: Project): ScriptsWithLoadedDependenciesStorage = project.service()
    }
}

internal class ScriptsWithLoadedDependenciesState() : BaseState() {
    var scripts by list<String>()

    constructor(scripts: Collection<String>) : this() {
        this.scripts = scripts.toMutableList()
    }
}

class KotlinDependentScriptModuleEntitySource(override val virtualFileUrl: VirtualFileUrl?) : KotlinScriptEntitySource(virtualFileUrl)

private class LibraryDependencyFactory(
    private val fileUrlManager: VirtualFileUrlManager,
    private val entitySourceSupplier: (virtualFileUrl: VirtualFileUrl) -> KotlinScriptEntitySource,
) {
    private val cache = HashMap<VirtualFile, LibraryEntity>()

    fun get(file: VirtualFile, storage: MutableEntityStorage): LibraryEntity {
        return cache.computeIfAbsent(file) {
            storage.createLibrary(file)
        }
    }

    fun MutableEntityStorage.createLibrary(file: VirtualFile): LibraryEntity {
        val fileUrl = file.toVirtualFileUrl(fileUrlManager)
        val libraryRoot = LibraryRoot(fileUrl, LibraryRootTypeId.COMPILED)

        val libraryEntity =
            LibraryEntity(file.name, LibraryTableId.ProjectLibraryTableId, listOf(libraryRoot), entitySourceSupplier(fileUrl))
        return addEntity(libraryEntity)
    }
}
