// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectIconHelper
import com.intellij.ide.RecentProjectIconHelper.Companion.createIcon
import com.intellij.ide.ReopenProjectAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.AnActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import com.intellij.util.ui.GraphicsUtil
import org.jetbrains.annotations.SystemIndependent
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.io.path.Path
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
        cell {
          component(IconPreviewPanel(ui.iconLabel)).withLargeLeftGap()
        }
        cell {
          component(panel {
            fullRow {
              component(ui.setIconActionLink)
                .comment(IdeBundle.message("link.change.project.icon.description"))
              component(ui.removeIcon.component)
            }
          }).withLargeLeftGap()
        }
      }
    }

    if (dialog(IdeBundle.message("dialog.title.change.project.icon"), panel).showAndGet()) {
      val basePath = RecentProjectIconHelper.getDotIdeaPath(projectPath)
      val iconSvg = basePath.resolve("icon.svg").toFile()
      val iconPng = basePath.resolve("icon.png").toFile()

      if (ui.pathToIcon != null) {
        FileUtil.copy(File(ui.pathToIcon!!.path), iconSvg)
        VfsUtil.markDirtyAndRefresh(false, false, false, iconSvg)
        FileUtil.delete(iconPng)
        RecentProjectIconHelper.refreshProjectIcon(projectPath)
      }
      if (ui.iconRemoved) {
        FileUtil.delete(ui.pathToIcon())
        RecentProjectIconHelper.refreshProjectIcon(projectPath)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = getSelectedElements(e).size == 1 && !hasGroupSelected(e)
  }
}

  private class ChangeProjectIcon constructor(private val ui: ProjectIconUI) : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
      val files = FileChooserFactory.getInstance()
        .createFileChooser(FileChooserDescriptor(true, false, false, false, false, false).withFileFilter { file: VirtualFile ->
          "svg".equals(file.extension, ignoreCase = true)
        }, null, null).choose(null)
      if (files.size == 1) {
        try {
          val newIcon = createIcon(Paths.get(files[0].path))
          ui.iconLabel.icon = newIcon
          ui.pathToIcon = files[0]
          ui.iconRemoved = false
        }
        catch (ignore: Exception) {
        }
      }
    }
  }

class ProjectIconUI(val projectPath: @SystemIndependent String) {
  val setIconActionLink = AnActionLink(IdeBundle.message("link.change.project.icon"), ChangeProjectIcon(this))
  val iconLabel = JBLabel(ProjectIcon.getProjectIcon(projectPath, false))
  var pathToIcon: VirtualFile? = null
  val removeIcon = createToolbar()
  var iconRemoved = false

  private fun createToolbar(): ActionToolbar {
    val removeIconAction = object : DumbAwareAction(AllIcons.Actions.GC) {
      override fun actionPerformed(e: AnActionEvent) {
        iconRemoved = true
        iconLabel.icon = RecentProjectIconHelper.generateProjectIcon(projectPath)
        pathToIcon = null
      }

      override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = pathToIcon != null || (Files.exists(pathToIcon()) && !iconRemoved)
      }
    }
    return ActionManager.getInstance().createActionToolbar("ProjectIconDialog", DefaultActionGroup(removeIconAction),true)
      .apply { setTargetComponent(iconLabel) }
  }

  fun pathToIcon() = Path("${projectPath}/.idea/icon.svg")
}

private class IconPreviewPanel(component: JComponent) : JPanel(BorderLayout()) {
  val radius = 4
  val size = 60

  init {
    isOpaque = false
    background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
    preferredSize = Dimension(size, size)
    minimumSize = Dimension(size, size)
    add(component)
  }

  override fun paintComponent(g: Graphics) {
    g.color = background
    val config = GraphicsUtil.setupRoundedBorderAntialiasing(g)
    g.fillRoundRect(0, 0, width, height, 2 * radius, 2 * radius)
    config.restore()
  }
}