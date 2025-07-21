// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k1.configuration

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.core.script.shared.CachedConfigurationInputs
import org.jetbrains.kotlin.idea.core.script.k1.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.k1.addScriptDependenciesNotificationPanel
import org.jetbrains.kotlin.idea.core.script.k1.configuration.cache.*
import org.jetbrains.kotlin.idea.core.script.k1.configuration.loader.DefaultScriptConfigurationLoader
import org.jetbrains.kotlin.idea.core.script.k1.configuration.loader.ScriptConfigurationLoader
import org.jetbrains.kotlin.idea.core.script.k1.configuration.loader.ScriptConfigurationLoadingContext
import org.jetbrains.kotlin.idea.core.script.k1.configuration.loader.ScriptOutsiderFileConfigurationLoader
import org.jetbrains.kotlin.idea.core.script.k1.configuration.utils.*
import org.jetbrains.kotlin.idea.core.script.k1.removeScriptDependenciesNotificationPanel
import org.jetbrains.kotlin.idea.core.script.k1.settings.KotlinScriptingSettingsImpl
import org.jetbrains.kotlin.idea.core.script.k1.ucache.ScriptClassRootsBuilder
import org.jetbrains.kotlin.idea.core.script.shared.areSimilar
import org.jetbrains.kotlin.idea.core.script.shared.getKtFile
import org.jetbrains.kotlin.idea.core.script.shared.getScriptReports
import org.jetbrains.kotlin.idea.core.script.v1.scriptingDebugLog
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.ScriptReportSink
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.script.experimental.api.ScriptDiagnostic

/**
 * Standard implementation of scripts configuration loading and caching.
 *
 * ## Loading initiation
 *
 * [getOrLoadConfiguration] will be called when we need to show or analyze some script file.
 *
 * Configuration may be loaded from [cache] or [reloadOutOfDateConfiguration] will be called on [cache] miss.
 *
 * There are 2 tiers [cache]: memory and FS. For now FS cache implemented by [ScriptConfigurationLoader]
 * because we are not storing classpath roots yet. As a workaround cache.all() will return only memory
 * cached configurations.  So, for now we are indexing roots that loaded from FS with
 * default [reloadOutOfDateConfiguration] mechanics.
 *
 * Also, [ensureLoadedFromCache] may be called from [UnusedSymbolInspection]
 * to ensure that configuration of all scripts containing some symbol are up-to-date or try load it in sync.
 *
 * ## Loading
 *
 * When requested, configuration will be loaded using first applicable [loaders].
 * It can work synchronously or asynchronously.
 *
 * Synchronous loader will be called just immediately. Despite this, its result may not be applied immediately,
 * see next section for details.
 *
 * Asynchronous loader will be called in background thread (by [org.jetbrains.kotlin.idea.core.script.k1.configuration.utils.BackgroundExecutor]).
 *
 * ## Applying
 *
 * By default loaded configuration will *not* be applied immediately. Instead, we show in editor notification
 * that suggests user to apply changed configuration. This was done to avoid sporadically starting indexing of new roots,
 * which may happens regularly for large Gradle projects.
 *
 * Notification will be displayed when configuration is going to be updated. First configuration will be loaded
 * without notification.
 *
 * This behavior may be disabled by enabling "auto reload" in project settings.
 * When enabled, all loaded configurations will be applied immediately, without any notification.
 *
 * ## Concurrency
 *
 * Each files may be in on of this state:
 * - scriptDefinition is not ready
 * - not loaded
 * - up-to-date
 * - invalid, in queue (in [org.jetbrains.kotlin.idea.core.script.k1.configuration.utils.BackgroundExecutor] queue)
 * - invalid, loading
 * - invalid, waiting for apply
 *
 * [reloadOutOfDateConfiguration] guard this states. See it's docs for more details.
 */
class DefaultScriptingSupport(val manager: ScriptConfigurationManager) {
    val project: Project
        get() = manager.myProject

