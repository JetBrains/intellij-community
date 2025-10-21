// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k1.configuration.loader

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.k1.configuration.cache.ScriptConfigurationSnapshot
import org.jetbrains.kotlin.idea.core.script.k1.settings.KotlinScriptingSettingsImpl
import org.jetbrains.kotlin.idea.core.script.shared.CachedConfigurationInputs
import org.jetbrains.kotlin.idea.core.script.v1.scriptingDebugLog
import org.jetbrains.kotlin.idea.core.script.v1.scriptingWarnLog
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.KotlinScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.LegacyResolverWrapper
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.asDiagnostics
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.dependencies.AsyncDependenciesResolver

open class DefaultScriptConfigurationLoader(val project: Project) : ScriptConfigurationLoader {
    override fun shouldRunInBackground(scriptDefinition: ScriptDefinition): Boolean =
        scriptDefinition
            .asLegacyOrNull<KotlinScriptDefinition>()
            ?.dependencyResolver
            ?.let { it is AsyncDependenciesResolver || it is LegacyResolverWrapper }
            ?: false

    override fun loadDependencies(
        isFirstLoad: Boolean,
        ktFile: KtFile,
        scriptDefinition: ScriptDefinition,
        context: ScriptConfigurationLoadingContext
    ): Boolean {
        if (project.isDisposed) return false

        val virtualFile = ktFile.originalFile.virtualFile

        val result = getConfigurationThroughScriptingApi(ktFile, virtualFile, scriptDefinition)

        if (KotlinScriptingSettingsImpl.getInstance(project).autoReloadConfigurations(scriptDefinition)) {
            context.saveNewConfiguration(virtualFile, result)
        } else {
            context.suggestNewConfiguration(virtualFile, result)
        }

        return true
    }

    protected fun getConfigurationThroughScriptingApi(
        file: KtFile,
        vFile: VirtualFile,
        scriptDefinition: ScriptDefinition
    ): ScriptConfigurationSnapshot {
        scriptingDebugLog(file) { "start dependencies loading" }

        val inputs = getInputsStamp(vFile, file)
        val scriptingApiResult = try {
            refineScriptCompilationConfiguration(
                KtFileScriptSource(file), scriptDefinition, file.project
            )
        } catch (e: Throwable) {
            if (e is ControlFlowException) throw e

            ResultWithDiagnostics.Failure(listOf(e.asDiagnostics()))
        }

        val result = ScriptConfigurationSnapshot(
            inputs,
            scriptingApiResult.reports,
            scriptingApiResult.valueOrNull()
        )

        scriptingDebugLog(file) { "finish dependencies loading" }
        logScriptingConfigurationErrors(vFile, result)

        return result
    }

    private fun logScriptingConfigurationErrors(file: VirtualFile, snapshot: ScriptConfigurationSnapshot) {
        if (snapshot.configuration == null) {
            scriptingWarnLog("Script configuration for file $file was not loaded")
            for (report in snapshot.reports) {
                if (report.severity >= ScriptDiagnostic.Severity.WARNING) {
                    scriptingWarnLog(report.message, report.exception)
                }
            }
        }
    }

    protected open fun getInputsStamp(virtualFile: VirtualFile, file: KtFile): CachedConfigurationInputs {
        return CachedConfigurationInputs.PsiModificationStamp.get(project, virtualFile, file)
    }
}
