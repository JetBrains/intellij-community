// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.highlighting

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.relativizeToClosestAncestor
import com.intellij.openapi.util.io.toNioPathOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.analysis.api.platform.modification.publishGlobalModuleStateModificationEvent
import org.jetbrains.kotlin.analysis.api.platform.modification.publishGlobalScriptModuleStateModificationEvent
import org.jetbrains.kotlin.idea.core.script.k2.configurations.ScriptConfigurationsProviderImpl
import org.jetbrains.kotlin.idea.core.script.k2.configurations.getConfigurationResolver
import org.jetbrains.kotlin.idea.core.script.k2.configurations.getWorkspaceModelManager
import org.jetbrains.kotlin.idea.core.script.v1.alwaysVirtualFile
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.core.script.v1.scriptingDebugLog
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.ScriptReportSink
import kotlin.script.experimental.jvm.util.isError

@Service(Service.Level.PROJECT)
class DefaultScriptResolutionStrategy(val project: Project, val coroutineScope: CoroutineScope) {
    fun isReadyToHighlight(ktFile: KtFile): Boolean {
        val definition = ktFile.findScriptDefinition()
        if (definition == null) {
            return false
        }

        val configurationsSupplier = definition.getConfigurationResolver(project)
        val projectModelUpdater = definition.getWorkspaceModelManager(project)

        val configuration = configurationsSupplier.get(ktFile.alwaysVirtualFile)?.scriptConfiguration ?: return false
        if (configuration.isError()) {
            project.service<ScriptReportSink>().attachReports(ktFile.alwaysVirtualFile, configuration.reports)
            return true
        }

        return projectModelUpdater.isScriptExist(project, ktFile.alwaysVirtualFile, definition)
    }

    fun execute(vararg ktFiles: KtFile): Job {
        val definitionByFile = ktFiles
            .filterNot { KotlinScripDeferredResolutionPolicy.shouldDeferResolution(project, it.virtualFile) }
            .associateWith {
                findScriptDefinition(project, KtFileScriptSource(it))
            }

        scriptingDebugLog {
            val baseDirPath = project.basePath?.toNioPathOrNull()

            definitionByFile.entries.joinToString(prefix = "processing scripts:\n", separator = "\n") { (script, definition) ->
                val path = baseDirPath?.relativizeToClosestAncestor(script.alwaysVirtualFile.path)?.second ?: script.alwaysVirtualFile.path
                "$path -> ${definition.name}(${definition.definitionId})"
            }
        }

        if (definitionByFile.isEmpty()) return Job()

        return coroutineScope.launch {
            process(definitionByFile)
        }
    }

    private suspend fun process(definitionByFile: Map<KtFile, ScriptDefinition>) {
        val configurationsSupplier = definitionByFile.firstNotNullOf { it.value.getConfigurationResolver(project) }
        val projectModelUpdater = definitionByFile.firstNotNullOf { it.value.getWorkspaceModelManager(project) }

        val configurationPerVirtualFile = definitionByFile.entries.associate { (file, definition) ->
            val virtualFile = file.virtualFile
            val configuration = configurationsSupplier.get(virtualFile)
                ?: configurationsSupplier.create(virtualFile, definition)
                ?: return

            virtualFile to configuration
        }

        projectModelUpdater.updateWorkspaceModel(configurationPerVirtualFile)

        edtWriteAction {
            project.publishGlobalModuleStateModificationEvent()
            project.publishGlobalScriptModuleStateModificationEvent()
        }

        ScriptDependenciesModificationTracker.getInstance(project).incModificationCount()
        HighlightingSettingsPerFile.getInstance(project).incModificationCount()

        val filesInEditors = readAction {
            FileEditorManager.getInstance(project).allEditors.mapTo(hashSetOf(), FileEditor::getFile)
        }

        if (project.isOpen && !project.isDisposed) {
            for (ktFile in definitionByFile.keys) {
                if (ktFile.alwaysVirtualFile !in filesInEditors) continue
                if (project.isOpen && !project.isDisposed) {
                    readAction {
                        DaemonCodeAnalyzer.getInstance(project).restart(ktFile)
                    }
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): DefaultScriptResolutionStrategy = project.service()

        private val logger: Logger
            get() = Logger.getInstance(DefaultScriptResolutionStrategy::class.java)
    }
}