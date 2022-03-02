// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.ucache

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.PsiManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.core.script.KotlinScriptDependenciesClassFinder
import org.jetbrains.kotlin.idea.core.script.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.scriptingDebugLog
import org.jetbrains.kotlin.idea.core.util.CheckCanceledLock
import org.jetbrains.kotlin.idea.core.util.EDT
import org.jetbrains.kotlin.idea.util.FirPluginOracleService
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Holder for [ScriptClassRootsCache].
 *
 * Updates of [classpathRoots] performed asynchronously using the copy-on-write strategy.
 * [gatherRoots] called when updating is required. Cache will built from the scratch.
 *
 * Updates can be coalesced by using `update { invalidate() }` transaction.
 * As an alternative you can just call [invalidateAndCommit].
 *
 * After update roots changed event will be triggered if there are new root.
 * This will start indexing.
 * Also analysis cache will be cleared and changed opened script files will be reanalyzed.
 */
abstract class ScriptClassRootsUpdater(
    val project: Project,
    val manager: CompositeScriptConfigurationManager
) {
    private var lastSeen: ScriptClassRootsCache? = null
    private var invalidated: Boolean = false
    private var syncUpdateRequired: Boolean = false
    private val concurrentUpdates = AtomicInteger()
    private val lock = CheckCanceledLock()

    abstract fun gatherRoots(builder: ScriptClassRootsBuilder)

    abstract fun afterUpdate()

    private fun recreateRootsCache(): ScriptClassRootsCache {
        val builder = ScriptClassRootsBuilder(project)
        gatherRoots(builder)
        return builder.build()
    }

    /**
     * We need CAS due to concurrent unblocking sync update in [checkInvalidSdks]
     */
    private val cache: AtomicReference<ScriptClassRootsCache> = AtomicReference(ScriptClassRootsCache.EMPTY)

    init {
        ProjectManager.getInstance().addProjectManagerListener(project, object : ProjectManagerListener {
            override fun projectClosing(project: Project) {
                scheduledUpdate?.apply {
                    cancel()
                    awaitCompletion()
                }
            }
        })

        ensureUpdateScheduled()
    }

    val classpathRoots: ScriptClassRootsCache
        get() = cache.get()

    /**
     * @param synchronous Used from legacy FS cache only, don't use
     */
    @Suppress("UNUSED_PARAMETER")
    fun invalidate(file: VirtualFile, synchronous: Boolean = false) {
        lock.withLock {
            // todo: record invalided files for some optimisations in update
            invalidate(synchronous)
        }
    }

    /**
     * @param synchronous Used from legacy FS cache only, don't use
     */
    fun invalidate(synchronous: Boolean = false) {
        lock.withLock {
            checkInTransaction()
            invalidated = true
            if (synchronous) {
                syncUpdateRequired = true
            }
        }
    }

    fun invalidateAndCommit() {
        update { invalidate() }
    }

    fun isInTransaction(): Boolean {
        return concurrentUpdates.get() > 0
    }

    fun checkInTransaction() {
        check(isInTransaction())
    }

    inline fun <T> update(body: () -> T): T {
        beginUpdating()
        return try {
            body()
        } finally {
            commit()
        }
    }

    fun beginUpdating() {
        concurrentUpdates.incrementAndGet()
    }

    fun commit() {
        concurrentUpdates.decrementAndGet()

        // run update even in inner transaction
        // (outer transaction may be async, so it would be better to not wait it)
        scheduleUpdateIfInvalid()
    }

    fun addConfiguration(vFile: VirtualFile, configuration: ScriptCompilationConfigurationWrapper) {
        update {
            val builder = classpathRoots.builder(project)
            builder.add(vFile, configuration)
            cache.set(builder.build())
        }
    }

    private fun scheduleUpdateIfInvalid() {
        lock.withLock {
            if (!invalidated) return
            invalidated = false

            if (syncUpdateRequired || isUnitTestMode()) {
                syncUpdateRequired = false
                updateSynchronously()
            } else {
                ensureUpdateScheduled()
            }
        }
    }

    private var scheduledUpdate: BackgroundTaskUtil.BackgroundTask<*>? = null

    private fun ensureUpdateScheduled() {
        val disposable = KotlinPluginDisposable.getInstance(project)
        lock.withLock {
            scheduledUpdate?.cancel()

            if (!disposable.disposed) {
                scheduledUpdate = BackgroundTaskUtil.submitTask(disposable) {
                    doUpdate()
                }
            }
        }
    }

    private fun updateSynchronously() {
        lock.withLock {
            scheduledUpdate?.cancel()
            doUpdate(false)
        }
    }

    private fun doUpdate(underProgressManager: Boolean = true) {
        val disposable = KotlinPluginDisposable.getInstance(project)
        try {
            val updates = recreateRootsCacheAndDiff()

            if (!updates.changed) return

            if (underProgressManager) {
                ProgressManager.checkCanceled()
            }
            if (disposable.disposed) return

            if (updates.hasNewRoots) {
                runInEdt (ModalityState.NON_MODAL) {
                    runWriteAction {
                        if (project.isDisposed) return@runWriteAction

                        scriptingDebugLog { "kotlin.script.dependencies from ${updates.oldRoots} to ${updates.newRoots}" }

                        AdditionalLibraryRootsListener.fireAdditionalLibraryChanged(
                            project,
                            KotlinBundle.message("script.name.kotlin.script.dependencies"),
                            updates.oldRoots,
                            updates.newRoots,
                            KotlinBundle.message("script.name.kotlin.script.dependencies")
                        )

                        ScriptDependenciesModificationTracker.getInstance(project).incModificationCount()
                    }
                }
            }

            runReadAction {
                if (project.isDisposed) return@runReadAction
                PsiElementFinder.EP.findExtensionOrFail(KotlinScriptDependenciesClassFinder::class.java, project).clearCache()

                ScriptDependenciesModificationTracker.getInstance(project).incModificationCount()

                if (updates.hasUpdatedScripts) {
                    updateHighlighting(project) { file -> updates.isScriptChanged(file.path) }
                }

                val scriptClassRootsCache = updates.cache
                ScriptCacheDependencies(scriptClassRootsCache).save(project)
                lastSeen = scriptClassRootsCache
            }
        } finally {
            scheduledUpdate = null
        }
    }

    private fun recreateRootsCacheAndDiff(): ScriptClassRootsCache.Updates {
        while (true) {
            val old = cache.get()
            val new = recreateRootsCache()
            if (cache.compareAndSet(old, new)) {
                afterUpdate()
                return new.diff(project, lastSeen)
            }
        }
    }

    internal fun checkInvalidSdks(remove: Sdk? = null) {
        // sdks should be updated synchronously to avoid disposed roots usage
        do {
            val old = cache.get()
            val actualSdks = old.sdks.rebuild(project, remove = remove)
            if (actualSdks == old.sdks) return
            val new = old.withUpdatedSdks(actualSdks)
        } while (!cache.compareAndSet(old, new))

        ensureUpdateScheduled()
    }

    private fun updateHighlighting(project: Project, filter: (VirtualFile) -> Boolean) {
        if (!project.isOpen) return

        val openFiles = FileEditorManager.getInstance(project).allEditors.mapNotNull { it.file }
        val openedScripts = openFiles.filter(filter)

        if (openedScripts.isEmpty()) return

        /**
         * Scripts guts are everywhere in the plugin code, without them some functionality does not work,
         * And with them some other fir plugin related is broken
         * As FIR plugin does not have scripts support yet, just disabling not working one for now
         */
        @Suppress("DEPRECATION")
        if (project.service<FirPluginOracleService>().isFirPlugin()) return

        GlobalScope.launch(EDT(project)) {
            if (project.isDisposed) return@launch

            openedScripts.forEach {
                if (!it.isValid) return@forEach

                PsiManager.getInstance(project).findFile(it)?.let { psiFile ->
                    if (psiFile is KtFile) {
                        DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
                    }
                }
            }
        }
    }
}
