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
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.containers.addIfNotNull
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.idea.core.script.*
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager.Companion.toVfsRoots
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
import java.nio.file.Path
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
        val scripts = scriptUrls.mapNotNull { manager.findByUrl(it)?.virtualFile }.toSet()

        DependencyResolutionService.getInstance(project).resolveInBackground {
            updateDependenciesAndCreateModules(scripts.map { BaseScriptModel(it) })
        }
    }

    override fun getConfiguration(virtualFile: VirtualFile): ResultWithDiagnostics<ScriptCompilationConfigurationWrapper>? {
        loadPersistedConfigurations
        val current = data.get().configurations[virtualFile]

        if (current != null) return current

        if (virtualFile.hasNoDependencies()) {
            DependencyResolutionService.getInstance(project).resolveInBackground {
                handleNoDependencies(virtualFile)
            }

            return data.get().configurations[virtualFile]
        }

        return null
    }

    private suspend fun handleNoDependencies(script: VirtualFile) {
        val sourceCode = VirtualFileScriptSource(script)
        val definition = findScriptDefinition(project, sourceCode)
        val result = ResultWithDiagnostics.Success(ScriptCompilationConfigurationWrapper.FromCompilationConfiguration(sourceCode, definition.compilationConfiguration))

        val projectSdk = ProjectJdkTable.getInstance().allJdks.firstOrNull()

        val scriptConfigurations = ScriptConfigurations(mapOf(script to result), sdks = projectSdk?.homePath?.let { mapOf(Path.of(it) to projectSdk) } ?: emptyMap())
        data.getAndAccumulate(scriptConfigurations) { left, right -> left + right }

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

        val scriptConfigurations =
            ScriptConfigurations(configurations, sdks = projectSdk?.homePath?.let { mapOf(Path.of(it) to projectSdk) } ?: emptyMap())

        data.getAndAccumulate(scriptConfigurations) { left, right -> left + right }
        updatePersistentState()
    }

    private fun updatePersistentState() {
        val loadedScripts = data.get().configurations.filter {
            it.value is ResultWithDiagnostics.Success
        }.map {
            it.key.url
        }

        ScriptsWithLoadedDependenciesStorage.getInstance(project).loadState(ScriptsWithLoadedDependenciesState(loadedScripts))
    }

    override suspend fun updateModules(storage: MutableEntityStorage?) {
        val updatedStorage = getUpdatedStorage(
            project, data.get()
        ) { KotlinDependentScriptModuleEntitySource(it) }

        project.workspaceModel.update("updating .main.kts modules") { targetStorage ->
            targetStorage.replaceBySource(
                { it is KotlinDependentScriptModuleEntitySource }, updatedStorage
            )
        }
    }

    private fun VirtualFile.hasNoDependencies(): Boolean {
        val ktFile = this.toPsiFile(project) as? KtFile ?: return false
        return ktFile.annotationEntries.none { it.text.contains("DependsOn") } //TODO use analyze for this
    }

    private fun getUpdatedStorage(
        project: Project,
        configurationsData: ScriptConfigurations,
        entitySourceSupplier: (virtualFileUrl: VirtualFileUrl) -> KotlinScriptEntitySource,
    ): MutableEntityStorage {
        val updatedStorage = MutableEntityStorage.create()
        val projectPath = project.basePath?.let { Path.of(it) } ?: return updatedStorage

        for ((scriptFile, configurationWrapper) in configurationsData.configurations) {
            val configuration = configurationWrapper.valueOrNull() ?: continue

            val basePath = projectPath.toFile()
            val file = Path.of(scriptFile.path).toFile()
            val relativeLocation = FileUtil.getRelativePath(basePath, file) ?: continue

            val definition = findScriptDefinition(project, VirtualFileScriptSource(scriptFile))

            val definitionScriptModuleName = "$KOTLIN_SCRIPTS_MODULE_NAME.${definition.name}"
            val locationName = relativeLocation.replace(VfsUtilCore.VFS_SEPARATOR_CHAR, ':')
            val moduleName = "$definitionScriptModuleName.$locationName"

            val sdkDependency = configurationsData.sdks.values.firstOrNull()
                ?.let { SdkDependency(SdkId(it.name, it.sdkType.name)) }

            val virtualFileManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
            val source = entitySourceSupplier(scriptFile.toVirtualFileUrl(virtualFileManager))
            val libraryDependencies =
                getLibraryDependencies(configuration, updatedStorage, source, definition, locationName)

            val allDependencies = listOfNotNull(sdkDependency) + libraryDependencies

            updatedStorage.addEntity(ModuleEntity(moduleName, allDependencies, source))
        }

        return updatedStorage
    }

    private fun getLibraryDependencies(
        configuration: ScriptCompilationConfigurationWrapper,
        storage: MutableEntityStorage,
        source: KotlinScriptEntitySource,
        definition: ScriptDefinition,
        locationName: String
    ): List<LibraryDependency> {
        val fileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()

        val updatedFactory = LibraryDependencyFactory(fileUrlManager, storage)
        val definitionLibraryEntity = storage.getDefinitionLibraryEntity(definition, project, KotlinDependentScriptModuleEntitySource(null))
        val rootsToSkip = definitionLibraryEntity?.roots?.map { it.url }?.toSet() ?: emptySet()

        val classes = toVfsRoots(configuration.dependenciesClassPath).filterNot { it.toVirtualFileUrl(fileUrlManager) in rootsToSkip }.sortedBy { it.name }

        return buildList {
            addIfNotNull(definitionLibraryEntity?.let { LibraryDependency(it.symbolicId, false, DependencyScope.COMPILE) })

            if (configuration.isUberDependencyAllowed()) {
                val sources = toVfsRoots(configuration.dependenciesSources).filterNot { it.toVirtualFileUrl(fileUrlManager) in rootsToSkip }.sortedBy { it.name }
                addIfNotNull(storage.createUberDependency(locationName, classes, sources, source))

            } else {
                addAll(classes.map { updatedFactory.get(it, source) })
            }
        }
    }

    private fun MutableEntityStorage.createUberDependency(
        locationName: String,
        classes: List<VirtualFile>,
        sources: List<VirtualFile>,
        source: KotlinScriptEntitySource,
    ): LibraryDependency? {
        if (classes.isEmpty() && sources.isEmpty()) {
            return null
        }
        val fileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()

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

class KotlinDependentScriptModuleEntitySource(override val virtualFileUrl: VirtualFileUrl?) :
    KotlinScriptEntitySource(virtualFileUrl)