// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.script

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.DefaultScriptingSupport
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptConfigurationsProvider
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.utils.addToStdlib.cast
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.makeFailureResult

// NOTE: this service exists exclusively because ScriptDependencyManager
// cannot be registered as implementing two services (state would be duplicated)
internal class IdeScriptDependenciesProvider(project: Project) : ScriptConfigurationsProvider(project) {
    override fun getScriptConfigurationResult(file: KtFile): ScriptCompilationConfigurationResult? {
        val configuration = getScriptConfiguration(file)
        val reports = getScriptReports(file)
        if (configuration == null && reports.isNotEmpty()) {
            return makeFailureResult(reports)
        }
        return configuration?.asSuccess(reports)
    }

    override fun getScriptConfiguration(file: KtFile): ScriptCompilationConfigurationWrapper? {
        // return only already loaded configurations OR force to load gradle-related configurations
        return if (DefaultScriptingSupport.getInstance(project).isLoadedFromCache(file) || !ScratchUtil.isScratch(file.virtualFile)) {
            ScriptConfigurationManager.getInstance(project).getConfiguration(file)
        } else {
            null
        }
    }

}

/**
 * Facade for loading and caching Kotlin script files configuration.
 *
 * This service also starts indexing of new dependency roots and runs highlighting
 * of opened files when configuration will be loaded or updated.
 */
interface ScriptConfigurationManager : ScriptDependencyAware {
    /**
     * Get cached configuration for [file] or load it.
     * May return null even configuration was loaded but was not yet applied.
     */
    fun getConfiguration(file: KtFile): ScriptCompilationConfigurationWrapper?

    /**
     * Check if configuration is already cached for [file] (in cache or FileAttributes).
     * The result may be true, even cached configuration is considered out-of-date.
     *
     * Supposed to be used to switch highlighting off for scripts without configuration
     * to avoid all file being highlighted in red.
     */
    fun hasConfiguration(file: KtFile): Boolean

    /**
     * returns true when there is no configuration and highlighting should be suspended
     */
    fun isConfigurationLoadingInProgress(file: KtFile): Boolean

    /**
     * Update caches that depends on script definitions and do update if necessary
     */
    fun updateScriptDefinitionReferences()

    ///////////////
    // classpath roots info:

    fun getScriptDependenciesSourceFiles(file: VirtualFile): Collection<VirtualFile>
    fun getAllScriptsSdkDependenciesClassFiles(): Collection<VirtualFile>
    fun getAllScriptSdkDependenciesSources(): Collection<VirtualFile>
    fun getScriptDependingOn(dependencies: Collection<String>): VirtualFile?

    companion object {
        fun getServiceIfCreated(project: Project): ScriptConfigurationManager? = project.serviceIfCreated()

        @JvmStatic
        fun getInstance(project: Project): ScriptConfigurationManager = project.service()

        @JvmStatic
        fun compositeScriptConfigurationManager(project: Project): CompositeScriptConfigurationManager =
            getInstance(project).cast<CompositeScriptConfigurationManager>()

        @Suppress("TestOnlyProblems")
        @TestOnly
        fun updateScriptDependenciesSynchronously(file: PsiFile) {
            // TODO: review the usages of this method
            val defaultScriptingSupport = defaultScriptingSupport(file.project)
            when (file) {
                is KtFile -> {
                    defaultScriptingSupport.updateScriptDependenciesSynchronously(file)
                }

                else -> {
                    val project = file.project
                    val injectedLanguageManager = InjectedLanguageManager.getInstance(project)
                    object : PsiRecursiveElementVisitor() {
                        override fun visitElement(element: PsiElement) {
                            injectedLanguageManager.enumerate(element) { psi, _ ->
                                defaultScriptingSupport.updateScriptDependenciesSynchronously(psi)
                            }
                            super.visitElement(element)
                        }
                    }.visitFile(file)
                }
            }
        }

        private fun defaultScriptingSupport(project: Project) =
            compositeScriptConfigurationManager(project).default

        @TestOnly
        fun clearCaches(project: Project) {
            defaultScriptingSupport(project).updateScriptDefinitionsReferences()
        }
    }
}
