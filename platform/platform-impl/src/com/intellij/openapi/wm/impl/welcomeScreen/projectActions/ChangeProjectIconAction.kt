// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.projectActions

import com.intellij.icons.AllIcons
import com.intellij.ide.*
import com.intellij.ide.RecentProjectIconHelper.Companion.createIcon
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
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
import com.intellij.util.io.copy
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel

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
      changeProjectIcon(basePath = basePath, ui = ui, projectPath = projectPath, event = event)
    }
  }

  private fun getProjectPath(project: Project?, selectedItem: RecentProjectTreeItem?): Path? {
    if (selectedItem is RecentProjectItem) {
      try {
        return Path.of(selectedItem.projectPath)
      }
      catch (e: InvalidPathException) {
        logger<ChangeProjectIconAction>().warn(e)
        return null
      }
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

private fun changeProjectIcon(
  basePath: Path,
  ui: ProjectIconUI,
  projectPath: Path,
  event: AnActionEvent,
) {
  val pathToIcon = ui.pathToIcon?.toNioPath()
  if (pathToIcon != null) {
    val iconSvg = basePath.resolve("icon.svg")
    pathToIcon.copy(iconSvg)
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(iconSvg)?.let {
      VfsUtil.markDirtyAndRefresh(/* async = */ false, /* recursive = */ false, /* reloadChildren = */ false, /* ...files = */ it)
    }

    Files.deleteIfExists(basePath.resolve("icon.png"))
    RecentProjectIconHelper.refreshProjectIcon(projectPath)
    event.project?.let {
      val customizer = ProjectWindowCustomizerService.getInstance()
      customizer.dropProjectIconCache(it)
      customizer.setIconMainColorAsProjectColor(it)
    }
  }

  if (ui.iconRemoved) {
    Files.deleteIfExists(ui.pathToIcon())
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

internal class ChangeProjectIcon(private val ui: ProjectIconUI) : AnAction() {
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
      catch (_: Exception) {
      }
    }
  }
}

internal class ProjectIconUI(private val projectPath: Path) {
  val setIconActionLink = AnActionLink(IdeBundle.message("link.change.project.icon"), ChangeProjectIcon(this))
  val iconLabel = JBLabel((RecentProjectsManager.getInstance() as RecentProjectsManagerBase).getProjectIcon(projectPath, true))
  var pathToIcon: VirtualFile? = null
  val removeIcon = createToolbar()
  var iconRemoved = false

  private fun createToolbar(): ActionToolbar {
    val removeIconAction = object : DumbAwareAction(AllIcons.Actions.GC) {
      override fun actionPerformed(e: AnActionEvent) {
        iconRemoved = true
        iconLabel.icon = RecentProjectIconHelper.generateNewProjectIcon(projectPath, isProjectValid = true, projectName = null, colorIndex = null)
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

  fun pathToIcon(): Path {
    val file = projectPath
    return RecentProjectIconHelper.getDotIdeaPath(file)?.resolve("icon.svg") ?: file.resolve(".idea/icon.svg")
  }
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
