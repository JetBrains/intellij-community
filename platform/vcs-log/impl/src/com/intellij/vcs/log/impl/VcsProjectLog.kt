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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.ui.VcsLogUiImpl
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.NonExtendable

/**
 * An entry point for interacting with the VCS Log.
 */
@NonExtendable
abstract class VcsProjectLog internal constructor() { // not an interface due to external deps
  open val dataManager: VcsLogData? = null

  open val logManager: VcsLogManager? = null

  /** The instance of the [MainVcsLogUi] or null if the log was not initialized yet. */
  open val mainLogUi: VcsLogUiImpl? = null

  @RequiresEdt
  open fun openLogTab(filters: VcsLogFilterCollection): MainVcsLogUi? = null

  @Internal
  abstract fun showRevisionAsync(root: VirtualFile, hash: Hash, filePath: FilePath?): Deferred<Boolean>

  abstract fun showRevisionInMainLog(root: VirtualFile, hash: Hash)

  abstract fun showRevisionInMainLog(hash: Hash)

  @Internal
  protected abstract fun runWhenLogIsReady(action: (VcsLogManager) -> Unit)

  @Internal
  protected abstract fun runInMainUi(consumer: (MainVcsLogUi) -> Unit)

  @Internal
  abstract suspend fun init(force: Boolean): VcsLogManager?

  @Internal
  abstract suspend fun reinit(beforeCreateLog: (suspend () -> Unit)? = null): VcsLogManager?

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
     * Executes the given action if the VcsProjectLog has been initialized. If not, then schedules the log initialization,
     * waits for it in a background task, and executes the action after the log is ready.
     */
    @RequiresEdt
    fun runWhenLogIsReady(project: Project, action: (VcsLogManager) -> Unit) {
      getInstance(project).runWhenLogIsReady(action)
    }

    /**
     * Executes the given action if and when the main UI has been initialized.
     */
    @JvmStatic
    @RequiresEdt
    fun runInMainLog(project: Project, consumer: (MainVcsLogUi) -> Unit) {
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