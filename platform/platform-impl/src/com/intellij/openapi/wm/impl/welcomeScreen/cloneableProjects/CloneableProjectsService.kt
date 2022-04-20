// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects

import com.intellij.CommonBundle
import com.intellij.ide.RecentProjectMetaInfo
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.CloneableProjectItem
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.Nls
import java.util.Collections.synchronizedList

@Service(Level.APP)
class CloneableProjectsService {
  private val projectProgressIndicators: MutableList<CloneableProjectProgressIndicator> = synchronizedList(mutableListOf())

  @RequiresEdt
  fun runCloneTask(
    projectPath: String,
    cloneTaskInfo: CloneTaskInfo,
    cloneTask: (ProgressIndicator) -> CloneResult,
    onSuccess: (CloneResult) -> CloneResult
  ) {
    val progressIndicator = CloneableProjectProgressIndicator(projectPath, cloneTaskInfo)
    addCloneableProject(progressIndicator)
    fireAddEvent(progressIndicator)

    val cloneProcess = Runnable {
      when (val cloneResult = cloneTask(progressIndicator)) {
        CloneResult.DOWNLOADED -> {
          upgradeCloneProjectToRecent(progressIndicator)
          ApplicationManager.getApplication().invokeLater {
            onSuccess(cloneResult)
          }
        }
        else -> {}
      }
    }

    // Execute clone
    ApplicationManager.getApplication().executeOnPooledThread {
      ProgressManager.getInstance().runProcess(cloneProcess, progressIndicator)
    }
  }

  fun collectCloneableProjects(): List<CloneableProjectItem> {
    val recentProjectManager = RecentProjectsManager.getInstance() as RecentProjectsManagerBase

    return projectProgressIndicators.map { projectProgressIndicators ->
      val projectPath = projectProgressIndicators.projectPath

      val projectName = recentProjectManager.getProjectName(projectPath)
      val displayName = recentProjectManager.getDisplayName(projectPath) ?: projectName

      CloneableProjectItem(projectPath, projectName, displayName, projectProgressIndicators, projectProgressIndicators.cloneTaskInfo)
    }
  }

  fun removeCloneProject(progressIndicator: ProgressIndicator) {
    progressIndicator.cancel()
    projectProgressIndicators.remove(progressIndicator)
    fireEvent()
  }

  private fun addCloneableProject(progressIndicator: CloneableProjectProgressIndicator) {
    projectProgressIndicators.add(progressIndicator)
    fireEvent()
  }

  private fun upgradeCloneProjectToRecent(progressIndicator: CloneableProjectProgressIndicator) {
    projectProgressIndicators.remove(progressIndicator)

    val recentProjectsManager = RecentProjectsManager.getInstance() as RecentProjectsManagerBase
    recentProjectsManager.addRecentPath(progressIndicator.projectPath, RecentProjectMetaInfo())
    fireEvent()
  }

  private fun fireEvent() {
    ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).change()
  }

  private fun fireAddEvent(progressIndicator: CloneableProjectProgressIndicator) {
    ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).add(progressIndicator, progressIndicator.cloneTaskInfo)
  }

  enum class CloneResult {
    SUCCESS,
    DOWNLOADED,
    FAILURE
  }

  class CloneTaskInfo(
    @NlsContexts.ProgressTitle private val title: String,
    @Nls private val cancelTooltipText: String
  ) : TaskInfo {
    override fun getTitle(): String = title
    override fun getCancelText(): String = CommonBundle.getCancelButtonText()
    override fun getCancelTooltipText(): String = cancelTooltipText
    override fun isCancellable(): Boolean = true
  }

  private class CloneableProjectProgressIndicator(
    val projectPath: String,
    val cloneTaskInfo: CloneTaskInfo
  ) : AbstractProgressIndicatorExBase() {
    init {
      setOwnerTask(cloneTaskInfo)
    }
  }

  interface CloneProjectChange {
    @JvmDefault
    fun add(progressIndicator: ProgressIndicatorEx, taskInfo: TaskInfo) {}

    @JvmDefault
    fun change() {}
  }

  companion object {
    @JvmField
    val TOPIC = Topic(CloneProjectChange::class.java)

    @JvmStatic
    fun getInstance() = service<CloneableProjectsService>()
  }
}