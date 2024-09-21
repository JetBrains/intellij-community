package org.jetbrains.kotlin.idea.core.script

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager.Companion.toVfsRoots
import org.jetbrains.kotlin.idea.core.script.k2.ScriptDependenciesData
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import java.nio.file.Path
import kotlin.script.experimental.api.valueOrNull

const val KOTLIN_SCRIPTS_MODULE_NAME = "Kotlin Scripts"

open class KotlinScriptEntitySource(override val virtualFileUrl: VirtualFileUrl?) : EntitySource
open class KotlinScriptLibraryEntitySource : KotlinScriptEntitySource(null)

@ApiStatus.Internal
fun getUpdatedStorage(
    project: Project,
    dependenciesData: ScriptDependenciesData,
    moduleEntitySourceSupplier: (virtualFileUrl: VirtualFileUrl) -> KotlinScriptEntitySource,
    libraryEntitySourceSupplier: () -> KotlinScriptLibraryEntitySource,
): MutableEntityStorage {
    val updatedStorage = MutableEntityStorage.create()

    val projectPath = project.basePath?.let { Path.of(it) } ?: return updatedStorage

    val fileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
    val libraryDependencyFactory = LibraryDependencyFactory(fileUrlManager, updatedStorage, libraryEntitySourceSupplier)

    for ((scriptFile, configurationWrapper) in dependenciesData.configurations) {
        val configuration = configurationWrapper.valueOrNull() ?: continue

        val basePath = projectPath.toFile()
        val file = Path.of(scriptFile.path).toFile()
        val relativeLocation = FileUtil.getRelativePath(basePath, file) ?: continue

        val definitionName = findScriptDefinition(project, VirtualFileScriptSource(scriptFile)).name

        val definitionScriptModuleName = "$KOTLIN_SCRIPTS_MODULE_NAME.$definitionName"
        val locationName = relativeLocation.replace(VfsUtilCore.VFS_SEPARATOR_CHAR, ':')
        val moduleName = "$definitionScriptModuleName.$locationName"

        val sdkDependency =
            configuration.javaHome?.toPath()
                ?.let { dependenciesData.sdks[it] }
                ?.let { SdkDependency(SdkId(it.name, it.sdkType.name)) }

        val libraryDependencies = toVfsRoots(configuration.dependenciesClassPath)
            .map { libraryDependencyFactory.get(it) }

        val allDependencies = libraryDependencies + listOfNotNull(sdkDependency)

        val source = moduleEntitySourceSupplier(scriptFile.toVirtualFileUrl(fileUrlManager))
        updatedStorage.addEntity(ModuleEntity(moduleName, allDependencies, source))
    }

    return updatedStorage
}

private class LibraryDependencyFactory(
    private val fileUrlManager: VirtualFileUrlManager,
    private val entityStorage: MutableEntityStorage,
    private val libraryEntitySourceSupplier: () -> KotlinScriptLibraryEntitySource
) {
    private val cache = HashMap<VirtualFile, LibraryDependency>()
    private val nameCache = HashMap<String, Int>()

    fun get(file: VirtualFile): LibraryDependency {
        return cache.computeIfAbsent(file) {
            createLibrary(file)
        }
    }

    fun createLibrary(file: VirtualFile): LibraryDependency {
        val fileUrl = file.toVirtualFileUrl(fileUrlManager)
        val libraryRoot = LibraryRoot(fileUrl, LibraryRootTypeId.COMPILED)

        val fileName = file.name

        // Module names for duplicating file names will have a number suffix (such as '.2')
        val libraryNameSuffixNumber = nameCache.compute(file.name) { _, oldValue -> if (oldValue != null) oldValue + 1 else 1 }!!
        val libraryNameSuffix = if (libraryNameSuffixNumber > 1) ".$libraryNameSuffixNumber" else ""
        val libraryName = "$fileName$libraryNameSuffix"

        val libraryTableId = LibraryTableId.ProjectLibraryTableId
        val libraryEntity = LibraryEntity(libraryName, libraryTableId, listOf(libraryRoot), libraryEntitySourceSupplier())
        val dependencyEntity = entityStorage.addEntity(libraryEntity)
        return LibraryDependency(dependencyEntity.symbolicId, false, DependencyScope.COMPILE)
    }
}