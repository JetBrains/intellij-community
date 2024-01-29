// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.script

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.analysis.providers.analysisMessageBus
import org.jetbrains.kotlin.analysis.providers.topics.KotlinTopics
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.core.script.configuration.cache.ScriptConfigurationSnapshot
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import kotlin.script.experimental.api.ScriptDiagnostic

@set: org.jetbrains.annotations.TestOnly
var Application.isScriptChangesNotifierDisabled by NotNullableUserDataProperty(
    Key.create("SCRIPT_CHANGES_NOTIFIER_DISABLED"),
    true
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

fun scriptConfigurationMissingForK2(file: KtFile): Boolean = file.isScript()
        && KotlinPluginModeProvider.isK2Mode()
        && K2ScriptDependenciesProvider.getInstanceIfCreated(file.project)?.getConfiguration(file.virtualFile) == null

fun getAllDefinitions(project: Project): List<ScriptDefinition> =
    if (KotlinPluginModeProvider.isK2Mode()) {
        K2ScriptDefinitionProvider.getInstanceIfCreated(project)?.currentDefinitions?.toList() ?: emptyList()
    } else {
        ScriptDefinitionsManager.getInstance(project).allDefinitions
    }

suspend fun configureGradleScriptsK2(
    javaHome: String?,
    project: Project,
    scripts: Set<ScriptModel>,
    definitions: List<ScriptDefinition>,
) {
    K2ScriptDefinitionProvider.getInstance(project).updateDefinitions(definitions)
    K2ScriptDependenciesProvider.getInstance(project).reloadConfigurations(scripts, javaHome)
    project.createScriptModules(scripts)

    for (script in scripts) {
        if (project.isOpen && !project.isDisposed) {
            readAction {
                val ktFile = script.virtualFile.toPsiFile(project) as? KtFile ?: error("Cannot convert to PSI file: ${script.virtualFile}")
                DaemonCodeAnalyzer.getInstance(project).restart(ktFile)
            }
        }
    }

    writeAction {
        project.analysisMessageBus.syncPublisher(KotlinTopics.GLOBAL_MODULE_STATE_MODIFICATION).onModification()
    }
}

fun k2ScriptingEnabled(): Boolean = KotlinPluginModeProvider.isK2Mode() && scriptingEnabled

val scriptingEnabled = Registry.`is`("kotlin.k2.scripting.enabled", false)