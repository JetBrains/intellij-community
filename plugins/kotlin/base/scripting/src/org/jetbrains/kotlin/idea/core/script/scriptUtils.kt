// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script

import com.intellij.openapi.application.Application
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.core.script.configuration.cache.ScriptConfigurationSnapshot
import org.jetbrains.kotlin.idea.core.script.k2.K2ScriptDefinitionProvider
import org.jetbrains.kotlin.idea.core.script.k2.ScriptClassPathUtil
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.impl.toClassPathOrEmpty

val KtFile.alwaysVirtualFile: VirtualFile get() = originalFile.virtualFile ?: viewProvider.virtualFile

fun MutableEntityStorage.createOrUpdateLibrary(
    libraryName: String, roots: List<LibraryRoot>, source: KotlinScriptEntitySource
): LibraryDependency {
    val libraryId = LibraryId(libraryName, LibraryTableId.ProjectLibraryTableId)
    val existingLibrary = this.resolve(libraryId)

    if (existingLibrary == null) {
        createLibrary(libraryId, roots, source)
    } else {
        modifyLibraryEntity(existingLibrary) {
            this.roots = roots.toMutableList()
        }
    }

    return LibraryDependency(libraryId, false, DependencyScope.COMPILE)
}

fun VirtualFile.sourceLibraryRoot(project: Project): LibraryRoot {
    val urlManager = project.workspaceModel.getVirtualFileUrlManager()
    return LibraryRoot(toVirtualFileUrl(urlManager), LibraryRootTypeId.SOURCES)
}

fun VirtualFile.compiledLibraryRoot(project: Project): LibraryRoot {
    val urlManager = project.workspaceModel.getVirtualFileUrlManager()
    return LibraryRoot(toVirtualFileUrl(urlManager), LibraryRootTypeId.COMPILED)
}

fun MutableEntityStorage.getOrCreateLibrary(
    libraryName: String, roots: List<LibraryRoot>, source: KotlinScriptEntitySource
): LibraryDependency {
    val libraryId = LibraryId(libraryName, LibraryTableId.ProjectLibraryTableId)
    if (!this.contains(libraryId)) createLibrary(libraryId, roots, source)

    return LibraryDependency(libraryId, false, DependencyScope.COMPILE)
}

fun MutableEntityStorage.createLibrary(
    libraryId: LibraryId, roots: List<LibraryRoot>, source: KotlinScriptEntitySource
): LibraryEntity {
    val sortedRoots = roots.sortedWith(ROOT_COMPARATOR)
    val libraryEntity = LibraryEntity(libraryId.name, libraryId.tableId, sortedRoots, source)

    return addEntity(libraryEntity)
}

val ROOT_COMPARATOR: Comparator<LibraryRoot> = Comparator { o1, o2 ->
    when {
        o1 == o2 -> 0
        o1 == null -> -1
        o2 == null -> 1
        else -> o1.url.url.compareTo(o2.url.url)
    }
}

@ApiStatus.Internal
fun MutableEntityStorage.getOrCreateDefinitionDependency(
    definition: ScriptDefinition, project: Project, entitySource: EntitySource
): LibraryDependency {
    val libraryId = LibraryId(".${definition.fileExtension} definition dependencies", LibraryTableId.ProjectLibraryTableId)
    if (!this.contains(libraryId)) {
        val fileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()

        val classes = definition.compilationConfiguration[ScriptCompilationConfiguration.dependencies]
            .toClassPathOrEmpty()
            .mapNotNull { ScriptClassPathUtil.findVirtualFile(it.path) }
            .sortedBy { it.name }

        val sources = definition.compilationConfiguration[ScriptCompilationConfiguration.ide.dependenciesSources]
            .toClassPathOrEmpty()
            .mapNotNull { ScriptClassPathUtil.findVirtualFile(it.path) }
            .sortedBy { it.name }

        val classRoots = classes.map {
            LibraryRoot(it.toVirtualFileUrl(fileUrlManager), LibraryRootTypeId.COMPILED)
        }

        val sourceRoots = sources.map {
            LibraryRoot(it.toVirtualFileUrl(fileUrlManager), LibraryRootTypeId.SOURCES)
        }

        addEntity(
            LibraryEntity(libraryId.name, libraryId.tableId, classRoots + sourceRoots, entitySource)
        )
    }

    return LibraryDependency(libraryId, false, DependencyScope.COMPILE)
}

inline fun <reified T : ScriptDefinitionsSource> Project.scriptDefinitionsSourceOfType(): T? =
    SCRIPT_DEFINITIONS_SOURCES.getExtensions(this).filterIsInstance<T>().firstOrNull().safeAs<T>()

val SCRIPT_DEFINITIONS_SOURCES: ProjectExtensionPointName<ScriptDefinitionsSource> =
    ProjectExtensionPointName("org.jetbrains.kotlin.scriptDefinitionsSource")

@set: org.jetbrains.annotations.TestOnly
var Application.isScriptChangesNotifierDisabled by NotNullableUserDataProperty(
    Key.create("SCRIPT_CHANGES_NOTIFIER_DISABLED"), true
)

internal val logger = Logger.getInstance("#org.jetbrains.kotlin.idea.script")

fun scriptingDebugLog(file: KtFile, message: () -> String) {
    scriptingDebugLog(file.originalFile.virtualFile, message)
}

fun scriptingDebugLog(file: VirtualFile? = null, message: () -> String) {
    if (logger.isDebugEnabled) {
        logger.debug("[KOTLIN_SCRIPTING] ${file?.let { file.path + " " } ?: ""}" + message())
    }
}

fun scriptingInfoLog(message: String) {
    logger.info("[KOTLIN_SCRIPTING] $message")
}

fun scriptingWarnLog(message: String) {
    logger.warn("[KOTLIN_SCRIPTING] $message")
}

fun scriptingWarnLog(message: String, throwable: Throwable?) {
    logger.warn("[KOTLIN_SCRIPTING] $message", throwable)
}

fun scriptingErrorLog(message: String, throwable: Throwable?) {
    logger.error("[KOTLIN_SCRIPTING] $message", throwable)
}

fun logScriptingConfigurationErrors(file: VirtualFile, snapshot: ScriptConfigurationSnapshot) {
    if (snapshot.configuration == null) {
        scriptingWarnLog("Script configuration for file $file was not loaded")
        for (report in snapshot.reports) {
            if (report.severity >= ScriptDiagnostic.Severity.WARNING) {
                scriptingWarnLog(report.message, report.exception)
            }
        }
    }
}

fun getAllDefinitions(project: Project): List<ScriptDefinition> = if (KotlinPluginModeProvider.isK2Mode()) {
    K2ScriptDefinitionProvider.getInstance(project).getAllDefinitions().toList()
} else {
    ScriptDefinitionsManager.getInstance(project).allDefinitions
}
