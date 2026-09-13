// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.welcomeScreen

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Constraints
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getOpenedProjects
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
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

  fun getGotoWelcomeProjectAction(): AnAction? {
    if (!WelcomeAccessPoint.isAvailable()) {
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
    return project != null && isWelcomeProject(project) && isNoUserDataOpened(FileEditorManager.getInstance(project))
  }

  suspend fun noCheckOpenConfirmation(project: Project): Boolean {
    return isWelcomeProject(project) && isNoUserDataOpened(project.serviceAsync<FileEditorManager>())
  }

  private fun isNoUserDataOpened(manager: FileEditorManager): Boolean {
    return manager.getAllEditors().isEmpty()
  }

  fun getWelcomeProjectIcon(project: Project): Icon? {
    if (WelcomeAccessPoint.isAvailable() && isWelcomeProject(project)) {
      return AllIcons.Nodes.HomeFolder
    }
    return null
  }

  fun isWelcomeProject(project: Project): Boolean {
    @Suppress("DEPRECATION")
    return ProjectFrameCapabilitiesService.getInstanceSync().has(project, ProjectFrameCapability.WELCOME_EXPERIENCE)
  }
}