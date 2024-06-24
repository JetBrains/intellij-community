package org.jetbrains.kotlin.idea.core.script

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager.Companion.toVfsRoots
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import java.nio.file.Path
import kotlin.script.experimental.api.valueOrNull
import kotlin.time.measureTime

const val KOTLIN_SCRIPTS_MODULE_NAME = "Kotlin Scripts"

data class KotlinScriptEntitySourceK2(override val virtualFileUrl: VirtualFileUrl) : EntitySource

suspend fun Project.createScriptModules(scripts: Set<ScriptModel>, storage: MutableEntityStorage? = null) {
    val workspaceModel = workspaceModel
    val storageSnapshot = workspaceModel.currentSnapshot
    val tempStorage = MutableEntityStorage.from(storageSnapshot)

    val duration = measureTime { createPureScriptModules(this, scripts, storage ?: tempStorage) }

    scriptingDebugLog { "createPureScriptModules duration = $duration" }

    if (storage == null) {
        workspaceModel.update("Updating kotlin scripts modules") {
            it.applyChangesFrom(tempStorage)
        }
    }
}

private fun createPureScriptModules(project: Project, scriptPaths: Set<ScriptModel>, storage: MutableEntityStorage) {
    val projectPath = project.basePath?.let { Path.of(it) } ?: return

    val sourcesToUpdate: MutableSet<KotlinScriptEntitySourceK2> = mutableSetOf<KotlinScriptEntitySourceK2>()
    val updatedStorage = MutableEntityStorage.create()

    for (scriptFile in scriptPaths.map { it.virtualFile }) {
        if (ScratchUtil.isScratch(scriptFile)) {
            continue
        }

        val basePath = projectPath.toFile()
        val file = Path.of(scriptFile.path).toFile()
        val relativeLocation = FileUtil.getRelativePath(basePath, file) ?: continue

        val definition = K2ScriptDefinitionProvider.getInstance(project).findDefinition(VirtualFileScriptSource(scriptFile))

        val definitionName = definition?.name ?: continue

        val definitionScriptModuleName = "$KOTLIN_SCRIPTS_MODULE_NAME.$definitionName"
        val locationName = relativeLocation.replace(VfsUtilCore.VFS_SEPARATOR_CHAR, ':')
        val moduleName = "$definitionScriptModuleName.$locationName"

        val source = KotlinScriptEntitySourceK2(scriptFile.toVirtualFileUrl(WorkspaceModel.getInstance(project).getVirtualFileUrlManager()))
        sourcesToUpdate += source
        val dependencies = updatedStorage.createDependencies(moduleName, scriptFile, project, source)

        updatedStorage.addEntity(ModuleEntity(moduleName, dependencies, source))
    }

    storage.replaceBySource({ entitySource -> entitySource in sourcesToUpdate }, updatedStorage)
}

private data class DependenciesFiles(
    val dependenciesClassFiles: List<VirtualFile>,
    val dependenciesSourceFiles: List<VirtualFile>,
    val sdk: SdkDependency?
) {
    companion object {
        val EMPTY = DependenciesFiles(listOf(), listOf(), null)
    }
}

private fun getDependenciesFiles(scriptFile: VirtualFile, project: Project): DependenciesFiles {
    val dependenciesProvider = K2ScriptDependenciesProvider.getInstance(project)
    val wrapper = dependenciesProvider.getConfiguration(scriptFile)?.valueOrNull() ?: return DependenciesFiles.EMPTY

    val sdk = dependenciesProvider.getScriptSdk(scriptFile)?.let { SdkDependency(SdkId(it.name, it.sdkType.name)) }

    val dependenciesClassFiles = toVfsRoots(wrapper.dependenciesClassPath)
    val dependenciesSourceFiles = toVfsRoots(wrapper.dependenciesSources)

    return DependenciesFiles(dependenciesClassFiles, dependenciesSourceFiles, sdk)
}

fun MutableEntityStorage.createDependencies(
    moduleName: String,
    scriptFile: VirtualFile,
    project: Project,
    entitySource: EntitySource
): List<ModuleDependencyItem> {
    val (dependenciesClassFiles, dependenciesSourceFiles, sdk) = getDependenciesFiles(scriptFile, project)

    val fileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()

    val classRoots = dependenciesClassFiles.map {
        LibraryRoot(it.toVirtualFileUrl(fileUrlManager), LibraryRootTypeId.COMPILED)
    }

    val sourceRoots = dependenciesSourceFiles.map {
        LibraryRoot(it.toVirtualFileUrl(fileUrlManager), LibraryRootTypeId.SOURCES)
    }

    val libraryTableId = LibraryTableId.ModuleLibraryTableId(moduleId = ModuleId(moduleName))
    val dependencyLibrary =
        addEntity(LibraryEntity("$moduleName dependencies", libraryTableId, classRoots + sourceRoots, entitySource))

    return listOfNotNull(LibraryDependency(dependencyLibrary.symbolicId, false, DependencyScope.COMPILE), sdk)
}