    @Suppress("LeakingThis")
    private val cache: ScriptConfigurationCache = createCache()

    internal val backgroundExecutor: BackgroundExecutor = when {
        isUnitTestMode() -> @Suppress("TestOnlyProblems") (TestingBackgroundExecutor(manager))
        else -> DefaultBackgroundExecutor(project, manager)
    }

    private val outsiderLoader = ScriptOutsiderFileConfigurationLoader(project)
    private val fileAttributeCache = ScriptConfigurationFileAttributeCache(project)
    private val defaultLoader = DefaultScriptConfigurationLoader(project)
    private val loaders: List<ScriptConfigurationLoader>
        get() = mutableListOf<ScriptConfigurationLoader>().apply {
            add(outsiderLoader)
            add(fileAttributeCache)
            addAll(ScriptConfigurationLoader.EP_NAME.getPoint(project).extensionList)
            add(defaultLoader)
        }

    private val saveLock = ReentrantLock()

    private fun createCache(): ScriptConfigurationCache = object : ScriptConfigurationMemoryCache(project) {
        override fun setLoaded(file: VirtualFile, configurationSnapshot: ScriptConfigurationSnapshot) {
            super.setLoaded(file, configurationSnapshot)
            fileAttributeCache.save(file, configurationSnapshot)
        }
    }

    fun getCachedConfigurationState(file: VirtualFile?): ScriptConfigurationState? {
        if (file == null) return null
        return cache[file]
    }

    /**
     * Will be called on [cache] miss to initiate loading of [file]'s script configuration.
     *
     * ## Concurrency
     *
     * Each files may be in on of the states described below:
     * - scriptDefinition is not ready. `ScriptDefinitionsManager.getInstance(project).isReady() == false`.
     * [updateScriptDefinitionsReferences] will be called when [org.jetbrains.kotlin.idea.core.script.k1.ScriptDefinitionsManager] will be ready
     * which will call [reloadOutOfDateConfiguration] for opened editors.
     * - unknown. When [isFirstLoad] true (`cache[file] == null`).
     * - up-to-date. `cache[file]?.upToDate == true`.
     * - invalid, in queue. `cache[file]?.upToDate == false && file in backgroundExecutor`.
     * - invalid, loading. `cache[file]?.upToDate == false && file !in backgroundExecutor`.
     * - invalid, waiting for apply. `cache[file]?.upToDate == false && file !in backgroundExecutor` and has notification panel?
     * - invalid, waiting for update. `cache[file]?.upToDate == false` and has notification panel
     *
     * Async:
     * - up-to-date: [reloadOutOfDateConfiguration] will not be called.
     * - `unknown` and `invalid, in queue`:
     *   Concurrent async loading will be guarded by `backgroundExecutor.ensureScheduled`
     *   (only one task per file will be scheduled at same time)
     * - `invalid, loading`
     *   Loading should be removed from `backgroundExecutor`, and will be rescheduled on change
     *   and file will be up-to-date checked again. This will happen after current loading,
     *   because only `backgroundExecutor` execute tasks in one thread.
     * - `invalid, waiting for apply`:
     *   Loading will not be queued, since we are marking file as up-to-date with
     *   not yet applied configuration.
     * - `invalid, waiting for update`:
     *   Loading wasn't started, only notification is shown
     *
     * Sync:
     * - up-to-date:
     *   [reloadOutOfDateConfiguration] will not be called.
     * - all other states, i.e: `unknown`, `invalid, in queue`, `invalid, loading` and `invalid, ready for apply`:
     *   everything will be computed just in place, possible concurrently.
     *   [suggestOrSaveConfiguration] calls will be serialized by the [saveLock]
     */
    private fun reloadOutOfDateConfiguration(
        file: KtFile,
        isFirstLoad: Boolean = getCachedConfigurationState(file.originalFile.virtualFile)?.applied == null,
        forceSync: Boolean = false,
        fromCacheOnly: Boolean = false,
        skipNotification: Boolean = false
    ): Boolean {
        val virtualFile = file.originalFile.virtualFile ?: return false

        if (project.isDisposed) return false
        val scriptDefinition = file.findScriptDefinition() ?: return false

        val (async, sync) = loaders.partition { it.shouldRunInBackground(scriptDefinition) }

        val syncLoader =
            sync.firstOrNull { it.loadDependencies(isFirstLoad, file, scriptDefinition, loadingContext) }

        return if (syncLoader == null) {
            if (!fromCacheOnly) {
                if (forceSync) {
                    loaders.firstOrNull { it.loadDependencies(isFirstLoad, file, scriptDefinition, loadingContext) }
                } else {
                    val autoReloadEnabled = KotlinScriptingSettingsImpl.getInstance(project).autoReloadConfigurations(scriptDefinition)
                    val forceSkipNotification = skipNotification || autoReloadEnabled

                    // sync loaders can do something, let's recheck
                    val isFirstLoadActual = getCachedConfigurationState(virtualFile)?.applied == null

                    val intercepted = !forceSkipNotification && async.any {
                        it.interceptBackgroundLoading(virtualFile, isFirstLoadActual) {
                            runAsyncLoaders(file, virtualFile, scriptDefinition, listOf(it), true)
                        }
                    }

                    if (!intercepted) {
                        runAsyncLoaders(file, virtualFile, scriptDefinition, async, forceSkipNotification)
                    }
                }
            }

            false
        } else {
            true
        }
    }

