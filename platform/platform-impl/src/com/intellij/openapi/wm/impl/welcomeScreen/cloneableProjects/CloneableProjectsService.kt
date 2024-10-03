// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects

import com.intellij.CommonBundle
import com.intellij.ide.RecentProjectMetaInfo
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.CloneableProjectItem
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.messages.Topic
import com.intellij.util.messages.Topic.AppLevel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.SystemIndependent
import java.util.*

@Service(Level.APP)
class CloneableProjectsService {
  private val cloneableProjects = ContainerUtil.createLockFreeCopyOnWriteList<CloneableProject>()

  @CalledInAny
  fun runCloneTask(projectPath: @SystemIndependent String, cloneTask: CloneTask) {
    val taskInfo = cloneTask.taskInfo()
    val progressIndicator = CloneableProjectProgressIndicator(taskInfo)
    val cloneableProject = CloneableProject(projectPath, taskInfo, progressIndicator, CloneStatus.PROGRESS)
    addCloneableProject(cloneableProject)

    ApplicationManager.getApplication().executeOnPooledThread {
      ProgressManager.getInstance().runProcess(Runnable {
        val activity = VcsCloneCollector.cloneStarted(taskInfo)
        val cloneStatus: CloneStatus = try {
          cloneTask.run(progressIndicator)
        }
        catch (_: ProcessCanceledException) {
          CloneStatus.CANCEL
        }
        catch (exception: Throwable) {
          logger<CloneableProjectsService>().error(exception)
          CloneStatus.FAILURE
        }
        VcsCloneCollector.cloneFinished(activity, cloneStatus, taskInfo)

        when (cloneStatus) {
          CloneStatus.SUCCESS -> onSuccess(cloneableProject)
          CloneStatus.FAILURE -> onFailure(cloneableProject)
          CloneStatus.CANCEL -> onCancel(cloneableProject)
          else -> {}
        }
      }, progressIndicator)
    }
  }

  internal fun collectCloneableProjects(): Sequence<CloneableProjectItem> {
    val recentProjectManager by lazy { RecentProjectsManager.getInstance() as RecentProjectsManagerBase }
    return cloneableProjects.asSequence().map { cloneableProject ->
      val projectPath = cloneableProject.projectPath
      val projectName = recentProjectManager.getProjectName(projectPath)
      val displayName = recentProjectManager.getDisplayName(projectPath) ?: projectName
      CloneableProjectItem(projectPath = projectPath,
                           projectName = projectName,
                           displayName = displayName,
                           cloneableProject = cloneableProject)
    }
  }

  fun cloneCount(): Int {
    return cloneableProjects.filter { it.cloneStatus == CloneStatus.PROGRESS }.size
  }

  fun isCloneActive(): Boolean {
    return cloneableProjects.any { it.cloneStatus == CloneStatus.PROGRESS }
  }

  fun cancelClone(cloneableProject: CloneableProject) {
    cloneableProject.progressIndicator.cancel()
  }

  fun removeCloneableProject(cloneableProject: CloneableProject) {
    if (cloneableProject.cloneStatus == CloneStatus.PROGRESS) {
      cloneableProject.progressIndicator.cancel()
    }

    cloneableProjects.removeIf { it.projectPath == cloneableProject.projectPath }
    fireCloneRemovedEvent()
  }

  private fun upgradeCloneProjectToRecent(cloneableProject: CloneableProject) {
    val recentProjectsManager = RecentProjectsManager.getInstance() as RecentProjectsManagerBase
    recentProjectsManager.addRecentPath(cloneableProject.projectPath, RecentProjectMetaInfo())
    removeCloneableProject(cloneableProject)
  }

  private fun addCloneableProject(cloneableProject: CloneableProject) {
    cloneableProjects.removeIf { it.projectPath == cloneableProject.projectPath }
    cloneableProjects.add(cloneableProject)
    fireCloneAddedEvent(cloneableProject)
  }

