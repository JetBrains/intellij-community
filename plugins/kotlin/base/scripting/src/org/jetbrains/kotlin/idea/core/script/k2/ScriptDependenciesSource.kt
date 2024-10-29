// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.kotlin.analysis.api.platform.analysisMessageBus
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationTopics
import org.jetbrains.kotlin.idea.core.script.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtFile
import java.util.concurrent.atomic.AtomicReference
import kotlin.script.experimental.api.ResultWithDiagnostics

open class BaseScriptModel(
    open val virtualFile: VirtualFile
)

abstract class ScriptDependenciesSource<T : BaseScriptModel>(open val project: Project) {
    val currentConfigurationsData = AtomicReference(ScriptDependenciesData())

    protected abstract fun resolveDependencies(scripts: Iterable<T>): ScriptDependenciesData

    protected abstract suspend fun updateModules(dependencies: ScriptDependenciesData, storage: MutableEntityStorage? = null)

    suspend fun updateDependenciesAndCreateModules(scripts: Iterable<T>, storage: MutableEntityStorage? = null) {
        project.waitForSmartMode()
        val configurationData = resolveDependencies(scripts)

        if (configurationData.configurations.all { it.value is ResultWithDiagnostics.Success }) {
            updateModules(configurationData, storage)
        }

        currentConfigurationsData.set(configurationData)

        ScriptConfigurationsProviderImpl.getInstance(project).notifySourceUpdated()

        writeAction {
            project.analysisMessageBus.syncPublisher(KotlinModificationTopics.GLOBAL_MODULE_STATE_MODIFICATION).onModification()
        }

        val filesInEditors = readAction {
            FileEditorManager.getInstance(project).allEditors.mapTo(hashSetOf(), FileEditor::getFile)
        }

        ScriptDependenciesModificationTracker.getInstance(project).incModificationCount()
        HighlightingSettingsPerFile.getInstance(project).incModificationCount()

        for (script in scripts) {
            val virtualFile = script.virtualFile
            if (virtualFile !in filesInEditors) continue
            if (project.isOpen && !project.isDisposed) {
                readAction {
                    val ktFile =
                        virtualFile.toPsiFile(project) as? KtFile ?: error("Cannot convert to PSI file: $virtualFile")
                    DaemonCodeAnalyzer.getInstance(project).restart(ktFile)
                }
            }
        }
    }
}
