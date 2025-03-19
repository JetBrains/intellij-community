// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ActiveChangeListTracker {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): ActiveChangeListTracker = project.service()
  }

  @RequiresEdt
  fun getActiveChangeListId(): String?

  @RequiresEdt
  fun isActiveChangeList(changeList: LocalChangeList): Boolean

  @RequiresEdt
  fun runUnderChangeList(changelistId: String, task: Runnable)
}

@ApiStatus.Internal
open class ActiveChangeListTrackerImpl(val project: Project) : ActiveChangeListTracker {
  companion object {
    private val LOG = logger<ActiveChangeListTracker>()
  }

  private var forcedChangeListId: String? = null

  override fun getActiveChangeListId(): String? = forcedChangeListId

  override fun isActiveChangeList(changeList: LocalChangeList): Boolean {
    val activeListId = getActiveChangeListId()
    if (activeListId != null) {
      return changeList.id == activeListId
    }
    else {
      return changeList.isDefault
    }
  }

  override fun runUnderChangeList(changelistId: String, task: Runnable) {
    if (!doRunUnderChangeList(changelistId, task)) {
      task.run()
    }
  }

  private fun doRunUnderChangeList(changelistId: String, task: Runnable): Boolean {
    val lstManager = LineStatusTrackerManager.getInstanceImpl(project)
    if (!lstManager.arePartialChangelistsEnabled()) {
      return false
    }

    if (forcedChangeListId != null) {
      LOG.warn("Conflicting forced changelist request", Throwable())
      return false
    }

    var success = false
    val changeListManager = ChangeListManagerImpl.getInstanceImpl(project)
    changeListManager.executeUnderDataLock {
      if (changeListManager.getChangeList(changelistId) != null) {
        forcedChangeListId = changelistId
        try {
          LOG.debug("running operation under changelist: $changelistId")
          success = true
          task.run()
        }
        finally {
          forcedChangeListId = null
        }
      }
    }
    return success
  }
}