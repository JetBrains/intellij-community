// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsProjectLog.Companion.isAvailable
import com.intellij.vcs.log.impl.VcsProjectLog.Companion.showRevisionInMainLog
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.ui.VcsLogUiImpl
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.NonExtendable

/**
 * An entry point for interacting with the VCS Log.
 * The main log is the non-closeable log UI intended to always be available.
 */
@NonExtendable
abstract class VcsProjectLog internal constructor() { // not an interface due to external deps
  open val dataManager: VcsLogData? = null

  @get:ApiStatus.Experimental
  abstract val logManagerState: StateFlow<VcsLogManager?>
  open val logManager: VcsLogManager?
    get() = logManagerState.value

  @Deprecated("Use VcsProjectLog.runInMainLog or get the ui from DataContext via VcsLogDataKeys.VCS_LOG_UI. As a last resort - VcsProjectLog.mainUi")
  open val mainLogUi: VcsLogUiImpl? = null

  /**
   * The main log UI or null if it was not initialized.
   * @see VcsProjectLog.runInMainLog
   */
  val mainUi: MainVcsLogUi? get() = mainLogUi

  /**
   * Open a new log tab with the given filters applied.
   */
  @RequiresEdt
  open fun openLogTab(filters: VcsLogFilterCollection): MainVcsLogUi? = null

  /**
   * Show the [hash] under [root] in any log that is capable of showing it.
   * Also select the [filePath] in the changes if possible.
   */
  @Internal
  abstract fun showRevisionAsync(root: VirtualFile, hash: Hash, filePath: FilePath?): Deferred<Boolean>

  abstract fun showRevisionInMainLog(root: VirtualFile, hash: Hash)

  abstract fun showRevisionInMainLog(hash: Hash)

  @Internal
  abstract fun canShowFileHistory(paths: Collection<FilePath>, revisionNumber: VcsRevisionNumber?): Boolean

  @Internal
  abstract fun openFileHistory(paths: Collection<FilePath>, revisionNumber: VcsRevisionNumber?)

  @Internal
  abstract fun openFileHistory(paths: Collection<FilePath>, revisionNumber: VcsRevisionNumber?, revisionToSelect: VcsRevisionNumber)

  /**
   * Executes the given action if the VcsProjectLog has been initialized. If not, then schedules the log initialization,
   * waits for it in a background task, and executes the action after the log is ready.
   */
  @Internal
  protected abstract fun runWhenLogIsReady(action: (VcsLogManager) -> Unit)

  /**
   * Executes the given action if and when the main log has been initialized.
   */
  @Internal
  protected abstract fun runInMainUi(@RequiresEdt consumer: (MainVcsLogUi) -> Unit)

  /**
   * Creates and initializes the [logManager] if [isAvailable]
   *
   * @param force if the index corruption should be ignored
   */
  @Internal
  abstract suspend fun init(force: Boolean): VcsLogManager?

  /**
   * Disposes the [logManager] if it is initialized, clears the caches, and then recreates the manager.
   */
  internal abstract suspend fun invalidateCaches()

  interface ProjectLogListener {
    @RequiresEdt
    fun logCreated(manager: VcsLogManager)

    @RequiresEdt
    fun logDisposed(manager: VcsLogManager)
  }

  companion object {
    @Topic.ProjectLevel
    @JvmField
    val VCS_PROJECT_LOG_CHANGED: Topic<ProjectLogListener> = Topic(ProjectLogListener::class.java, Topic.BroadcastDirection.NONE, true)

    @JvmStatic
    fun getLogProviders(project: Project): Map<VirtualFile, VcsLogProvider> {
      return VcsLogManager.findLogProviders(ProjectLevelVcsManager.getInstance(project).allVcsRoots.toList(), project)
    }

    @JvmStatic
    fun isAvailable(project: Project): Boolean {
      return !getLogProviders(project).isEmpty()
    }

    @JvmStatic
    fun getSupportedVcs(project: Project): Set<VcsKey> {
      return getLogProviders(project).values.mapTo(mutableSetOf()) { it.supportedVcs }
    }

    @JvmStatic
    fun getInstance(project: Project): VcsProjectLog = project.service<VcsProjectLog>()

    /**
     * @see VcsProjectLog.runWhenLogIsReady
     */
    @RequiresEdt
    fun runWhenLogIsReady(project: Project, action: (VcsLogManager) -> Unit) {
      getInstance(project).runWhenLogIsReady(action)
    }

    /**
     * @see VcsProjectLog.runInMainUi
     */
    @JvmStatic
    @RequiresEdt
    fun runInMainLog(project: Project, @RequiresEdt consumer: (MainVcsLogUi) -> Unit) {
      if (!isAvailable(project)) {
        VcsLogContentUtil.showLogIsNotAvailableMessage(project)
        return
      }

      getInstance(project).runInMainUi(consumer)
    }

    @JvmStatic
    @RequiresEdt
    fun showRevisionInMainLog(project: Project, root: VirtualFile, hash: Hash) {
      if (!isAvailable(project)) return
      getInstance(project).showRevisionInMainLog(root, hash)
    }

    /**
     * Consider using [showRevisionInMainLog] when the root is known
     */
    @RequiresEdt
    @JvmStatic
    fun showRevisionInMainLog(project: Project, hash: Hash) {
      if (!isAvailable(project)) return
      getInstance(project).showRevisionInMainLog(hash)
    }

    suspend fun awaitLogIsReady(project: Project): VcsLogManager? = project.serviceAsync<VcsProjectLog>().init(true)

    @Deprecated("awaitLogIsReady is preferred",
                ReplaceWith("awaitLogIsReady(project) != null",
                            "com.intellij.vcs.log.impl.VcsProjectLog.Companion.awaitLogIsReady"))
    suspend fun waitWhenLogIsReady(project: Project): Boolean = awaitLogIsReady(project) != null

    @Internal
    @RequiresBackgroundThread
    fun ensureLogCreated(project: Project): Boolean {
      ApplicationManager.getApplication().assertIsNonDispatchThread()
      return runBlockingMaybeCancellable {
        awaitLogIsReady(project) != null
      }
    }
  }
}