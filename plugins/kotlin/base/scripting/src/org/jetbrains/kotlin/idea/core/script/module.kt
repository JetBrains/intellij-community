package org.jetbrains.kotlin.idea.core.script

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager.Companion.toVfsRoots
import org.jetbrains.kotlin.idea.core.script.k2.K2ScriptDefinitionProvider
import org.jetbrains.kotlin.idea.core.script.k2.ScriptDependenciesData
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import java.nio.file.Path
import kotlin.script.experimental.api.valueOrNull

const val KOTLIN_SCRIPTS_MODULE_NAME = "Kotlin Scripts"

data class KotlinScriptEntitySourceK2(override val virtualFileUrl: VirtualFileUrl) : EntitySource

fun creteScriptModules(project: Project, dependenciesData: ScriptDependenciesData, storage: MutableEntityStorage) {
    val projectPath = project.basePath?.let { Path.of(it) } ?: return

    val sourcesToUpdate: MutableSet<KotlinScriptEntitySourceK2> = mutableSetOf()
    val updatedStorage = MutableEntityStorage.create()

    for ((scriptFile, configurationWrapper) in dependenciesData.configurations) {
        if (ScratchUtil.isScratch(scriptFile)) {
            continue
        }

        val configuration = configurationWrapper.valueOrNull() ?: continue

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

        val sdkDependency =
            configuration.javaHome?.toPath()
                ?.let { dependenciesData.sdks[it] }
                ?.let { SdkDependency(SdkId(it.name, it.sdkType.name)) }

        val dependencies = listOfNotNull(
            updatedStorage.createLibraryDependency(moduleName, project, source, configuration),
            sdkDependency
        )

        updatedStorage.addEntity(ModuleEntity(moduleName, dependencies, source))
    }

    storage.replaceBySource({ entitySource -> entitySource in sourcesToUpdate }, updatedStorage)
}

fun MutableEntityStorage.createLibraryDependency(
    moduleName: String,
    project: Project,
    entitySource: EntitySource,
    configurationWrapper: ScriptCompilationConfigurationWrapper
): LibraryDependency {

    val roots = getLibraryRoots(project, configurationWrapper)
    val libraryTableId = LibraryTableId.ModuleLibraryTableId(moduleId = ModuleId(moduleName))
    val dependencyEntity =
        addEntity(LibraryEntity("$moduleName dependencies", libraryTableId, roots, entitySource))

    return LibraryDependency(dependencyEntity.symbolicId, false, DependencyScope.COMPILE)
}

private fun getLibraryRoots(
    project: Project,
    configurationWrapper: ScriptCompilationConfigurationWrapper
): List<LibraryRoot> {
    val fileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()

    val roots = buildList {
        toVfsRoots(configurationWrapper.dependenciesClassPath).mapTo(this) {
            LibraryRoot(it.toVirtualFileUrl(fileUrlManager), LibraryRootTypeId.COMPILED)
        }
    }

    return roots
}