    private fun runAsyncLoaders(
        file: KtFile,
        virtualFile: VirtualFile,
        scriptDefinition: ScriptDefinition,
        loaders: List<ScriptConfigurationLoader>,
        forceSkipNotification: Boolean
    ) {
        backgroundExecutor.ensureScheduled(virtualFile) {
            val cached = getCachedConfigurationState(virtualFile)

            val applied = cached?.applied
            if (applied != null && applied.inputs.isUpToDate(project, virtualFile)) {
                // in case user reverted to applied configuration
                suggestOrSaveConfiguration(virtualFile, applied, forceSkipNotification)
            } else if (cached == null || !cached.isUpToDate(project, virtualFile)) {
                // don't start loading if nothing was changed
                // (in case we checking for up-to-date and loading concurrently)
                val actualIsFirstLoad = cached == null
                loaders.firstOrNull { it.loadDependencies(actualIsFirstLoad, file, scriptDefinition, loadingContext) }
            }
        }
    }

    @TestOnly
    fun runLoader(file: KtFile, loader: ScriptConfigurationLoader): ScriptCompilationConfigurationWrapper? {
        val virtualFile = file.originalFile.virtualFile ?: return null

        val scriptDefinition = file.findScriptDefinition() ?: return null

        manager.updater.update {
            loader.loadDependencies(false, file, scriptDefinition, loadingContext)
        }

        return getCachedConfigurationState(virtualFile)?.applied?.configuration
    }

    private val loadingContext = object : ScriptConfigurationLoadingContext {
        /**
         * Used from [ScriptOutsiderFileConfigurationLoader] only.
         */
        override fun getCachedConfiguration(file: VirtualFile): ScriptConfigurationSnapshot? =
            getCachedConfigurationState(file)?.applied ?: getFromGlobalCache(file)

        private fun getFromGlobalCache(file: VirtualFile): ScriptConfigurationSnapshot? {
            val info = manager.getLightScriptInfo(file.path) ?: return null
            return ScriptConfigurationSnapshot(CachedConfigurationInputs.UpToDate, listOf(), info.buildConfiguration())
        }

        override fun suggestNewConfiguration(file: VirtualFile, newResult: ScriptConfigurationSnapshot) {
            suggestOrSaveConfiguration(file, newResult, false)
        }

        override fun saveNewConfiguration(file: VirtualFile, newResult: ScriptConfigurationSnapshot) {
            suggestOrSaveConfiguration(file, newResult, true)
        }
    }

