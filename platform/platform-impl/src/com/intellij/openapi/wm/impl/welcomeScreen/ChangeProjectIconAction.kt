// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectIconHelper
import com.intellij.ide.RecentProjectIconHelper.Companion.createIcon
import com.intellij.ide.ReopenProjectAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.AnActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import org.jetbrains.annotations.SystemIndependent
import java.io.File
import java.nio.file.Paths
import com.intellij.ide.RecentProjectsManagerBase.Companion.instanceEx as ProjectIcon

/**
 * @author Konstantin Bulenkov
 */
class ChangeProjectIconAction : RecentProjectsWelcomeScreenActionBase() {
  override fun actionPerformed(e: AnActionEvent) {
    val projectPath = (getSelectedElements(e).first() as ReopenProjectAction).projectPath

    val ui = ProjectIconUI(projectPath)
    val panel = panel {
      row {
        label(IdeBundle.message("label.project.icon.for.default.theme"))
        component(ui.lightIconLabel)
        component(ui.setIconActionLinkActionLink)
        component(ui.clearIconActionLinkActionLink)
      }
      row {
        label(IdeBundle.message("label.project.icon.for.darcula.theme"))
        component(ui.darkIconLabel)
        component(ui.setIconDarkActionLinkActionLink)
        component(ui.clearIconDarkActionLinkActionLink)
      }
    }

    if (dialog("Change Project Icon", panel).showAndGet()) {
      val darkIconSvg = File("$projectPath/.idea/icon_dark.svg")
      val iconSvg: File = File("$projectPath/.idea/icon.svg")
      val darkIconPng = File("$projectPath/.idea/icon_dark.png")
      val iconPng: File = File("$projectPath/.idea/icon.png")

      if (ui.pathToDarkIcon != null) {
        FileUtil.copy(File(ui.pathToDarkIcon!!.path), darkIconSvg)
        VfsUtil.markDirtyAndRefresh(false, false, false, darkIconSvg)
        FileUtil.delete(darkIconPng)
        RecentProjectIconHelper.refreshProjectIcon(ui.pathToDarkIcon!!.path)
      }
      if (ui.pathToIcon != null) {
        FileUtil.copy(File(ui.pathToIcon!!.path), iconSvg)
        VfsUtil.markDirtyAndRefresh(false, false, false, iconSvg)
        FileUtil.delete(iconPng)
        RecentProjectIconHelper.refreshProjectIcon(ui.pathToIcon!!.path)
      }
    }
  }

  private class ChangeProjectIcon constructor(private val isDarcula: Boolean, private val ui: ProjectIconUI) : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
      val files = FileChooserFactory.getInstance()
        .createFileChooser(FileChooserDescriptor(true, false, false, false, false, false).withFileFilter { file: VirtualFile ->
          "svg".equals(file.extension, ignoreCase = true)
        }, null, null).choose(null)
      if (files.size == 1) {
        try {
          val newIcon = createIcon(Paths.get(files[0].path))
          if (isDarcula) {
            ui.darkIconLabel.icon = newIcon
            ui.pathToDarkIcon = files[0]
          }
          else {
            ui.lightIconLabel.icon = newIcon
            ui.pathToIcon = files[0]
          }
        }
        catch (ignore: Exception) {
        }
      }
    }
  }


  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = getSelectedElements(e).size == 1 && !hasGroupSelected(e)
  }

  class ProjectIconUI(projectPath: @SystemIndependent String) {
    val setIconActionLinkActionLink = AnActionLink(IdeBundle.message("link.change.project.icon"), ChangeProjectIcon(false, this))
    val setIconDarkActionLinkActionLink = AnActionLink(IdeBundle.message("link.change.project.icon"), ChangeProjectIcon(true, this))
    val clearIconActionLinkActionLink = AnActionLink(IdeBundle.message("link.reset.project.icon"), ChangeProjectIcon(false, this))
    val clearIconDarkActionLinkActionLink = AnActionLink(IdeBundle.message("link.reset.project.icon"), ChangeProjectIcon(true, this))
    val lightIconLabel = JBLabel(ProjectIcon.getProjectIcon(projectPath, false))
    val darkIconLabel = JBLabel(ProjectIcon.getProjectIcon(projectPath, true))
    var pathToDarkIcon: VirtualFile? = null
    var pathToIcon: VirtualFile? = null
  }

  companion object {
    private val LOG = Logger.getInstance(ChangeProjectIconAction::class.java)
  }
}