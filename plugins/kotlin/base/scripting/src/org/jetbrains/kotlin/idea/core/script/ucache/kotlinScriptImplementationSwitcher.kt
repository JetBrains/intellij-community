// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.ucache

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.NonClasspathDirectoriesScope
import com.intellij.util.applyIf
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.storage.EntityStorage
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.psi.KtFile

const val KOTLIN_SCRIPTS_AS_ENTITIES = "kotlin.scripts.as.entities"

val scriptsAsEntities: Boolean
    get() = Registry.`is`(KOTLIN_SCRIPTS_AS_ENTITIES, true)


/**
 *  ScriptConfigurationManager or WorkspaceModel based on 'scriptsAsEntities'
 */

fun getScriptDependenciesClassFilesScope(project: Project, ktFile: KtFile): GlobalSearchScope {
    val vFile = ktFile.virtualFile ?: ktFile.viewProvider.virtualFile
    require(ktFile.isScript()) { "argument must be a script: ${vFile.path}" }

    return if (scriptsAsEntities) {
        val entityStorage = WorkspaceModel.getInstance(project).currentSnapshot
        val scriptEntity = entityStorage.resolve(KotlinScriptId(vFile.path))

        if (scriptEntity == null) {
            // WorkspaceModel doesn't know about the file yet. But once the latest sync is over it will.
            // We cannot synchronously call its syncScriptEntities() because it requires platform write-lock acquisition and this function
            // is called under platform read-lock.
            ScriptConfigurationManager.getInstance(project).getScriptDependenciesClassFilesScope(vFile)
        } else {
            NonClasspathDirectoriesScope.compose(scriptEntity.listDependencies(project, KotlinScriptLibraryRootTypeId.COMPILED))
        }
    } else {
        ScriptConfigurationManager.getInstance(project).getScriptDependenciesClassFilesScope(vFile)
    }
}

fun getAllScriptsDependenciesClassFilesScope(project: Project): GlobalSearchScope =
    // calculation based on WS model is inefficient and leads to freezes
    ScriptConfigurationManager.getInstance(project).getAllScriptsDependenciesClassFilesScope()

fun getAllScriptDependenciesSourcesScope(project: Project): GlobalSearchScope =
    // calculation based on WS model is inefficient and leads to freezes
    ScriptConfigurationManager.getInstance(project).getAllScriptDependenciesSourcesScope()

fun getAllScriptsDependenciesClassFiles(project: Project): Collection<VirtualFile> =
    // calculation based on WS model is inefficient and leads to delays
    ScriptConfigurationManager.getInstance(project).getAllScriptsDependenciesClassFiles()

fun getAllScriptDependenciesSources(project: Project): Collection<VirtualFile> =
    // calculation based on WS model is inefficient and leads to freezes
    ScriptConfigurationManager.getInstance(project).getAllScriptDependenciesSources()

fun computeClassRoots(project: Project): List<VirtualFile> {
    return if (scriptsAsEntities) {
        val entityStorage = WorkspaceModel.getInstance(project).currentSnapshot
        entityStorage.listDependenciesOfAllScriptEntities(project, KotlinScriptLibraryRootTypeId.COMPILED).toList()
    } else {
        val manager = ScriptConfigurationManager.getInstance(project)
        (manager.getAllScriptsDependenciesClassFiles() + manager.getAllScriptsSdkDependenciesClassFiles()).filter { it.isValid }
    }
}

private fun EntityStorage.listDependenciesOfAllScriptEntities(
    project: Project,
    rootTypeId: KotlinScriptLibraryRootTypeId
): Collection<VirtualFile> =
    entities(KotlinScriptEntity::class.java)
        .flatMap { it.listDependencies(project, rootTypeId) }
        .toSet()

@Suppress("unused") // exists for debug purposes
internal fun scriptEntitiesDebugInfo(project: Project, listRoots: Boolean = false): String {
    fun List<KotlinScriptLibraryRoot>.print(indent: CharSequence = "          ") = asSequence()
        .mapIndexed { i, root -> "$indent${i + 1}: ${root.url.presentableUrl}" }
        .joinToString("\n", indent)

    return buildString {
        val entityStorage = WorkspaceModel.getInstance(project).currentSnapshot

        val allClasses = HashSet<KotlinScriptLibraryRoot>()
        val allSources = HashSet<KotlinScriptLibraryRoot>()

        entityStorage.entities(KotlinScriptEntity::class.java).forEachIndexed { scriptIndex, scriptEntity ->
            append("#${scriptIndex + 1}: [${scriptEntity.path}]\n")
            scriptEntity.dependencies.forEachIndexed { libIndex, libId ->
                val lib = entityStorage.resolve(libId) ?: return@forEachIndexed

                val (classes, sources) = lib.roots.partition { it.type == KotlinScriptLibraryRootTypeId.COMPILED }
                allClasses.addAll(classes)
                allSources.addAll(sources)
                append("      Lib #${libIndex + 1}: \"${lib.name}\", classes: ${classes.size}, sources: ${sources.size} \n")
                applyIf(listRoots) {
                    append("        Classes:\n ${classes.print()}\n")
                    append("        Sources:\n ${sources.print()}\n")
                }
            }
        }

        insert(0, "==> WorkspaceModel (unique classes: ${allClasses.size}, sources: ${allSources.size})\n")
    }
}

@Suppress("unused") // exists for debug purposes
internal fun managerScriptsDebugInfo(project: Project, scriptFiles: Sequence<VirtualFile>? = null): String = buildString {
    val configurationManager = ScriptConfigurationManager.getInstance(project)

    val allSourcesSize = configurationManager.getAllScriptDependenciesSources().size
    val allSdkSourcesSize = configurationManager.getAllScriptSdkDependenciesSources().size

    val allClassesSize = configurationManager.getAllScriptsDependenciesClassFiles().size
    val allSdkClassesSize = configurationManager.getAllScriptsSdkDependenciesClassFiles().size

    scriptFiles?.forEach {
        val classDepSize = configurationManager.getScriptDependenciesClassFiles(it).size
        val sourceDepSize = configurationManager.getScriptDependenciesSourceFiles(it).size
        append("[${it.path}]: classes: ${classDepSize}, sources: ${sourceDepSize}\n")
    }
    insert(
        0,
        "==> ScriptConfigurationManager (classes: $allClassesSize, sdkClasses: $allSdkClassesSize, sources: $allSourcesSize, sdkSources: $allSdkSourcesSize)\n"
    )
}
