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
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.ide.toPath
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptLibraryRoot
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty
import org.jetbrains.kotlin.scripting.definitions.LazyScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import javax.swing.Icon
import kotlin.script.experimental.api.IdeScriptCompilationConfigurationKeys
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.util.PropertiesCollection

fun indexSourceRootsEagerly(): Boolean = Registry.`is`("kotlin.scripting.index.dependencies.sources", false)

val KtFile.alwaysVirtualFile: VirtualFile get() = originalFile.virtualFile ?: viewProvider.virtualFile

inline fun <reified T : ScriptDefinitionsSource> Project.scriptDefinitionsSourceOfType(): T? =
    SCRIPT_DEFINITIONS_SOURCES.getExtensions(this).filterIsInstance<T>().firstOrNull().safeAs<T>()

val SCRIPT_DEFINITIONS_SOURCES: ProjectExtensionPointName<ScriptDefinitionsSource> =
    ProjectExtensionPointName("org.jetbrains.kotlin.scriptDefinitionsSource")

@set:TestOnly
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
