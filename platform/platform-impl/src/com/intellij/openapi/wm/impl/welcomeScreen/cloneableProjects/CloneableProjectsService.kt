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
  fun runCloneTask(projectPath: String, cloneTask: CloneTask) {
    val progressIndicator = CloneableProjectProgressIndicator(projectPath, cloneTask.taskInfo())
    addCloneableProject(progressIndicator)

    ApplicationManager.getApplication().executeOnPooledThread {
      ProgressManager.getInstance().runProcess(Runnable {
        when (cloneTask.run(progressIndicator)) {
          CloneStatus.SUCCESS -> {
            upgradeCloneProjectToRecent(progressIndicator)
          }
          else -> {}
        }
      }, progressIndicator)
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
    fireCloneRemovedEvent()
  }

  private fun addCloneableProject(progressIndicator: CloneableProjectProgressIndicator) {
    projectProgressIndicators.add(progressIndicator)
    fireCloneAddedEvent(progressIndicator)
  }

  private fun upgradeCloneProjectToRecent(progressIndicator: CloneableProjectProgressIndicator) {
    projectProgressIndicators.remove(progressIndicator)

    val recentProjectsManager = RecentProjectsManager.getInstance() as RecentProjectsManagerBase
    recentProjectsManager.addRecentPath(progressIndicator.projectPath, RecentProjectMetaInfo())

    fireCloneRemovedEvent()
  }

  private fun fireCloneAddedEvent(progressIndicator: CloneableProjectProgressIndicator) {
    ApplicationManager.getApplication().messageBus
      .syncPublisher(TOPIC)
      .onCloneAdded(progressIndicator, progressIndicator.cloneTaskInfo)
  }

  private fun fireCloneRemovedEvent() {
    ApplicationManager.getApplication().messageBus
      .syncPublisher(TOPIC)
      .onCloneRemoved()
  }

  enum class CloneStatus {
    SUCCESS,
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

  private inner class CloneableProjectProgressIndicator(
    val projectPath: String,
    val cloneTaskInfo: CloneTaskInfo
  ) : AbstractProgressIndicatorExBase() {
    init {
      setOwnerTask(cloneTaskInfo)
    }

    override fun cancel() {
      super.cancel()
      projectProgressIndicators.remove(this)
      fireCloneRemovedEvent()
    }
  }

  interface CloneTask {
    fun taskInfo(): CloneTaskInfo

    fun run(indicator: ProgressIndicator): CloneStatus
  }

  interface CloneProjectListener {
    @JvmDefault
    fun onCloneAdded(progressIndicator: ProgressIndicatorEx, taskInfo: TaskInfo) {}

    @JvmDefault
    fun onCloneRemoved() {}
  }

  companion object {
    @JvmField
    val TOPIC = Topic(CloneProjectListener::class.java)

    @JvmStatic
    fun getInstance() = service<CloneableProjectsService>()
  }
}