  private fun onSuccess(cloneableProject: CloneableProject) {
    cloneableProject.cloneStatus = CloneStatus.SUCCESS
    upgradeCloneProjectToRecent(cloneableProject)
    fireCloneSuccessEvent()
  }

  private fun onFailure(cloneableProject: CloneableProject) {
    cloneableProject.cloneStatus = CloneStatus.FAILURE
    fireCloneFailedEvent()
  }

  private fun onCancel(cloneableProject: CloneableProject) {
    cloneableProject.cloneStatus = CloneStatus.CANCEL
    fireCloneCanceledEvent()
  }

  private fun fireCloneAddedEvent(cloneableProject: CloneableProject) {
    ApplicationManager.getApplication().invokeLater {
      ApplicationManager.getApplication().messageBus
        .syncPublisher(TOPIC)
        .onCloneAdded(cloneableProject.progressIndicator, cloneableProject.cloneTaskInfo)
    }
  }

  private fun fireCloneRemovedEvent() {
    ApplicationManager.getApplication().invokeLater {
      ApplicationManager.getApplication().messageBus
        .syncPublisher(TOPIC)
        .onCloneRemoved()
    }
  }

  private fun fireCloneSuccessEvent() {
    ApplicationManager.getApplication().invokeLater {
      ApplicationManager.getApplication().messageBus
        .syncPublisher(TOPIC)
        .onCloneSuccess()
    }
  }

  private fun fireCloneFailedEvent() {
    ApplicationManager.getApplication().invokeLater {
      ApplicationManager.getApplication().messageBus
        .syncPublisher(TOPIC)
        .onCloneFailed()
    }
  }

  private fun fireCloneCanceledEvent() {
    ApplicationManager.getApplication().invokeLater {
      ApplicationManager.getApplication().messageBus
        .syncPublisher(TOPIC)
        .onCloneCanceled()
    }
  }

  enum class CloneStatus {
    SUCCESS,
    PROGRESS,
    FAILURE,
    CANCEL
  }

  open class CloneTaskInfo(
    private val title: @NlsContexts.ProgressTitle String,
    private val cancelTooltipText: @Nls String,
    val actionTitle: @Nls String,
    val actionTooltipText: @Nls String,
    val failedTitle: @Nls String,
    val canceledTitle: @Nls String,
    val stopTitle: @Nls String,
    val stopDescription: @Nls String,
  ) : TaskInfo {
    override fun getTitle(): String = title
    override fun getCancelText(): String = CommonBundle.getCancelButtonText()
    override fun getCancelTooltipText(): String = cancelTooltipText
    override fun isCancellable(): Boolean = true

    @ApiStatus.Internal
    open fun getActivityData(): List<EventPair<*>> = emptyList()
  }

  data class CloneableProject(
    val projectPath: @SystemIndependent String,
    val cloneTaskInfo: CloneTaskInfo,
    val progressIndicator: ProgressIndicatorEx,
    var cloneStatus: CloneStatus
  )

  private class CloneableProjectProgressIndicator(cloneTaskInfo: CloneTaskInfo) : AbstractProgressIndicatorExBase() {
    init {
      setOwnerTask(cloneTaskInfo)
    }
  }

  interface CloneTask {
    fun taskInfo(): CloneTaskInfo

    fun run(indicator: ProgressIndicator): CloneStatus
  }

  interface CloneProjectListener {
    @RequiresEdt
    fun onCloneAdded(progressIndicator: ProgressIndicatorEx, taskInfo: TaskInfo) {
    }

    @RequiresEdt
    fun onCloneRemoved() {
    }

    @RequiresEdt
    fun onCloneSuccess() {
    }

    @RequiresEdt
    fun onCloneFailed() {
    }

    @RequiresEdt
    fun onCloneCanceled() {
    }
  }

  companion object {
    @AppLevel
    val TOPIC: Topic<CloneProjectListener> = Topic(CloneProjectListener::class.java, Topic.BroadcastDirection.NONE)

    @JvmStatic
    fun getInstance(): CloneableProjectsService = service<CloneableProjectsService>()
  }
}