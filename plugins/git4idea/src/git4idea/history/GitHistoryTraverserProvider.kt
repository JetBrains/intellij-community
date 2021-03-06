// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.vcs.log.data.DataPackChangeListener
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsProjectLog

fun subscribeForGitHistoryTraverserCreation(project: Project, listener: GitHistoryTraverserListener, disposable: Disposable) {
  var logData: VcsLogData? = VcsProjectLog.getInstance(project).dataManager
  var traverser: GitHistoryTraverser? = null

  fun notifyTraverserCreated() {
    logData?.let {
      traverser = getTraverser(project, it)
      if (traverser != null) {
        listener.traverserCreated(traverser!!)
      }
    }
  }

  val dataPackChangeListener = DataPackChangeListener {
    if (traverser != null) {
      listener.graphUpdated()
    }
    else {
      notifyTraverserCreated()
    }
  }

  val projectLogListener = object : VcsProjectLog.ProjectLogListener {
    override fun logCreated(manager: VcsLogManager) {
      logData = manager.dataManager
      logData?.let {
        it.addDataPackChangeListener(dataPackChangeListener)
        notifyTraverserCreated()
      }
    }

    override fun logDisposed(manager: VcsLogManager) {
      logData?.removeDataPackChangeListener(dataPackChangeListener)
      logData = null
      traverser = null
    }
  }

  val subscriptionDisposable = Disposable {
    logData?.removeDataPackChangeListener(dataPackChangeListener)
  }
  val connection = project.messageBus.connect(subscriptionDisposable)
  connection.subscribe(VcsProjectLog.VCS_PROJECT_LOG_CHANGED, projectLogListener)

  Disposer.register(disposable, subscriptionDisposable)
}

fun getTraverser(project: Project): GitHistoryTraverser? {
  val logData = VcsProjectLog.getInstance(project).dataManager ?: return null
  return getTraverser(project, logData)
}

private fun getTraverser(project: Project, logData: VcsLogData): GitHistoryTraverser? {
  if (logData.dataPack.isFull) {
    return GitHistoryTraverserImpl(project, logData)
  }
  return null
}

interface GitHistoryTraverserListener {
  fun traverserCreated(newTraverser: GitHistoryTraverser)

  fun graphUpdated()
}