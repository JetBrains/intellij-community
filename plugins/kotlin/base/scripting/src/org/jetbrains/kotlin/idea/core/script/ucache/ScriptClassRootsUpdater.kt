// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.ucache

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.psi.PsiManager
import com.intellij.refactoring.suggested.createSmartPointer
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.applyIf
import com.intellij.util.ui.EDT.isCurrentThreadEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.base.util.CheckCanceledLock
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.core.script.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.idea.util.FirPluginOracleService
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import java.nio.file.Paths
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
    val manager: CompositeScriptConfigurationManager,
    val scope: CoroutineScope
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
                }
            }
        })

        performUpdate(synchronous = false)
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
            checkHasTransactionToHappen()
            invalidated = true
            if (synchronous) {
                syncUpdateRequired = true
            }
        }
    }

    fun invalidateAndCommit() {
        update { invalidate() }
    }

    /**
     * Indicates if there is an update to happen.
     * This method considers both scheduled async and ongoing sync translations.
     *
     * @return true if there is scheduled async or ongoing synchronous transaction.
     * @see performUpdate
     */
    fun isTransactionAboutToHappen(): Boolean {
        return concurrentUpdates.get() > 0
    }

    fun checkHasTransactionToHappen() {
        check(isTransactionAboutToHappen())
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
            builder.warnAboutDependenciesExistence(false)
            builder.add(vFile, configuration)
            cache.set(builder.build())
        }
    }

    private fun scheduleUpdateIfInvalid() {
        lock.withLock {
            invalidated.ifFalse { return }
            invalidated = false

            val isSync = (syncUpdateRequired || isUnitTestMode()).also {
                it.ifTrue { syncUpdateRequired = false }
            }
            performUpdate(synchronous = isSync)
        }
    }

    private var scheduledUpdate: BackgroundTaskUtil.BackgroundTask<*>? = null

    private fun performUpdate(synchronous: Boolean = false) {
        val disposable = KotlinPluginDisposable.getInstance(project)
        if (disposable.disposed) return

        beginUpdating()
        when {
            synchronous -> updateSynchronously()
            else -> ensureUpdateScheduled(disposable)
        }
    }


    private fun ensureUpdateScheduled(parentDisposable: Disposable) {
        lock.withLock {
            scheduledUpdate?.cancel()

            scheduledUpdate = BackgroundTaskUtil.submitTask(parentDisposable) {
                doUpdate()
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

            val manager = VirtualFileManager.getInstance()
            val updatedScriptPaths = when (updates) {
                is ScriptClassRootsCache.IncrementalUpdates -> updates.updatedScripts
                else -> updates.cache.scriptsPaths()
            }

            updatedScriptPaths.takeUnless { it.isEmpty() }?.asSequence()
                ?.map {
                    val byNioPath = manager.findFileByNioPath(Paths.get(it))
                    if (byNioPath == null) { // e.g. jupyter notebooks have their .kts in memory only
                        val path = it.applyIf(it.startsWith("/")) { it.replaceFirst("/", "") }
                        LightVirtualFile(path)
                    } else {
                        byNioPath
                    }
                }
                ?.let { updatedScriptFiles ->
                    val actualScriptPaths = updates.cache.scriptsPaths()
                    val (filesToAddOrUpdate, filesToRemove) = updatedScriptFiles.partition { actualScriptPaths.contains(it.path) }

                    // Here we're sometimes under read-lock.
                    // There is no way to acquire write-lock (on EDT) without releasing this thread.

                    applyDiffToModelAsync(filesToAddOrUpdate, filesToRemove)
                }

            if (updates.hasNewRoots) {
                runInEdt(ModalityState.nonModal()) {
                    runWriteAction {
                        if (project.isDisposed) return@runWriteAction
                        ScriptDependenciesModificationTracker.getInstance(project).incModificationCount()
                    }
                }
            }

            runReadAction {
                if (project.isDisposed) return@runReadAction

                if (updates.hasUpdatedScripts) {
                    updateHighlighting(project) { file -> updates.isScriptChanged(file.path) }
                }

                val scriptClassRootsCache = updates.cache
                lastSeen = scriptClassRootsCache
            }
        } finally {
            scheduledUpdate = null
            concurrentUpdates.decrementAndGet()
        }
    }

    private fun applyDiffToModelAsync(
        filesToAddOrUpdate: List<VirtualFile>,
        filesToRemove: List<VirtualFile>
    ) {
        if (ApplicationManager.getApplication().isUnitTestMode || !isCurrentThreadEdt()) {
            applyDiffToModel(filesToAddOrUpdate, filesToRemove)
        } else {
            if (project.isDisposed) return
            BackgroundTaskUtil.executeOnPooledThread(KotlinPluginDisposable.getInstance(project)) {
                applyDiffToModel(filesToAddOrUpdate, filesToRemove)
            }
        }
    }

    private fun applyDiffToModel(
        filesToAddOrUpdate: List<VirtualFile>,
        filesToRemove: List<VirtualFile>
    ) {
        if (project.isDisposed) return

        val builderSnapshot = WorkspaceModel.getInstance(project).getBuilderSnapshot()
        builderSnapshot.syncScriptEntities(project, filesToAddOrUpdate, filesToRemove) // time-consuming call
        val replacement = builderSnapshot.getStorageReplacement()

        runInEdt(ModalityState.nonModal()) {
            val replaced = runWriteAction {
                if (project.isDisposed) false
                else WorkspaceModel.getInstance(project).replaceProjectModel(replacement)
            }
            if (!replaced) {
                // initiate update once again
                applyDiffToModelAsync(filesToAddOrUpdate, filesToRemove)
            }
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

        performUpdate(synchronous = false)
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
        if (project.isDisposed || project.service<FirPluginOracleService>().isFirPlugin()) return

        val ktFiles = openedScripts.mapNotNull {
            if (!it.isValid) return@mapNotNull null

            val ktFile = PsiManager.getInstance(project).findFile(it) as? KtFile
            ktFile?.createSmartPointer()
        }
        if (ktFiles.isNotEmpty()) {
            scope.launch {
                withContext(Dispatchers.EDT) {
                    blockingContext {
                        ktFiles.forEach {
                            val ktFile = it.element ?: return@forEach
                            DaemonCodeAnalyzer.getInstance(project)
                                .restart(ktFile)
                        }
                    }
                }
            }
        }
    }
}
