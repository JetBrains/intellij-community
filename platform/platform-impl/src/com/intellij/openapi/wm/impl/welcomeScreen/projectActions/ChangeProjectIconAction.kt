// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.projectActions

import com.intellij.icons.AllIcons
import com.intellij.ide.*
import com.intellij.ide.RecentProjectIconHelper.Companion.createIcon
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.ProviderRecentProjectItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectTreeItem
import com.intellij.ui.IconDeferrer
import com.intellij.ui.components.AnActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.SystemIndependent
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.io.path.Path

/**
 * @author Konstantin Bulenkov
 */
internal class ChangeProjectIconAction : RecentProjectsWelcomeScreenActionBase() {
  init {
    isEnabledInModalContext = true  // To allow the action to be run in the Manage Recent Projects modal dialog, see IDEA-302750
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project
    val selectedItem = getSelectedItem(event)
    val projectPath = getProjectPath(project, selectedItem) ?: return
    val basePath = RecentProjectIconHelper.getDotIdeaPath(projectPath) ?: return

    val ui = ProjectIconUI(projectPath)

    val panel = panel {
      row {
        cell(IconPreviewPanel(ui.iconLabel))
        panel {
          row {
            cell(ui.setIconActionLink)
              .gap(RightGap.SMALL)
            cell(ui.removeIcon.component)
          }
          row {
            text(IdeBundle.message("link.change.project.icon.description")).applyToComponent {
              foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
            }
          }
        }
      }
    }

    if (dialog(IdeBundle.message("dialog.title.change.project.icon"), panel).showAndGet()) {
      val iconSvg = basePath.resolve("icon.svg").toFile()
      val iconPng = basePath.resolve("icon.png").toFile()

      if (ui.pathToIcon != null) {
        FileUtil.copy(File(ui.pathToIcon!!.path), iconSvg)
        VfsUtil.markDirtyAndRefresh(false, false, false, iconSvg)
        FileUtil.delete(iconPng)
        RecentProjectIconHelper.refreshProjectIcon(projectPath)
        event.project?.let {
          val customizer = ProjectWindowCustomizerService.getInstance()
          customizer.dropProjectIconCache(it)
          customizer.setIconMainColorAsProjectColor(it)
        }
      }
      if (ui.iconRemoved) {
        FileUtil.delete(ui.pathToIcon())
        RecentProjectIconHelper.refreshProjectIcon(projectPath)
        event.project?.let {
          ProjectWindowCustomizerService.getInstance().dropProjectIconCache(it)
        }
      }
      // Actually, we can try to drop the needed icon,
      // but it is a very rare action and this whole cache drop will not have any performance impact.
      // Moreover, VCS changes will drop the cache also.
      IconDeferrer.getInstance().clearCache()
    }
  }

  private fun getProjectPath(project: Project?, selectedItem: RecentProjectTreeItem?): String? {
    if (selectedItem is RecentProjectItem) {
      return selectedItem.projectPath
    }
    if (project != null && selectedItem == null) {
      return ProjectWindowCustomizerService.projectPath(project)
    }
    return null
  }

  override fun update(event: AnActionEvent) {
    val project = event.project
    val selectedItem = getSelectedItem(event)
    event.presentation.isEnabled = getProjectPath(project, selectedItem) != null
    event.presentation.isVisible = selectedItem !is ProviderRecentProjectItem
  }
}

private class ChangeProjectIcon(private val ui: ProjectIconUI) : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val files = FileChooserFactory.getInstance()
      .createFileChooser(FileChooserDescriptorFactory.createSingleFileDescriptor("svg"), null, null)
      .choose(null)
    if (files.size == 1) {
      try {
        val newIcon = createIcon(Path.of(files[0].path))
        ui.iconLabel.icon = newIcon
        ui.pathToIcon = files[0]
        ui.iconRemoved = false
      }
      catch (ignore: Exception) {
      }
    }
  }
}

private class ProjectIconUI(private val projectPath: @SystemIndependent String) {
  val setIconActionLink = AnActionLink(IdeBundle.message("link.change.project.icon"), ChangeProjectIcon(this))
  val iconLabel = JBLabel((RecentProjectsManager.getInstance() as RecentProjectsManagerBase).getProjectIcon(projectPath, true))
  var pathToIcon: VirtualFile? = null
  val removeIcon = createToolbar()
  var iconRemoved = false

  private fun createToolbar(): ActionToolbar {
    val removeIconAction = object : DumbAwareAction(AllIcons.Actions.GC) {
      override fun actionPerformed(e: AnActionEvent) {
        iconRemoved = true
        iconLabel.icon = RecentProjectIconHelper.generateProjectIcon(projectPath, isProjectValid = true)
        pathToIcon = null
      }

      override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = pathToIcon != null || (Files.exists(pathToIcon()) && !iconRemoved)
      }

      override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
      }
    }
    return ActionManager.getInstance().createActionToolbar("ProjectIconDialog", DefaultActionGroup(removeIconAction), true)
      .apply { targetComponent = iconLabel }
  }

  fun pathToIcon() = RecentProjectIconHelper.getDotIdeaPath(projectPath)?.resolve("icon.svg") ?: Path("${projectPath}/.idea/icon.svg")
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
