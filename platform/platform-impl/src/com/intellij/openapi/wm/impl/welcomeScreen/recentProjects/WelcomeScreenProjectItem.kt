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
import com.intellij.util.BitUtil
import org.jetbrains.annotations.SystemIndependent
import java.awt.event.InputEvent
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Items of welcome screen tree
 * - RecentProject: project which can be open
 * - RecentProjectGroup: group of recent projects
 */
sealed interface WelcomeScreenProjectItem {
  fun name(): String

  fun children(): List<WelcomeScreenProjectItem>
}

class RecentProjectItem(
  val projectPath: @SystemIndependent String,
  @NlsSafe val projectName: String,
  @NlsSafe val displayName: String
) : WelcomeScreenProjectItem {
  fun openProject(event: AnActionEvent) {
    // Force move focus to IdeFrame
    IdeEventQueue.getInstance().popupManager.closeAllPopups()

    val file = Paths.get(projectPath).normalize()
    if (!Files.exists(file)) {
      val exitCode = Messages.showDialog(null,
                                         IdeBundle.message("message.the.path.0.does.not.exist.maybe.on.remote",
                                                           FileUtil.toSystemDependentName(projectPath)),
                                         IdeBundle.message("dialog.title.reopen.project"),
                                         arrayOf(CommonBundle.getOkButtonText(), IdeBundle.message("button.remove.from.list")),
                                         0,
                                         Messages.getErrorIcon())
      if (exitCode == 1) {
        RecentProjectsManager.getInstance().removePath(projectPath)
      }

      return
    }

    val modifiers = event.modifiers
    val forceOpenInNewFrame = BitUtil.isSet(modifiers, InputEvent.CTRL_DOWN_MASK) ||
                              BitUtil.isSet(modifiers, InputEvent.SHIFT_DOWN_MASK) ||
                              event.place === ActionPlaces.WELCOME_SCREEN ||
                              LightEdit.owns(null)
    val options = OpenProjectTask.build().withProjectToClose(null).withForceOpenInNewFrame(forceOpenInNewFrame).withRunConfigurators()
    RecentProjectsManagerBase.instanceEx.openProject(file, options)
  }

  override fun name(): String = displayName
  override fun children(): List<WelcomeScreenProjectItem> = emptyList()
}

class RecentProjectGroupItem(
  val group: ProjectGroup,
  val children: List<RecentProjectItem>
) : WelcomeScreenProjectItem {
  override fun name(): String = group.name
  override fun children(): List<WelcomeScreenProjectItem> = children
}

// The root node is required for the filtering tree
class Root : WelcomeScreenProjectItem {
  override fun name(): String = ""
  override fun children(): List<WelcomeScreenProjectItem> = RecentProjectListActionProvider.getInstance().collectProjects()
}