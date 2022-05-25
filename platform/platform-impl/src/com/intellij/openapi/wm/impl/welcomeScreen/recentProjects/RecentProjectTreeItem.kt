// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.recentProjects

import com.intellij.CommonBundle
import com.intellij.ide.*
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneableProject
import com.intellij.openapi.wm.impl.welcomeScreen.projectActions.RemoveSelectedProjectsAction
import com.intellij.util.BitUtil
import org.jetbrains.annotations.SystemIndependent
import java.awt.event.InputEvent
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Items of recent project tree:
 * - RootItem: collect all items of interface
 * - RecentProjectItem: an item project which can be interacted
 * - ProjectsGroupItem: group of RecentProjectItem
 *
 * @see com.intellij.openapi.wm.impl.welcomeScreen.ProjectsTabFactory.createWelcomeTab
 * @see com.intellij.ide.ManageRecentProjectsAction
 */
sealed interface RecentProjectTreeItem {
  fun displayName(): String

  fun children(): List<RecentProjectTreeItem>

  fun removeItem(event: AnActionEvent) {
    RemoveSelectedProjectsAction().actionPerformed(event)
  }
}

data class RecentProjectItem(
  val projectPath: @SystemIndependent String,
  @NlsSafe val projectName: String,
  @NlsSafe val displayName: String
) : RecentProjectTreeItem {
  override fun displayName(): String = displayName

  override fun children(): List<RecentProjectTreeItem> = emptyList()

  fun openProject(event: AnActionEvent) {
    // Force move focus to IdeFrame
    IdeEventQueue.getInstance().popupManager.closeAllPopups()

    val file = Paths.get(projectPath).normalize()
    if (!Files.exists(file)) {
      val exitCode = Messages.showYesNoDialog(
        IdeBundle.message("message.the.path.0.does.not.exist.maybe.on.remote", FileUtil.toSystemDependentName(projectPath)),
        IdeBundle.message("dialog.title.reopen.project"),
        CommonBundle.getOkButtonText(),
        IdeBundle.message("button.remove.from.list"),
        Messages.getErrorIcon()
      )

      if (exitCode == Messages.NO) {
        RecentProjectsManager.getInstance().removePath(projectPath)
      }

      return
    }

    val modifiers = event.modifiers
    val forceOpenInNewFrame = BitUtil.isSet(modifiers, InputEvent.CTRL_DOWN_MASK) ||
                              BitUtil.isSet(modifiers, InputEvent.SHIFT_DOWN_MASK) ||
                              event.place === ActionPlaces.WELCOME_SCREEN ||
                              LightEdit.owns(null)
    val options = OpenProjectTask.build()
      .withProjectToClose(null)
      .withForceOpenInNewFrame(forceOpenInNewFrame)
      .withRunConfigurators()

    RecentProjectsManagerBase.instanceEx.openProject(file, options)
  }
}

data class ProjectsGroupItem(
  val group: ProjectGroup,
  val children: List<RecentProjectItem>
) : RecentProjectTreeItem {
  override fun displayName(): String = group.name

  override fun children(): List<RecentProjectTreeItem> = children
}

data class CloneableProjectItem(
  val projectPath: @SystemIndependent String,
  @NlsSafe val projectName: String,
  @NlsSafe val displayName: String,
  val cloneableProject: CloneableProject
) : RecentProjectTreeItem {
  override fun displayName(): String = displayName

  override fun children(): List<RecentProjectTreeItem> = emptyList()
}

// The root node is required for the filtering tree
object RootItem : RecentProjectTreeItem {
  override fun displayName(): String = "" // Not visible in tree

  override fun children(): List<RecentProjectTreeItem> {
    val projects = mutableListOf<RecentProjectTreeItem>().apply {
      addAll(CloneableProjectsService.getInstance().collectCloneableProjects())
      addAll(RecentProjectListActionProvider.getInstance().collectProjects())
    }

    return projects
  }
}