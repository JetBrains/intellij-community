// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.script

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.io.URLUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.DefaultScriptingSupport
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.UserDataProperty
import org.jetbrains.kotlin.scripting.definitions.ScriptDependenciesProvider
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.utils.addToStdlib.cast
import java.io.File
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.notExists
import kotlin.io.path.pathString
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.makeFailureResult

// NOTE: this service exists exclusively because ScriptDependencyManager
// cannot be registered as implementing two services (state would be duplicated)
class IdeScriptDependenciesProvider(project: Project) : ScriptDependenciesProvider(project) {
    override fun getScriptConfigurationResult(file: KtFile): ScriptCompilationConfigurationResult? {
        val configuration = getScriptConfiguration(file)
        val reports = IdeScriptReportSink.getReports(file)
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
interface ScriptConfigurationManager {
    fun loadPlugins()

    /**
     * Get cached configuration for [file] or load it.
     * May return null even configuration was loaded but was not yet applied.
     */
    fun getConfiguration(file: KtFile): ScriptCompilationConfigurationWrapper?

    @Deprecated("Use getScriptClasspath(KtFile) instead")
    fun getScriptClasspath(file: VirtualFile): List<VirtualFile>

    /**
     * @see [getConfiguration]
     */
    fun getScriptClasspath(file: KtFile): List<VirtualFile>

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

    fun getScriptSdk(file: VirtualFile): Sdk?
    fun getFirstScriptsSdk(): Sdk?

    fun getScriptDependenciesClassFilesScope(file: VirtualFile): GlobalSearchScope

    fun getAllScriptsDependenciesClassFilesScope(): GlobalSearchScope
    fun getAllScriptDependenciesSourcesScope(): GlobalSearchScope
    fun getAllScriptsDependenciesClassFiles(): Collection<VirtualFile>
    fun getAllScriptDependenciesSources(): Collection<VirtualFile>

    companion object {
        fun getServiceIfCreated(project: Project): ScriptConfigurationManager? = project.serviceIfCreated()

        @JvmStatic
        fun getInstance(project: Project): ScriptConfigurationManager = project.service()

        @JvmStatic
        fun allExtraRoots(project: Project): Collection<VirtualFile> {
            val manager = getInstance(project)
            return manager.getAllScriptsDependenciesClassFiles() + manager.getAllScriptDependenciesSources()
        }

        @JvmStatic
        fun compositeScriptConfigurationManager(project: Project) =
            getInstance(project).cast<CompositeScriptConfigurationManager>()

        fun toVfsRoots(roots: Iterable<File>): List<VirtualFile> = roots.mapNotNull { classpathEntryToVfs(it.toPath()) }

        // TODO: report this somewhere, but do not throw: assert(res != null, { "Invalid classpath entry '$this': exists: ${exists()}, is directory: $isDirectory, is file: $isFile" })
        fun classpathEntryToVfs(path: Path): VirtualFile? = when {
            path.notExists() -> null
            path.isDirectory() -> StandardFileSystems.local()?.findFileByPath(path.pathString)
            path.isRegularFile() -> StandardFileSystems.jar()?.findFileByPath(path.pathString + URLUtil.JAR_SEPARATOR)
            else -> null
        }

        @TestOnly
        fun updateScriptDependenciesSynchronously(file: PsiFile) {
            // TODO: review the usages of this method
            defaultScriptingSupport(file.project).updateScriptDependenciesSynchronously(file)
        }

        private fun defaultScriptingSupport(project: Project) =
            compositeScriptConfigurationManager(project).default

        @TestOnly
        fun clearCaches(project: Project) {
            defaultScriptingSupport(project).updateScriptDefinitionsReferences()
        }

        fun clearManualConfigurationLoadingIfNeeded(file: VirtualFile) {
            if (file.LOAD_CONFIGURATION_MANUALLY == true) {
                file.LOAD_CONFIGURATION_MANUALLY = null
            }
        }

        fun markFileWithManualConfigurationLoading(file: VirtualFile) {
            file.LOAD_CONFIGURATION_MANUALLY = true
        }

        fun isManualConfigurationLoading(file: VirtualFile): Boolean = file.LOAD_CONFIGURATION_MANUALLY ?: false

        private var VirtualFile.LOAD_CONFIGURATION_MANUALLY: Boolean? by UserDataProperty(Key.create<Boolean>("MANUAL_CONFIGURATION_LOADING"))
    }
}
