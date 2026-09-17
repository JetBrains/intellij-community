// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.welcomeScreen

import com.intellij.codeWithMe.ClientId
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ReopenProjectAction
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.openapi.wm.ex.getWelcomeScreenProjectProvider
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import kotlinx.coroutines.launch
import java.nio.file.Path
import javax.swing.Icon

internal abstract class WelcomeReopenProjectActionBase(path: Path, name: String) :
  ReopenProjectAction(projectPath = path.toAbsolutePath().toString(), projectName = name, displayName = name) {

  override val projectIcon: Icon get() = AllIcons.Nodes.HomeFolder

  override val projectNameToDisplay = name

  override val projectPathToDisplay = null

  override fun actionPerformed(e: AnActionEvent) {
    val provider = getWelcomeScreenProjectProvider() ?: return

    val project = e.project
    if (project != null && WelcomeUtils.isWelcomeProject(project)) {
      return
    }

    IdeEventQueue.getInstance().popupManager.closeAllPopups()
    service<CoreUiCoroutineScopeHolder>().coroutineScope.launch(ClientId.coroutineContext()) {
      WelcomeScreenProjectProvider.createOrOpenWelcomeScreenProject(provider, null, true)
    }
  }
}

internal class WelcomeReopenProjectAction :
  WelcomeReopenProjectActionBase(WelcomeScreenProjectProvider.getWelcomeScreenProjectPath() ?: Path.of("/"),
                                 getWelcomeScreenProjectProvider()?.getWelcomeScreenProjectName() ?: " ") {

  override fun update(e: AnActionEvent) {
    super.update(e)
    val project = e.project
    e.presentation.text = ActionsBundle.message("action.WelcomeReopenProjectAction.text")
    e.presentation.icon = AllIcons.Nodes.HomeFolder
    e.presentation.isEnabledAndVisible =
      projectDisplayName.isNotBlank() && project != null && !WelcomeUtils.isWelcomeProject(project) && WelcomeAccessPoint.isAvailable()
  }
}