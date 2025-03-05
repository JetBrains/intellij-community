// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.kotlin.analysis.api.platform.analysisMessageBus
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationTopics
import org.jetbrains.kotlin.idea.core.script.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import java.util.concurrent.atomic.AtomicReference
import kotlin.script.experimental.api.ResultWithDiagnostics

typealias ScriptConfiguration = ResultWithDiagnostics<ScriptCompilationConfigurationWrapper>

open class BaseScriptModel(
    open val virtualFile: VirtualFile
) {
    override fun toString(): String {
        return "BaseScriptModel(virtualFile=$virtualFile)"
    }
}

data class ScriptConfigurationWithSdk(
    val scriptConfiguration: ScriptConfiguration,
    val sdk: Sdk?,
)

abstract class ScriptConfigurationsSource<T : BaseScriptModel>(open val project: Project) {
    val data: AtomicReference<Map<VirtualFile, ScriptConfigurationWithSdk>> = AtomicReference(emptyMap())

    abstract fun getScriptDefinitionsSource(): ScriptDefinitionsSource?

    open fun getConfigurationWithSdk(virtualFile: VirtualFile): ScriptConfigurationWithSdk? =
        data.get()[virtualFile]

    protected abstract suspend fun updateConfigurations(scripts: Iterable<T>)

    abstract suspend fun updateModules(storage: MutableEntityStorage? = null)

    suspend fun updateDependenciesAndCreateModules(scripts: Iterable<T>, storage: MutableEntityStorage? = null) {
        updateConfigurations(scripts)

        updateModules(storage)

        ScriptConfigurationsProviderImpl.getInstance(project).notifySourceUpdated()

        edtWriteAction {
            project.analysisMessageBus.syncPublisher(KotlinModificationTopics.GLOBAL_MODULE_STATE_MODIFICATION).onModification()
        }

        ScriptDependenciesModificationTracker.getInstance(project).incModificationCount()
        HighlightingSettingsPerFile.getInstance(project).incModificationCount()

        val filesInEditors = readAction {
            FileEditorManager.getInstance(project).allEditors.mapTo(hashSetOf(), FileEditor::getFile)
        }

        for (script in scripts) {
            val virtualFile = script.virtualFile
            if (virtualFile !in filesInEditors) continue
            if (project.isOpen && !project.isDisposed) {
                readAction {
                    val ktFile = virtualFile.toPsiFile(project) as? KtFile ?: error("Cannot convert to PSI file: $virtualFile")
                    DaemonCodeAnalyzer.getInstance(project).restart(ktFile)
                }
            }
        }
    }
}
