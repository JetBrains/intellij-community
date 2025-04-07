// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.analysis.api.platform.modification.publishGlobalModuleStateModificationEvent
import org.jetbrains.kotlin.idea.base.scripting.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.core.script.alwaysVirtualFile
import org.jetbrains.kotlin.idea.core.script.scriptingErrorLog
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource

@Service(Service.Level.PROJECT)
class DefaultScriptResolutionStrategy(val project: Project, val coroutineScope: CoroutineScope) {
    fun isReadyToHighlight(ktFile: KtFile): Boolean {
        val definition = ktFile.findScriptDefinition()
        if (definition == null) {
            return false
        }

        val configurationsSupplier = definition.getConfigurationResolver(project)
        val projectModelUpdater = definition.getWorkspaceModelManager(project)

        return configurationsSupplier.get(ktFile.alwaysVirtualFile) != null
                && projectModelUpdater.isModuleExist(project, ktFile.alwaysVirtualFile, definition)
    }

    fun execute(vararg ktFiles: KtFile): Job {
        val firstDefinition = ktFiles.firstNotNullOfOrNull { it.findScriptDefinition() }
        if (firstDefinition == null) {
            scriptingErrorLog("failed to find script definition", IllegalStateException())
            return Job()
        }

        val configurationsSupplier = firstDefinition.getConfigurationResolver(project)
        val projectModelUpdater = firstDefinition.getWorkspaceModelManager(project)

        val definitionByVirtualFile = ktFiles.associate {
            it.alwaysVirtualFile to findScriptDefinition(
                project,
                VirtualFileScriptSource(it.alwaysVirtualFile)
            )
        }

        return coroutineScope.launch {
            withBackgroundProgress(project, KotlinBaseScriptingBundle.message("progress.title.processing.scripts")) {
                val configurationPerVirtualFile = definitionByVirtualFile.entries.associate { (script, definition) ->
                    val configuration = configurationsSupplier.get(script)
                        ?: configurationsSupplier.create(script, definition)
                        ?: return@withBackgroundProgress

                    script to configuration
                }

                projectModelUpdater.updateWorkspaceModel(configurationPerVirtualFile)
                ScriptConfigurationsProviderImpl.getInstance(project).store(configurationPerVirtualFile.values)

                edtWriteAction {
                    project.publishGlobalModuleStateModificationEvent()
                }

                ScriptDependenciesModificationTracker.getInstance(project).incModificationCount()
                HighlightingSettingsPerFile.getInstance(project).incModificationCount()

                if (project.isOpen && !project.isDisposed) {
                    val focusedFile = readAction { FileEditorManager.getInstance(project).focusedEditor?.file }
                    ktFiles.firstOrNull { it.alwaysVirtualFile == focusedFile }?.let {
                        readAction {
                            DaemonCodeAnalyzer.getInstance(project).restart(it)
                        }
                    }
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): DefaultScriptResolutionStrategy = project.service()
    }
}