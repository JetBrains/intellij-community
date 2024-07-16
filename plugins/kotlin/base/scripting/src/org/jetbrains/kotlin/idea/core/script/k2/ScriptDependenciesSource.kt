// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.kotlin.analysis.api.platform.analysisMessageBus
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationTopics
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtFile
import java.util.concurrent.atomic.AtomicReference

open class BaseScriptModel(
    open val virtualFile: VirtualFile
)

abstract class ScriptDependenciesSource<T : BaseScriptModel>(open val project: Project) {
    val currentConfigurationsData = AtomicReference(ScriptDependenciesData())

    protected abstract fun resolveDependencies(scripts: Iterable<T>): ScriptDependenciesData
    protected abstract suspend fun updateModules(dependencies: ScriptDependenciesData, storage: MutableEntityStorage? = null)

    suspend fun updateDependenciesAndCreateModules(scripts: Iterable<T>, storage: MutableEntityStorage? = null) {
        project.waitForSmartMode()
        val dependencies = resolveDependencies(scripts)

        updateModules(dependencies, storage)
        currentConfigurationsData.set(dependencies)

        ScriptConfigurationDataProvider.getInstance(project).notifySourceUpdated()

        writeAction {
            project.analysisMessageBus.syncPublisher(KotlinModificationTopics.GLOBAL_MODULE_STATE_MODIFICATION).onModification()
        }

        for (script in scripts) {
            if (project.isOpen && !project.isDisposed) {
                readAction {
                    val ktFile =
                        script.virtualFile.toPsiFile(project) as? KtFile ?: error("Cannot convert to PSI file: ${script.virtualFile}")
                    DaemonCodeAnalyzer.getInstance(project).restart(ktFile)
                }
            }
        }
    }
}
