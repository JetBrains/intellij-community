// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.ucache

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.applyIf
import com.intellij.util.ui.EDT.isCurrentThreadEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
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
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

val LOG = logger<ScriptClassRootsUpdater>()

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
    private var invalidated: Boolean = false
    private var syncUpdateRequired: Boolean = false
    private val invalidationLock = ReentrantLock()
    private val updateState = AtomicReference(UpdateState.NONE)

    /**
     * Represents the state of the [cache] update process.
     */
    private enum class UpdateState {
        // nothing runs or is scheduled
        NONE,
        // update process is scheduled for the future
        SCHEDULED,
        // update process runs right now
        RUNNING
    }

    abstract fun gatherRoots(builder: ScriptClassRootsBuilder)

    abstract fun afterUpdate()

    abstract fun onTrivialUpdate()

    abstract fun onUpdateException(exception: Exception)

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

        if (KotlinPluginModeProvider.isK1Mode()) {
            performUpdate(synchronous = false)
        }
    }

    val classpathRoots: ScriptClassRootsCache
        get() = cache.get()

    /**
     * @param synchronous Used from legacy FS cache only, don't use
     */
    @Suppress("UNUSED_PARAMETER")
    fun invalidate(file: VirtualFile, synchronous: Boolean = false) {
        invalidationLock.withLock {
            // todo: record invalided files for some optimisations in update
            invalidate(synchronous)
        }
    }

    /**
     * @param synchronous Used from legacy FS cache only, don't use
     */
    fun invalidate(synchronous: Boolean = false) {
        invalidationLock.withLock {
            invalidated = true
            if (synchronous) {
                syncUpdateRequired = true
            }
        }
    }

    fun invalidateAndCommit() {
        if (KotlinPluginModeProvider.isK1Mode()) {
            update { invalidate() }
        }
    }

    /**
     * Indicates if there is an update to happen.
     * This method considers both scheduled async and ongoing sync translations.
     *
     * @return true if there is scheduled async or ongoing synchronous transaction.
     * @see performUpdate
     */
    fun isTransactionAboutToHappen(): Boolean {
        return updateState.get() != UpdateState.NONE
    }

    inline fun <T> update(body: () -> T): T {
        return try {
            body()
        } finally {
            commit()
        }
    }

    fun commit() {
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
        val isSync = invalidationLock.withLock {
            invalidated.ifFalse { return }
            invalidated = false

            return@withLock (syncUpdateRequired || isUnitTestMode()).also {
                it.ifTrue { syncUpdateRequired = false }
            }
        }
        performUpdate(synchronous = isSync)
    }

    private var scheduledUpdate: BackgroundTaskUtil.BackgroundTask<*>? = null

    private fun performUpdate(synchronous: Boolean = false) {
        if (KotlinPluginModeProvider.isK2Mode()) return
        val disposable = KotlinPluginDisposable.getInstance(project)
        if (disposable.disposed) return

        when {
            synchronous -> updateSynchronously()
            else -> ensureUpdateScheduled(disposable)
        }
    }

    private fun ensureUpdateScheduled(parentDisposable: Disposable) {
        if (updateState.compareAndSet(UpdateState.NONE, UpdateState.SCHEDULED)) {
            scheduledUpdate = BackgroundTaskUtil.submitTask(parentDisposable) {
                if (updateState.compareAndSet(UpdateState.SCHEDULED, UpdateState.RUNNING)) {
                    doUpdate(synchronous = false)
                }
            }
        } else {
            LOG.debug("Will not schedule update, state: ${updateState.get()}")
        }
    }

    private fun updateSynchronously() {
        val previousState = updateState.getAndSet(UpdateState.RUNNING)
        if (previousState == UpdateState.SCHEDULED) {
            scheduledUpdate?.cancel()
        }
        doUpdate(synchronous = true)
    }

    private fun doUpdate(synchronous: Boolean, underProgressManager: Boolean = true) {
        val disposable = KotlinPluginDisposable.getInstance(project)
        var shouldRescheduleOnException = false
        try {
            doUpdateImpl(underProgressManager, disposable)
        } catch (ex: ProcessCanceledException) {
            shouldRescheduleOnException = true
            throw ex
        } catch (ex: Exception) {
            if (ex is ControlFlowException) {
                throw ex
            }
            LOG.error("Exception during script roots update", ex)
            onUpdateException(ex)
        } finally {
            scheduledUpdate = null
            updateState.set(UpdateState.NONE)
            // reschedule if happened in async call
            if (shouldRescheduleOnException && !synchronous && !disposable.disposed) {
                ensureUpdateScheduled(disposable)
            }
        }
    }

    private fun doUpdateImpl(underProgressManager: Boolean, disposable: KotlinPluginDisposable) {
        val updates = recreateRootsCacheAndDiff()

        if (!updates.changed) {
            LOG.debug("Does not have any new updates, aborting")
            return
        }

        if (underProgressManager) {
            ProgressManager.checkCanceled()
        }
        if (disposable.disposed) return

        updateConfigurationInScriptRoots(updates)
        if (updates.hasNewRoots) {
            updateModificationTracker()
        }

        launchHighlightingUpdateIfNeeded(updates)
    }

    private fun updateConfigurationInScriptRoots(updates: ScriptClassRootsCache.Updates) {
        val manager = VirtualFileManager.getInstance()

        val updatedScriptPaths = when (updates) {
            is ScriptClassRootsCache.IncrementalUpdates -> updates.updatedScripts
            else -> updates.cache.scriptsPaths()
        }

        val scriptVirtualFiles = updatedScriptPaths.takeUnless { it.isEmpty() }
        // the current update is finished
        if (scriptVirtualFiles == null) {
            onTrivialUpdate()
            return
        }

        val updatedScriptFiles = scriptVirtualFiles.asSequence()
            .map {
                val byNioPath = manager.findFileByNioPath(Paths.get(it))
                if (byNioPath == null) { // e.g. jupyter notebooks have their .kts in memory only
                    val path = it.applyIf(it.startsWith("/")) { it.replaceFirst("/", "") }
                    LightVirtualFile(path)
                } else {
                    byNioPath
                }
            }

        val actualScriptPaths = updates.cache.scriptsPaths()
        val (filesToAddOrUpdate, filesToRemove) = updatedScriptFiles.partition { actualScriptPaths.contains(it.path) }

        // Here we're sometimes under read-lock.
        // There is no way to acquire write-lock (on EDT) without releasing this thread.
        applyDiffToModelAsync(filesToAddOrUpdate, filesToRemove)
    }

    private fun launchHighlightingUpdateIfNeeded(updates: ScriptClassRootsCache.Updates) {
        if (!updates.hasUpdatedScripts) {
            return
        }

        scope.async {
            readAction {
                if (project.isDisposed) return@readAction

                runCatching {
                    updateHighlighting(project) { file -> updates.isScriptChanged(file.path) }
                }.onFailure {
                    if (it is ControlFlowException) {
                        throw it
                    }
                    LOG.error("Failed to update highlighting", it)
                }
            }
        }
    }

    private fun updateModificationTracker() = scope.async {
        withContext(Dispatchers.EDT) {
            writeAction {
                if (project.isDisposed) return@writeAction

                ScriptDependenciesModificationTracker.getInstance(project).incModificationCount()
            }
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

        val builderSnapshot = (WorkspaceModel.getInstance(project) as WorkspaceModelInternal).getBuilderSnapshot()
        builderSnapshot.syncScriptEntities(project, filesToAddOrUpdate, filesToRemove) // time-consuming call
        val replacement = builderSnapshot.getStorageReplacement()

        runInEdt(ModalityState.nonModal()) {
            val replaced = runWriteAction {
                if (project.isDisposed) false
                else (WorkspaceModel.getInstance(project) as WorkspaceModelInternal).replaceProjectModel(replacement)
            }
            if (!replaced) {
                // initiate update once again
                applyDiffToModelAsync(filesToAddOrUpdate, filesToRemove)
            } else {
                // notify after changes are applied
                afterUpdate()
            }
        }
    }

    private fun recreateRootsCacheAndDiff(): ScriptClassRootsCache.Updates {
        while (true) {
            val old = cache.get()
            val new = recreateRootsCache()
            if (cache.compareAndSet(old, new)) {
                return new.diff(old)
            }
        }
    }

    internal fun checkInvalidSdks(vararg remove: Sdk) {
        // sdks should be updated synchronously to avoid disposed roots usage
        do {
            val old = cache.get()
            val actualSdks =
                if (remove.isEmpty()) {
                    old.sdks.rebuild(project, null)
                } else {
                    var sdks = old.sdks
                    for (sdk in remove) {
                        sdks = sdks.rebuild(project, sdk)
                    }
                    sdks
                }
            if (actualSdks == old.sdks) return
            val new = old.withUpdatedSdks(actualSdks)
        } while (!cache.compareAndSet(old, new))

        performUpdate(synchronous = false)
    }

    private fun updateHighlighting(project: Project, filter: (VirtualFile) -> Boolean) {
        /**
         * Scripts guts are everywhere in the plugin code, without them some functionality does not work,
         * And with them some other fir plugin related is broken
         * As FIR plugin does not have scripts support yet, just disabling not working one for now
         */
        @Suppress("DEPRECATION")
        if (!project.isOpen || project.service<FirPluginOracleService>().isFirPlugin()) return
        // tests do not like sudden daemon restarts
        if (ApplicationManager.getApplication().isUnitTestMode) return

        val openFiles = FileEditorManager.getInstance(project).allEditors.mapNotNull { it.file }
        val openedScripts = openFiles.filter(filter) 

        if (openedScripts.isEmpty()) return

        openedScripts.forEach {
            if (it.isValid) {
                (PsiManager.getInstance(project).findFile(it) as? KtFile)?.let { ktFile -> DaemonCodeAnalyzer.getInstance(project).restart(ktFile) }
            }
        }
    }
}
