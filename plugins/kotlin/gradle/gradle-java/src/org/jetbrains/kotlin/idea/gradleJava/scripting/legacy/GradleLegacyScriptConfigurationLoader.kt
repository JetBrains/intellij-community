// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.scripting.legacy

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.core.script.configuration.cache.CachedConfigurationInputs
import org.jetbrains.kotlin.idea.core.script.configuration.loader.DefaultScriptConfigurationLoader
import org.jetbrains.kotlin.idea.core.script.configuration.loader.ScriptConfigurationLoadingContext
import org.jetbrains.kotlin.idea.gradleJava.scripting.getGradleScriptInputsStamp
import org.jetbrains.kotlin.idea.gradleJava.scripting.isGradleKotlinScript
import org.jetbrains.kotlin.idea.gradleJava.scripting.roots.GradleBuildRootsManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition

/**
 * Loader that performs loading for .gralde.kts scripts configuration through the [DefaultScriptingSupport]
 *
 * TODO(gradle6): remove
 */
class GradleLegacyScriptConfigurationLoader(project: Project, private val coroutineScope: CoroutineScope) : DefaultScriptConfigurationLoader(project) {
    private val buildRootsManager
        get() = GradleBuildRootsManager.getInstanceSafe(project)

    override fun interceptBackgroundLoading(file: VirtualFile, isFirstLoad: Boolean, doLoad: () -> Unit): Boolean {
        if (!isGradleKotlinScript(file)) return false

        GradleStandaloneScriptActionsManager.getInstance(project).add {
            GradleStandaloneScriptActions(it, file, isFirstLoad, doLoad)
        }

        return true
    }

    override fun hideInterceptedNotification(file: VirtualFile) {
        if (!isGradleKotlinScript(file)) return

        GradleStandaloneScriptActionsManager.getInstance(project).remove(file)
    }

    override fun shouldRunInBackground(scriptDefinition: ScriptDefinition) = true

    override fun loadDependencies(
        isFirstLoad: Boolean,
        ktFile: KtFile,
        scriptDefinition: ScriptDefinition,
        context: ScriptConfigurationLoadingContext
    ): Boolean {
        val vFile = ktFile.originalFile.virtualFile

        if (!isGradleKotlinScript(vFile)) return false

        hideInterceptedNotification(vFile)

        if (!buildRootsManager.isAffectedGradleProjectFile(vFile.path)) {
            // not known gradle file and not configured as standalone script
            // skip
            return true
        }

        // Gradle read files from FS, so let's save all docs
        coroutineScope.launch(Dispatchers.EDT) {
            writeAction {
                FileDocumentManager.getInstance().saveAllDocuments()
            }
        }

        val result = getConfigurationThroughScriptingApi(ktFile, vFile, scriptDefinition)
        context.saveNewConfiguration(vFile, result)
        return true
    }

    override fun getInputsStamp(virtualFile: VirtualFile, file: KtFile): CachedConfigurationInputs {
        return getGradleScriptInputsStamp(project, virtualFile, file)
            ?: super.getInputsStamp(virtualFile, file)
    }
}
