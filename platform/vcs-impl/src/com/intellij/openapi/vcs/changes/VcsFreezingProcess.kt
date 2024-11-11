// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.configurationStore.StoreReloadManager.Companion.getInstance
import com.intellij.ide.SaveAndSyncHandler.Companion.getInstance
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.Nls

/**
 * Executes an action surrounding it with freezing-unfreezing of the ChangeListManager
 * and blocking/unblocking save/sync on frame de/activation.
 */
open class VcsFreezingProcess(private val myProject: Project, private val myOperationTitle: @Nls String, private val myRunnable: Runnable) {
  private val myChangeListManager: ChangeListManagerEx = ChangeListManagerEx.getInstanceEx(myProject)

  open fun execute() {
    LOG.debug("starting")
    try {
      LOG.debug("saving documents, blocking project autosync")
      saveAndBlockInAwt()
      try {
        LOG.debug("freezing the ChangeListManager")
        freeze()
        LOG.debug("running the operation")
        myRunnable.run()
        LOG.debug("operation completed.")
      }
      finally {
        LOG.debug("unfreezing the ChangeListManager")
        unfreeze()
      }
    }
    finally {
      LOG.debug("unblocking project autosync")
      unblockInAwt()
    }
    LOG.debug("finished.")
  }

  private fun saveAndBlockInAwt() {
    ApplicationManager.getApplication().invokeAndWait(Runnable {
      getInstance(myProject).blockReloadingProjectOnExternalChanges()
      FileDocumentManager.getInstance().saveAllDocuments()

      val saveAndSyncHandler = getInstance()
      saveAndSyncHandler.blockSaveOnFrameDeactivation()
      saveAndSyncHandler.blockSyncOnFrameActivation()
    })
  }

  private fun unblockInAwt() {
    ApplicationManager.getApplication().invokeAndWait(Runnable {
      getInstance(myProject).unblockReloadingProjectOnExternalChanges()
      val saveAndSyncHandler = getInstance()
      saveAndSyncHandler.unblockSaveOnFrameDeactivation()
      saveAndSyncHandler.unblockSyncOnFrameActivation()
    })
  }

  private fun freeze() {
    myProject.getMessageBus().syncPublisher<Listener>(Listener.TOPIC).onFreeze()
    myChangeListManager.freeze(VcsBundle.message("local.changes.freeze.message", myOperationTitle))
  }

  private fun unfreeze() {
    myProject.getMessageBus().syncPublisher<Listener>(Listener.TOPIC).onUnfreeze()
    myChangeListManager.unfreeze()
  }

  interface Listener {
    fun onFreeze() {}

    fun onUnfreeze() {}

    companion object {
      @JvmField
      val TOPIC: Topic<Listener> = Topic.create<Listener>("Change List Manager Freeze", Listener::class.java)
    }
  }

  private companion object {
    private val LOG = Logger.getInstance(VcsFreezingProcess::class.java)
  }
}
