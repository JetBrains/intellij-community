// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.script

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.io.URLUtil
import com.intellij.util.io.isDirectory
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.core.script.ClasspathToVfsConverter.classpathEntryToVfs
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.DefaultScriptingSupport
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDependenciesProvider
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.utils.addToStdlib.cast
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.isRegularFile
import kotlin.io.path.notExists
import kotlin.io.path.pathString
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.makeFailureResult

// NOTE: this service exists exclusively because ScriptDependencyManager
// cannot be registered as implementing two services (state would be duplicated)
internal class IdeScriptDependenciesProvider(project: Project) : ScriptDependenciesProvider(project) {
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
 * **Please, note** that [ScriptConfigurationManager] should not be used directly.
 * Instead, consider using [org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptImplementationSwitcher].
 *
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

    fun getScriptDependenciesClassFiles(file: VirtualFile): Collection<VirtualFile>
    fun getScriptDependenciesSourceFiles(file: VirtualFile): Collection<VirtualFile>

    fun getScriptSdkDependenciesClassFiles(file: VirtualFile): Collection<VirtualFile>
    fun getScriptSdkDependenciesSourceFiles(file: VirtualFile): Collection<VirtualFile>

    fun getAllScriptsDependenciesClassFilesScope(): GlobalSearchScope
    fun getAllScriptDependenciesSourcesScope(): GlobalSearchScope
    fun getAllScriptsDependenciesClassFiles(): Collection<VirtualFile>
    fun getAllScriptDependenciesSources(): Collection<VirtualFile>
    fun getAllScriptsSdkDependenciesClassFiles(): Collection<VirtualFile>
    fun getAllScriptSdkDependenciesSources(): Collection<VirtualFile>

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

        @TestOnly
        fun updateScriptDependenciesSynchronously(file: PsiFile) {
            // TODO: review the usages of this method
            defaultScriptingSupport(file.project).updateScriptDependenciesSynchronously(file)
        }

        private fun defaultScriptingSupport(project: Project) =
            compositeScriptConfigurationManager(project).default

        @TestOnly
        fun clearCaches(project: Project) {
            ClasspathToVfsConverter.clearCaches()
            defaultScriptingSupport(project).updateScriptDefinitionsReferences()
        }
    }
}

object ClasspathToVfsConverter {

    private enum class FileType {
        NOT_EXISTS, DIRECTORY, REGULAR_FILE, UNKNOWN
    }

    private val cache = ConcurrentHashMap<String, Pair<FileType, VirtualFile?>>()

    private val Path.fileType: FileType get(){
        return when {
            notExists() -> FileType.NOT_EXISTS
            isDirectory() -> FileType.DIRECTORY
            isRegularFile() -> FileType.REGULAR_FILE
            else -> FileType.UNKNOWN
        }
    }

    fun clearCaches() = cache.clear()

    // TODO: report this somewhere, but do not throw: assert(res != null, { "Invalid classpath entry '$this': exists: ${exists()}, is directory: $isDirectory, is file: $isFile" })
    fun classpathEntryToVfs(path: Path): VirtualFile? {
        val key = path.pathString
        val newType = path.fileType

        fun compute(filePath: String): Pair<FileType, VirtualFile?> {
            return newType to when(newType) {
                FileType.NOT_EXISTS -> null
                FileType.DIRECTORY -> StandardFileSystems.local()?.findFileByPath(filePath)
                FileType.REGULAR_FILE -> StandardFileSystems.jar()?.findFileByPath(filePath + URLUtil.JAR_SEPARATOR)
                FileType.UNKNOWN -> null
            }
        }

        val (oldType, oldVFile) = cache.computeIfAbsent(key, ::compute)

        if (oldType != newType || oldVFile == null && (oldType == FileType.DIRECTORY || oldType == FileType.REGULAR_FILE)) {
            return cache.compute(key) { k, _ -> compute(k) }?.second
        } else {
            return oldVFile
        }
    }
}