    private fun suggestOrSaveConfiguration(
        file: VirtualFile,
        newResult: ScriptConfigurationSnapshot,
        forceSkipNotification: Boolean
    ) {
        saveLock.withLock {
            scriptingDebugLog(file) { "configuration received = $newResult" }

            cache.setLoaded(file, newResult)

            hideInterceptedNotification(file)

            val newConfiguration = newResult.configuration
            if (newConfiguration == null) {
                saveReports(file, newResult.reports)
                return
            }
            val old = getCachedConfigurationState(file)
            val oldConfiguration = old?.applied?.configuration
            if (oldConfiguration != null && areSimilar(oldConfiguration, newConfiguration)) {
                saveReports(file, newResult.reports)
                file.removeScriptDependenciesNotificationPanel(project)
                return
            }
            val skipNotification = forceSkipNotification
                    || oldConfiguration == null
                    || ApplicationManager.getApplication().isUnitTestModeWithoutScriptLoadingNotification

            if (skipNotification) {
                if (oldConfiguration != null) {
                    file.removeScriptDependenciesNotificationPanel(project)
                }
                saveReports(file, newResult.reports)
                setAppliedConfiguration(file, newResult, syncUpdate = true)
                return
            }
            scriptingDebugLog(file) {
                "configuration changed, notification is shown: old = $oldConfiguration, new = $newConfiguration"
            }

            // restore reports for applied configuration in case of previous error
            saveReports(file, old.applied.reports)

            file.addScriptDependenciesNotificationPanel(
                newConfiguration, project,
                onClick = {
                    saveReports(file, newResult.reports)
                    file.removeScriptDependenciesNotificationPanel(project)
                    manager.updater.update {
                        setAppliedConfiguration(file, newResult)
                    }
                }
            )
        }
    }

    private fun saveReports(
        file: VirtualFile,
        newReports: List<ScriptDiagnostic>
    ) {
        val oldReports = getScriptReports(file)
        if (oldReports != newReports) {
            scriptingDebugLog(file) { "new script reports = $newReports" }

            project.service<ScriptReportSink>().attachReports(file, newReports)
        }
    }

    fun ensureNotificationsRemoved(file: VirtualFile) {
        if (cache.remove(file)) {
            saveReports(file, listOf())
            file.removeScriptDependenciesNotificationPanel(project)
        }

        // this notification can be shown before something will be in cache
        hideInterceptedNotification(file)
    }

    fun updateScriptDefinitionsReferences() {
        // remove notification for all files
        cache.allApplied().forEach { (file, _) ->
            saveReports(file, listOf())
            file.removeScriptDependenciesNotificationPanel(project)
            hideInterceptedNotification(file)
        }

        cache.clear()
    }

    fun getOrLoadConfiguration(
        virtualFile: VirtualFile,
        preloadedKtFile: KtFile?
    ): ScriptCompilationConfigurationWrapper? {
        val cached = getCachedConfigurationState(virtualFile)?.applied
        if (cached != null) return cached.configuration

        // It is known that write access results to the
        // ERROR: A write action should never be executed inside an analysis context (i.e. an `analyze` call).
        if (ApplicationManager.getApplication().isWriteAccessAllowed) return null

        val ktFile = project.getKtFile(virtualFile, preloadedKtFile) ?: return null
        manager.updater.update {
            reloadOutOfDateConfiguration(ktFile, isFirstLoad = true)
        }

        return getCachedConfigurationState(virtualFile)?.applied?.configuration
    }

