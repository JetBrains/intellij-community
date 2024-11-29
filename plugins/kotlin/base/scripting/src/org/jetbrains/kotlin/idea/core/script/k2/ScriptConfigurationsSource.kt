// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
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
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BinaryOperator
import kotlin.script.experimental.api.ResultWithDiagnostics

open class BaseScriptModel(
    open val virtualFile: VirtualFile
)

class ScriptConfigurations(
    val configurations: Map<VirtualFile, ResultWithDiagnostics<ScriptCompilationConfigurationWrapper>> = mapOf(),
    val sdks: Map<Path, Sdk> = mutableMapOf(),
) {
    operator fun plus(other: ScriptConfigurations): ScriptConfigurations = ScriptConfigurations(
        configurations + other.configurations, sdks + other.sdks
    )
}

abstract class ScriptConfigurationsSource<T : BaseScriptModel>(open val project: Project) {
    val data = AtomicReference(ScriptConfigurations())

    abstract fun getScriptDefinitionsSource(): ScriptDefinitionsSource?

    open fun getScriptConfigurations(virtualFile: VirtualFile): ResultWithDiagnostics<ScriptCompilationConfigurationWrapper>? =
        data.get().configurations[virtualFile]

    protected abstract suspend fun updateConfigurations(scripts: Iterable<T>)

    protected abstract suspend fun updateModules(storage: MutableEntityStorage? = null)

    suspend fun updateDependenciesAndCreateModules(scripts: Iterable<T>, storage: MutableEntityStorage? = null) {
        updateConfigurations(scripts)

        updateModules(storage)

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
                    val ktFile = virtualFile.toPsiFile(project) as? KtFile ?: error("Cannot convert to PSI file: $virtualFile")
                    DaemonCodeAnalyzer.getInstance(project).restart(ktFile)
                }
            }
        }
    }
}
