// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.welcomeScreen

import com.intellij.icons.AllIcons
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Constraints
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getOpenedProjects
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.openapi.wm.ex.WelcomeScreenTabService
import com.intellij.openapi.wm.ex.getWelcomeScreenProjectProvider
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
object WelcomeUtils {
  fun addWelcomeProjectNewAction(initEvent: AnActionEvent, project: Project?, group: DefaultActionGroup) {
    if (project == null || !isWelcomeProject(project)) {
      return
    }

    group.getChildren(initEvent).forEach {
      if (it.templateText == "Project…") { // TODO: move to WelcomeScreenProjectProvider but needs new module depends
        group.remove(it)
      }
    }

    // TODO: better way to handle empty action groups
    // TODO: inline one action in group

    if (getWelcomeScreenProjectProvider()?.addWelcomeProjectNewAction() != true) {
      return
    }

    group.add(ActionManager.getInstance().getAction("NonModalWelcomeScreen.LeftTabActions.New"), Constraints.FIRST)
  }

  fun getGotoWelcomeProjectAction(project: Project?): AnAction? {
    if (project != null && isWelcomeProject(project)) {
      return null
    }

    val provider = getWelcomeScreenProjectProvider() ?: return null
    val path = WelcomeScreenProjectProvider.getWelcomeScreenProjectPath() ?: return null

    val name = provider.getWelcomeScreenProjectName()

    return object : WelcomeReopenProjectActionBase(path, name) {}
  }

  @JvmStatic
  fun isSingleWelcomeProjectWithoutConfirmation(): Boolean {
    val project = getOpenedProjects().singleOrNull()
    if (project == null) {
      return false
    }
    if (isWelcomeProject(project)) {
      return isNoUserDataOpened(FileEditorManager.getInstance(project))
    }
    return false
  }

  suspend fun noCheckOpenConfirmation(project: Project): Boolean {
    return isWelcomeProject(project) && isNoUserDataOpened(project.serviceAsync<FileEditorManager>())
  }

  @JvmStatic
  fun addGotoHomeToConfirmationDialog(): Boolean {
    if (SystemInfo.isMac) {
      return false
    }
    val project = ProjectUtil.getActiveProject() ?: return false
    return !isWelcomeProject(project)
  }

  private fun isNoUserDataOpened(manager: FileEditorManager): Boolean {
    val editors = manager.getAllEditors()
    if (editors.size == 0) {
      return true
    }
    if (editors.size == 1) {
      val marker = editors[0].getFile()?.getUserData(WelcomeScreenTabService.WELCOME_TAB_FILE_MARKER)
      return marker != null && marker
    }
    return false
  }

  fun getWelcomeProjectIcon(project: Project): Icon? {
    if (isWelcomeProject(project)) {
      return AllIcons.Nodes.HomeFolder
    }
    return null
  }

  fun isWelcomeProject(project: Project): Boolean {
    @Suppress("DEPRECATION")
    return ProjectFrameCapabilitiesService.getInstanceSync().has(project, ProjectFrameCapability.WELCOME_EXPERIENCE)
  }
}