    /**
     * Load new configuration and suggest applying it (only if it is changed)
     */
    fun ensureUpToDatedConfigurationSuggested(file: KtFile, skipNotification: Boolean = false, forceSync: Boolean = false) {
        manager.updater.update {
            file.originalFile.virtualFile?.let { virtualFile ->
                val state = cache[virtualFile]
                if (state == null || forceSync || !state.isUpToDate(project, virtualFile, file)) {
                    runReadAction {
                        reloadOutOfDateConfiguration(
                            file,
                            forceSync = forceSync,
                            isFirstLoad = state == null,
                            skipNotification = skipNotification
                        )
                    }
                }
            }
        }
    }

    fun isLoadedFromCache(file: KtFile): Boolean = file.originalFile.virtualFile?.let { cache[it] != null } ?: true

    /**
     * Ensure that any configuration for [files] is loaded from cache
     */
    fun ensureLoadedFromCache(files: List<KtFile>): Boolean {
        var allLoaded = true
        manager.updater.update {
            files.forEach { file ->
                file.originalFile.virtualFile?.let { virtualFile ->
                    cache[virtualFile] ?: run {
                        if (!reloadOutOfDateConfiguration(
                                file,
                                isFirstLoad = true,
                                fromCacheOnly = true
                            )
                        ) {
                            allLoaded = false
                        }
                    }
                }
            }
        }

        return allLoaded
    }

    private fun setAppliedConfiguration(
        file: VirtualFile,
        newConfigurationSnapshot: ScriptConfigurationSnapshot?,
        syncUpdate: Boolean = false
    ) {
        val newConfiguration = newConfigurationSnapshot?.configuration
        scriptingDebugLog(file) { "configuration changed = $newConfiguration" }

        if (newConfiguration != null) {
            cache.setApplied(file, newConfigurationSnapshot)
            manager.updater.invalidate(file, synchronous = syncUpdate)
        }
    }

    @TestOnly
    fun updateScriptDependenciesSynchronously(file: PsiFile) {
        file.findScriptDefinition() ?: return

        file as? KtFile ?: error("PsiFile $file should be a KtFile, otherwise script dependencies cannot be loaded")

        val virtualFile = file.virtualFile
        if (cache[virtualFile]?.isUpToDate(project, virtualFile, file) == true) return

        manager.updater.update {
            reloadOutOfDateConfiguration(file, forceSync = true)
        }

        UIUtil.dispatchAllInvocationEvents()
    }

    private fun collectConfigurationsWithCache(builder: ScriptClassRootsBuilder) {
        // own builder for saving to storage
        val rootsStorage = ScriptClassRootsStorage.getInstance(project)
        val storageBuilder = ScriptClassRootsBuilder.fromStorage(project, rootsStorage)
        val ownBuilder = ScriptClassRootsBuilder(storageBuilder)
        cache.allApplied().forEach { (vFile, configuration) ->
            ownBuilder.add(vFile, configuration)
            if (!ScratchUtil.isScratch(vFile)) {
                // do not store (to disk) scratch file configurations due to huge dependencies
                // (to be indexed next time - even if you don't use scratches at all)
                storageBuilder.add(vFile, configuration)
            }
        }
        storageBuilder.toStorage(rootsStorage)

        builder.add(ownBuilder)
    }

    fun collectConfigurations(builder: ScriptClassRootsBuilder) {
        if (isFSRootsStorageEnabled()) {
            collectConfigurationsWithCache(builder)
        } else {
            cache.allApplied().forEach { (vFile, configuration) -> builder.add(vFile, configuration) }
        }
    }

    private fun hideInterceptedNotification(file: VirtualFile) {
        loaders.forEach {
            it.hideInterceptedNotification(file)
        }
    }

    companion object {
        fun getInstance(project: Project) = ScriptConfigurationManager.getInstance(project).default
    }
}

internal fun isFSRootsStorageEnabled(): Boolean = Registry.`is`("kotlin.scripting.fs.roots.storage.enabled")

val ScriptConfigurationManager.testingBackgroundExecutor: TestingBackgroundExecutor
    get() {
        @Suppress("TestOnlyProblems")
        return this.default.backgroundExecutor as TestingBackgroundExecutor
    }
