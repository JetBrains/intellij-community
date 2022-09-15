// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.ucache

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.NonClasspathDirectoriesScope
import com.intellij.util.applyIf
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.virtualFile
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.api.LibraryRoot
import com.intellij.workspaceModel.storage.bridgeEntities.api.LibraryRootTypeId
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.psi.KtFile

val scriptsAsEntities: Boolean = Registry.`is`("kotlin.scripts.as.entities", false)


/**
 *  ScriptConfigurationManager or WorkspaceModel based on 'scriptsAsEntities'
 */

fun getScriptDependenciesClassFilesScope(project: Project, ktFile: KtFile): GlobalSearchScope {
    require(ktFile.isScript()) { "argument must be a script: ${ktFile.virtualFilePath}" }

    return if (scriptsAsEntities) {
        val entityStorage = WorkspaceModel.getInstance(project).entityStorage.current
        val scriptEntity = entityStorage.resolve(ScriptId(ktFile.virtualFilePath))

        if (scriptEntity == null) {
            // WorkspaceModel doesn't know about the file yet. But once the latest sync is over it will.
            // We cannot synchronously call its syncScriptEntities() because it requires platform write-lock acquisition and this function
            // is called under platform read-lock.
            return ScriptConfigurationManager.getInstance(project).getScriptDependenciesClassFilesScope(ktFile.virtualFile)
        }

        NonClasspathDirectoriesScope.compose(scriptEntity.listDependencies(LibraryRootTypeId.COMPILED))
    } else {
        return ScriptConfigurationManager.getInstance(project).getScriptDependenciesClassFilesScope(ktFile.virtualFile)
    }
}

fun getAllScriptsDependenciesClassFilesScope(project: Project): GlobalSearchScope {
    return if (scriptsAsEntities) {
        listAllScriptDependenciesScope(project, LibraryRootTypeId.COMPILED)
    } else {
        return ScriptConfigurationManager.getInstance(project).getAllScriptsDependenciesClassFilesScope()
    }
}

fun getAllScriptDependenciesSourcesScope(project: Project): GlobalSearchScope {
    return if (scriptsAsEntities) {
        listAllScriptDependenciesScope(project, LibraryRootTypeId.SOURCES)
    } else {
        return ScriptConfigurationManager.getInstance(project).getAllScriptDependenciesSourcesScope()
    }
}

fun getAllScriptsDependenciesClassFiles(project: Project): Collection<VirtualFile> {
    return if (scriptsAsEntities) {
        val entityStorage = WorkspaceModel.getInstance(project).entityStorage.current
        entityStorage.listDependenciesOfAllScriptEntities(LibraryRootTypeId.COMPILED)
    } else {
        return ScriptConfigurationManager.getInstance(project).getAllScriptsDependenciesClassFiles()
    }
}

fun getAllScriptDependenciesSources(project: Project): Collection<VirtualFile> {
    return if (scriptsAsEntities) {
        val entityStorage = WorkspaceModel.getInstance(project).entityStorage.current
        entityStorage.listDependenciesOfAllScriptEntities(LibraryRootTypeId.SOURCES)
    } else {
        return ScriptConfigurationManager.getInstance(project).getAllScriptDependenciesSources()
    }
}

fun computeClassRoots(project: Project): List<VirtualFile> {
    return if (scriptsAsEntities) {
        val entityStorage = WorkspaceModel.getInstance(project).entityStorage.current
        entityStorage.listDependenciesOfAllScriptEntities(LibraryRootTypeId.COMPILED).toList()
    } else {
        val manager = ScriptConfigurationManager.getInstance(project)
        return (manager.getAllScriptsDependenciesClassFiles() + manager.getAllScriptsSdkDependenciesClassFiles()).filter { it.isValid }
    }
}

private fun listAllScriptDependenciesScope(project: Project, rootTypeId: LibraryRootTypeId): GlobalSearchScope {
    val entityStorage = WorkspaceModel.getInstance(project).entityStorage.current
    val files = entityStorage.listDependenciesOfAllScriptEntities(rootTypeId).toList()
    return NonClasspathDirectoriesScope.compose(files)
}

private fun EntityStorage.listDependenciesOfAllScriptEntities(rootTypeId: LibraryRootTypeId): Collection<VirtualFile> {
    return entities(KotlinScriptEntity::class.java)
        .flatMap { it.listDependencies(rootTypeId) }
        .toSet()
}

private fun KotlinScriptEntity.listDependencies(rootTypeId: LibraryRootTypeId): List<VirtualFile> = dependencies.asSequence()
    .flatMap { it.roots }
    .filter { it.type == rootTypeId }
    .mapNotNull { it.url.virtualFile }
    .filter { it.isValid }
    .toList()


@Suppress("unused") // exists for debug purposes
internal fun scriptEntitiesDebugInfo(project: Project, listRoots: Boolean = false): String {
    fun List<LibraryRoot>.print(indent: CharSequence = "          ") = asSequence()
        .mapIndexed { i, root -> "$indent${i + 1}: ${root.url.presentableUrl}" }
        .joinToString("\n", indent)

    val debugInfo = StringBuilder()

    val entityStorage = WorkspaceModel.getInstance(project).entityStorage.current

    val allClasses = HashSet<LibraryRoot>()
    val allSources = HashSet<LibraryRoot>()

    entityStorage.entities(KotlinScriptEntity::class.java).forEachIndexed { scriptIndex, scriptEntity ->
        debugInfo.append("#${scriptIndex + 1}: [${scriptEntity.path}]\n")
        scriptEntity.dependencies.forEachIndexed { libIndex, lib ->
            val (classes, sources) = lib.roots.partition { it.type == LibraryRootTypeId.COMPILED }
            allClasses.addAll(classes)
            allSources.addAll(sources)
            debugInfo.append("      Lib #${libIndex + 1}: \"${lib.name}\", classes: ${classes.size}, sources: ${sources.size} \n")
            debugInfo.applyIf(listRoots) {
                append("        Classes:\n ${classes.print()}\n")
                append("        Sources:\n ${sources.print()}\n")
            }
        }
    }
    debugInfo.insert(0, "==> WorkspaceModel (unique classes: ${allClasses.size}, sources: ${allSources.size})\n")
    return debugInfo.toString()
}

@Suppress("unused") // exists for debug purposes
internal fun managerScriptsDebugInfo(project: Project, scriptFiles: Sequence<VirtualFile>? = null): String {
    val debugInfo = StringBuilder()
    val configurationManager = ScriptConfigurationManager.getInstance(project)

    val allSourcesSize = configurationManager.getAllScriptDependenciesSources().size
    val allSdkSourcesSize = configurationManager.getAllScriptSdkDependenciesSources().size

    val allClassesSize = configurationManager.getAllScriptsDependenciesClassFiles().size
    val allSdkClassesSize = configurationManager.getAllScriptsSdkDependenciesClassFiles().size

    scriptFiles?.forEach {
        val classDepSize = configurationManager.getScriptDependenciesClassFiles(it).size
        val sourceDepSize = configurationManager.getScriptDependenciesSourceFiles(it).size
        debugInfo.append("[${it.path}]: classes: ${classDepSize}, sources: ${sourceDepSize}\n")
    }
    debugInfo.insert(
        0,
        "==> ScriptConfigurationManager (classes: $allClassesSize, sdkClasses: $allSdkClassesSize, sources: $allSourcesSize, sdkSources: $allSdkSourcesSize)\n"
    )
    return debugInfo.toString()
}
