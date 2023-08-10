// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.github

import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class GithubShareAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT)
    e.presentation.isEnabledAndVisible = project != null && !project.isDefault && project.isTrusted()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT)
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

    if (project == null || project.isDisposed) {
      return
    }

    GHShareProjectUtil.shareProjectOnGithub(project, file)
  }


  companion object {
    @Deprecated("Extracted to utility",
                ReplaceWith("GHShareProjectUtil.shareProjectOnGithub",
                            "org.jetbrains.plugins.github.GHShareProjectUtil"))
    @JvmStatic
    fun shareProjectOnGithub(project: Project, file: VirtualFile?): Unit = GHShareProjectUtil.shareProjectOnGithub(project, file)
  }
}