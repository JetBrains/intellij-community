// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.welcomeScreen.WelcomeUtils
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsKey
import com.intellij.platform.vcs.impl.shared.VcsMappingsHolder
import com.intellij.platform.vcs.impl.shared.VcsPresentation
import com.intellij.platform.vcs.impl.shared.VcsPresenter

internal class VcsGroupsActionGroup : DefaultActionGroup(), DumbAware {

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    val project = e.project
    if (project != null) {
      presentation.text = VcsMappingsHolder.getInstance(project).getSingleActiveVcs()
                            ?.let { findVcsPresentation(project, it) }?.shortNameWithMnemonic
                          ?: VcsBundle.message("vcs.generic.name.with.mnemonic")
    }
    presentation.isEnabledAndVisible = project != null && TrustedProjects.isProjectTrusted(project) && !WelcomeUtils.isWelcomeProject(project)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

/**
 * Returns the presentation of the VCS with the [vcsKey].
 */
internal fun findVcsPresentation(project: Project, vcsKey: VcsKey): VcsPresentation? {
  return VcsPresenter.findPresentation(vcsKey)
         ?: ProjectLevelVcsManager.getInstance(project).findVcsByName(vcsKey.name)
           ?.let { VcsPresentation(it.shortNameWithMnemonic, it.isWithCustomMenu) }
}
