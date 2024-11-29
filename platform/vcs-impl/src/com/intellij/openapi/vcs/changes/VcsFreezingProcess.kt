// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.configurationStore.StoreReloadManager.Companion.getInstance
import com.intellij.ide.SaveAndSyncHandler.Companion.getInstance
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.util.messages.Topic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls

private val LOG = Logger.getInstance(VcsFreezingProcess::class.java)

/**
 * Executes an action surrounding it with freezing-unfreezing of the ChangeListManager
 * and blocking/unblocking save/sync on frame de/activation.
 */
open class VcsFreezingProcess(private val myProject: Project, private val myOperationTitle: @Nls String, private val myRunnable: Runnable) {
  open fun execute() {
    runFreezingInternal(
      myProject,
      myOperationTitle,
      action = myRunnable::run,
      saveAndBlock = {
        invokeAndWaitIfNeeded {
          saveAndBlock(myProject)
        }
      },
      unblock = {
        invokeAndWaitIfNeeded {
          unblock(myProject)
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
      runFreezingInternal(
        project,
        title,
        action = { action() },
        saveAndBlock = {
          withContext(Dispatchers.EDT) {
            saveAndBlock(project)
          }
        },
        unblock = {
          withContext(Dispatchers.EDT) {
            unblock(project)
          }
        }
      )
    }

    private inline fun runFreezingInternal(project: Project, title: String, action: () -> Unit, saveAndBlock: () -> Unit, unblock: () -> Unit) {
      val changeListManager = ChangeListManagerEx.getInstanceEx(project)

      LOG.debug("starting")
      try {
        LOG.debug("saving documents, blocking project autosync")
        saveAndBlock()
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
        unblock()
      }
      LOG.debug("finished.")
    }

    private fun saveAndBlock(project: Project) {
      getInstance(project).blockReloadingProjectOnExternalChanges()
      FileDocumentManager.getInstance().saveAllDocuments()

      val saveAndSyncHandler = getInstance()
      saveAndSyncHandler.blockSaveOnFrameDeactivation()
      saveAndSyncHandler.blockSyncOnFrameActivation()
    }

    private fun unblock(project: Project) {
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
