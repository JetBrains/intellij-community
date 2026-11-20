// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.configurationStore.StoreReloadManager.Companion.getInstance
import com.intellij.ide.SaveAndSyncHandler.Companion.getInstance
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.concurrent.atomic.AtomicBoolean

private val LOG = Logger.getInstance(VcsFreezingProcess::class.java)

/**
 * Executes an action surrounding it with freezing-unfreezing of the ChangeListManager
 * and blocking/unblocking save/sync on frame de/activation.
 */
open class VcsFreezingProcess(private val myProject: Project, private val myOperationTitle: @Nls String, private val myRunnable: Runnable) {
  open fun execute() {
    val isEntered = AtomicBoolean()
    runFreezingInternal(
      myProject,
      myOperationTitle,
      action = myRunnable::run,
      enter = {
        invokeAndWaitIfNeeded {
          enterFreezing(myProject, isEntered)
        }
      },
      exit = {
        ProgressManager.getInstance().executeNonCancelableSection {
          invokeAndWaitIfNeeded {
            exitFreezing(myProject, isEntered)
          }
        }
      }
    )
  }

  interface Listener {
    fun onFreeze() {}

    fun onUnfreeze() {}

    companion object {
      @JvmField
      val TOPIC: Topic<Listener> = Topic.create<Listener>("Change List Manager Freeze", Listener::class.java)
    }
  }

  companion object {
    suspend fun runFreezing(project: Project, title: String, action: suspend () -> Unit) {
      val isEntered = AtomicBoolean()
      runFreezingInternal(
        project,
        title,
        action = { action() },
        enter = {
          withContext(Dispatchers.EDT) {
            enterFreezing(project, isEntered)
          }
        },
        exit = {
          withContext(NonCancellable + Dispatchers.EDT) {
            exitFreezing(project, isEntered)
          }
        }
      )
    }

    /**
     * @param enter runs [enterFreezing] on the EDT. A cancellation can end its wait before or after the EDT step runs.
     * @param exit runs [exitFreezing] on the EDT, also after a cancellation
     */
    private inline fun runFreezingInternal(project: Project, title: String, action: () -> Unit, enter: () -> Unit, exit: () -> Unit) {
      val changeListManager = ChangeListManagerEx.getInstanceEx(project)

      LOG.debug("starting")
      try {
        LOG.debug("saving documents, blocking project autosync")
        enter()
        try {
          LOG.debug("freezing the ChangeListManager")
          freeze(project, changeListManager, title)
          LOG.debug("running the operation")
          action()
          LOG.debug("operation completed.")
        }
        finally {
          LOG.debug("unfreezing the ChangeListManager")
          unfreeze(project, changeListManager)
        }
      }
      finally {
        LOG.debug("unblocking project autosync")
        exit()
      }
      LOG.debug("finished.")
    }

    @RequiresEdt
    private fun enterFreezing(project: Project, isEntered: AtomicBoolean) {
      try {
        saveAndBlock(project)
      }
      catch (e: Throwable) {
        unblock(project)
        throw e
      }
      isEntered.set(true)
    }

    /**
     * Undoes [enterFreezing] when the EDT did run it. The caller does not know that after a cancellation.
     */
    @RequiresEdt
    private fun exitFreezing(project: Project, isEntered: AtomicBoolean) {
      if (!isEntered.get()) return
      unblock(project)
    }

    @RequiresEdt
    @JvmStatic
    @ApiStatus.Internal
    fun saveAndBlock(project: Project) {
      getInstance(project).blockReloadingProjectOnExternalChanges()
      FileDocumentManager.getInstance().saveAllDocuments()

      val saveAndSyncHandler = getInstance()
      saveAndSyncHandler.blockSaveOnFrameDeactivation()
      saveAndSyncHandler.blockSyncOnFrameActivation()
    }

    @RequiresEdt
    @JvmStatic
    @ApiStatus.Internal
    fun unblock(project: Project) {
      getInstance(project).unblockReloadingProjectOnExternalChanges()
      val saveAndSyncHandler = getInstance()
      saveAndSyncHandler.unblockSaveOnFrameDeactivation()
      saveAndSyncHandler.unblockSyncOnFrameActivation()
    }

    private fun freeze(project: Project, changeListManager: ChangeListManagerEx, title: String) {
      project.getMessageBus().syncPublisher<Listener>(Listener.TOPIC).onFreeze()
      changeListManager.freeze(VcsBundle.message("local.changes.freeze.message", title))
    }

    private fun unfreeze(project: Project, changeListManager: ChangeListManagerEx) {
      project.getMessageBus().syncPublisher<Listener>(Listener.TOPIC).onUnfreeze()
      changeListManager.unfreeze()
    }
  }
}
