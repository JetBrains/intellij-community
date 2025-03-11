// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.psi.util.childrenOfType
import com.intellij.util.containers.addIfNotNull
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.amper.dependency.resolution.LocalM2RepositoryFinder
import org.jetbrains.kotlin.idea.core.script.*
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager.Companion.toVfsRoots
import org.jetbrains.kotlin.idea.core.script.ucache.relativeName
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.ScriptReportSink
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import java.io.File
import java.nio.file.Files
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm


class DependentScriptConfigurationsSource(override val project: Project, val coroutineScope: CoroutineScope) :
    ScriptConfigurationsSource<BaseScriptModel>(project) {

    private val m2LocalRepositoryPath = LocalM2RepositoryFinder.findPath()

    override fun getConfigurationWithSdk(virtualFile: VirtualFile): ScriptConfigurationWithSdk? {
        data.get()[virtualFile]?.let { return it }

        val ktFile = virtualFile.toPsiFile(project) as? KtFile ?: return null
        if (ktFile.hasNoDependencies()) {
            runBlockingMaybeCancellable {
                DependencyResolutionService.getInstance(project).resolveInBackground {
                    handleNoDependencies(virtualFile)
                }
            }
        } else if (ktFile.dependenciesExistLocally()) {
            runBlockingMaybeCancellable {
                DependencyResolutionService.getInstance(project).resolveInBackground {
                    updateDependenciesAndCreateModules(listOf(BaseScriptModel(virtualFile)))
                }
            }
        }

        return data.get()[virtualFile]
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

    override fun getDefinitions(): Sequence<ScriptDefinition>? =
        project.scriptDefinitionsSourceOfType<MainKtsScriptDefinitionSource>()?.definitions

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

    override suspend fun updateModules(storage: MutableEntityStorage?) {
        val updatedStorage = getUpdatedStorage(project, data.get())

        project.workspaceModel.update("updating .main.kts modules") { targetStorage ->
            targetStorage.replaceBySource(
                { it is KotlinDependentScriptModuleEntitySource }, updatedStorage
            )
        }
    }

    private fun KtFile.hasNoDependencies() = annotationEntries.none { it.text.startsWith("@file:DependsOn") }

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
            val libraryDependencies = updatedStorage.addLibraryDependencies(configuration, source, definition, locationName, libraryFactory)

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

        val classes = toVfsRoots(configurationWrapper.dependenciesClassPath).filterNot { it.toVirtualFileUrl(urlManager) in rootsToSkip }
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


    private fun KtFile.dependenciesExistLocally(): Boolean {
        val artifactLocations = script?.annotationEntries?.filter { it.text.startsWith("@file:DependsOn") }?.mapNotNull {
            it.childrenOfType<KtValueArgumentList>().singleOrNull()
                ?.arguments?.singleOrNull()
                ?.stringTemplateExpression?.childrenOfType<KtLiteralStringTemplateEntry>()
                ?.singleOrNull()
                ?.text
        } ?: return true

        return artifactLocations.all {
            val splitted = it.split(":")
            val group = splitted[0]
            val module = splitted[1]
            val version = splitted[2]
            val dependencyPath = m2LocalRepositoryPath.resolve(
                "${group.split('.').joinToString("/")}/$module/$version"
            )
            Files.exists(dependencyPath)
        }
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
