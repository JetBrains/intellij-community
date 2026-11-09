// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch

import com.intellij.ide.actions.WelcomeFilesRootType
import com.intellij.ide.welcomeScreen.WelcomeUtils
import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile

/** "Change Language" for the files created from the welcome screen; they live under [WelcomeFilesRootType], not under Scratches. */
internal class WelcomeChangeLanguageAction : ChangeLanguageAction() {
  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project != null && !WelcomeUtils.isWelcomeProject(project)) {
      e.presentation.isEnabledAndVisible = false
    }
    else {
      super.update(e)
    }
  }

  override fun fileFilter(project: Project): Condition<VirtualFile> {
    return Condition { file -> !file.isDirectory() && WelcomeFilesRootType.Util.instance.containsFile(file) }
  }

  /** The language comes from the file name, because [WelcomeFilesRootType] does not substitute the language. */
  override fun fileLanguage(project: Project, file: VirtualFile): Language? = LanguageUtil.getLanguageForPsi(project, file)
}
