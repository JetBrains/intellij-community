// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script

import com.intellij.openapi.application.Application
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty
import org.jetbrains.kotlin.scripting.definitions.LazyScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import javax.swing.Icon
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.impl.toClassPathOrEmpty
import kotlin.script.experimental.util.PropertiesCollection

fun indexSourceRootsEagerly(): Boolean = Registry.`is`("kotlin.scripting.index.dependencies.sources", false)

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

@set: TestOnly
var Application.isScriptChangesNotifierDisabled: Boolean by NotNullableUserDataProperty(
    Key.create("SCRIPT_CHANGES_NOTIFIER_DISABLED"), true
)

@ApiStatus.Internal
val logger: Logger = Logger.getInstance("#org.jetbrains.kotlin.idea.script")

@ApiStatus.Internal
fun scriptingDebugLog(file: KtFile, message: () -> String) {
    scriptingDebugLog(file.originalFile.virtualFile, message)
}

@ApiStatus.Internal
fun scriptingDebugLog(file: VirtualFile? = null, message: () -> String) {
    if (logger.isDebugEnabled) {
        logger.debug("[KOTLIN_SCRIPTING] ${file?.let { file.path + " " } ?: ""}" + message())
    }
}

@ApiStatus.Internal
fun scriptingInfoLog(message: String) {
    logger.info("[KOTLIN_SCRIPTING] $message")
}

@ApiStatus.Internal
fun scriptingWarnLog(message: String) {
    logger.warn("[KOTLIN_SCRIPTING] $message")
}

fun scriptingWarnLog(message: String, throwable: Throwable?) {
    logger.warn("[KOTLIN_SCRIPTING] $message", throwable)
}

fun scriptingErrorLog(message: String, throwable: Throwable?) {
    logger.error("[KOTLIN_SCRIPTING] $message", throwable)
}

fun getAllDefinitions(project: Project): List<ScriptDefinition> = IdeScriptDefinitionProvider.getInstance(project).getDefinitions()

abstract class IdeScriptDefinitionProvider : LazyScriptDefinitionProvider() {
    abstract fun getDefinitions(): List<ScriptDefinition>

    companion object {
        fun getInstance(project: Project): IdeScriptDefinitionProvider {
            return project.service<ScriptDefinitionProvider>() as IdeScriptDefinitionProvider
        }
    }
}

fun loggingReporter(severity: ScriptDiagnostic.Severity, message: String) {
    val log = Logger.getInstance("ScriptDefinitionsProviders")
    when (severity) {
        ScriptDiagnostic.Severity.FATAL, ScriptDiagnostic.Severity.ERROR -> log.error(message)

        ScriptDiagnostic.Severity.WARNING, ScriptDiagnostic.Severity.INFO -> log.info(message)

        else -> {}
    }
}


class NewScriptFileInfo(
    var id: String = "", var title: String = "", var templateName: String = "Kotlin Script", var icon: Icon = KotlinIcons.SCRIPT
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NewScriptFileInfo

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

val IdeScriptCompilationConfigurationKeys.kotlinScriptTemplateInfo: PropertiesCollection.Key<NewScriptFileInfo> by PropertiesCollection.key()
