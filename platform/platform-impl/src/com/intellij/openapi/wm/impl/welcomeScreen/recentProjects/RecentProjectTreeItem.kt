// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.recentProjects

import com.intellij.CommonBundle
import com.intellij.ide.*
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame
import com.intellij.openapi.wm.impl.welcomeScreen.ProjectDetector
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneableProject
import com.intellij.openapi.wm.impl.welcomeScreen.projectActions.RemoveSelectedProjectsAction
import com.intellij.util.BitUtil
import com.intellij.util.SystemProperties
import kotlinx.coroutines.launch
import org.jetbrains.annotations.SystemIndependent
import java.awt.event.ActionEvent
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.SwingUtilities

/**
 * Items of recent project tree:
 * - RootItem: collect all items of interface
 * - RecentProjectItem: an item project which can be interacted
 * - ProjectsGroupItem: group of RecentProjectItem
 *
 * @see com.intellij.openapi.wm.impl.welcomeScreen.ProjectsTabFactory.createWelcomeTab
 * @see com.intellij.ide.ManageRecentProjectsAction
 */
internal sealed interface RecentProjectTreeItem {
  fun displayName(): String

  fun children(): List<RecentProjectTreeItem>

  fun removeItem() {
    RemoveSelectedProjectsAction.removeItem(this)
  }
}

internal data class RecentProjectItem(
  val projectPath: @SystemIndependent String,
  @NlsSafe val projectName: String,
  @NlsSafe val displayName: String,
  val projectGroup: ProjectGroup?
) : RecentProjectTreeItem {
  override fun displayName(): String = displayName

  override fun children(): List<RecentProjectTreeItem> = emptyList()

  companion object {
    fun openProjectAndLogRecent(file: Path, options: OpenProjectTask, projectGroup: ProjectGroup?) {
      @Suppress("DEPRECATION")
      ApplicationManager.getApplication().coroutineScope.launch {
        RecentProjectsManagerBase.getInstanceEx().openProject(file, options)
        for (extension in ProjectDetector.EXTENSION_POINT_NAME.extensions) {
          extension.logRecentProjectOpened(projectGroup)
        }
      }
    }
  }

  fun openProject(event: AnActionEvent) {
    // Force move focus to IdeFrame
    IdeEventQueue.getInstance().popupManager.closeAllPopups()

    val file = Path.of(projectPath).normalize()
    if (!Files.exists(file)) {
      val exitCode = Messages.showYesNoDialog(
        IdeBundle.message("message.the.path.0.does.not.exist.maybe.on.remote", FileUtil.toSystemDependentName(projectPath)),
        IdeBundle.message("dialog.title.reopen.project"),
        IdeBundle.message("button.remove.from.list"),
        CommonBundle.getCancelButtonText(),
        Messages.getErrorIcon()
      )

      if (exitCode == Messages.YES) {
        RecentProjectsManager.getInstance().removePath(projectPath)
      }

      return
    }

    if (event.place == ActionPlaces.WELCOME_SCREEN) {
      val welcomeFrame = SwingUtilities.getAncestorOfClass(FlatWelcomeFrame::class.java,
                                                           event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)) as FlatWelcomeFrame?
      if (welcomeFrame != null) {
        Disposer.dispose(welcomeFrame)
      }
    }

    val forceOpenInNewFrame = BitUtil.isSet(event.modifiers, ActionEvent.CTRL_MASK) ||
                              BitUtil.isSet(event.modifiers, ActionEvent.SHIFT_MASK) ||
                              event.place == ActionPlaces.WELCOME_SCREEN ||
                              LightEdit.owns(null)
    openProjectAndLogRecent(file, OpenProjectTask {
      this.forceOpenInNewFrame = forceOpenInNewFrame
      runConfigurators = true
    }, projectGroup)
  }

  fun searchName(): String {
    val home = SystemProperties.getUserHome()
    var path = projectPath
    if (FileUtil.startsWith(path, home)) {
      path = path.substring(home.length)
    }
    val groupName = RecentProjectsManagerBase.getInstanceEx().findGroup(projectPath)?.name.orEmpty()
    return "$groupName $path $displayName"
  }
}

internal data class ProjectsGroupItem(
  val group: ProjectGroup,
  val children: List<RecentProjectItem>
) : RecentProjectTreeItem {
  override fun displayName(): String = group.name

  override fun children(): List<RecentProjectTreeItem> = children
}

internal data class CloneableProjectItem(
  val projectPath: @SystemIndependent String,
  @NlsSafe val projectName: String,
  @NlsSafe val displayName: String,
  val cloneableProject: CloneableProject
) : RecentProjectTreeItem {
  override fun displayName(): String = displayName

  override fun children(): List<RecentProjectTreeItem> = emptyList()
}

// The root node is required for the filtering tree
internal class RootItem(private val collectors: List<() -> List<RecentProjectTreeItem>>) : RecentProjectTreeItem {
  override fun displayName(): String = "" // Not visible in tree

  override fun children(): List<RecentProjectTreeItem> = collectors.flatMap { collector -> collector() }
}

internal object ProjectCollectors {
  @JvmField
  val recentProjectsCollector: () -> List<RecentProjectTreeItem> = {
    RecentProjectListActionProvider.getInstance().collectProjects()
  }

  @JvmField
  val cloneableProjectsCollector: () -> List<RecentProjectTreeItem> = {
    CloneableProjectsService.getInstance().collectCloneableProjects().toList()
  }

  @JvmField
  val all: List<() -> List<RecentProjectTreeItem>> = listOf(cloneableProjectsCollector, recentProjectsCollector)

  @JvmStatic
  fun createRecentProjectsWithoutCurrentCollector(currentProject: Project): () -> List<RecentProjectTreeItem> {
    return {
      RecentProjectListActionProvider.getInstance().collectProjectsWithoutCurrent(currentProject)
    }
  }
}
