// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde.editorTab

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.LearnTabPanel
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.jbAcademy.JBAcademyWelcomeScreenBundle
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

internal class LearnIdeVirtualFile(val panel: LearnTabPanel, val project: Project) :
  LightVirtualFile("Learn", LearnIdeFileType(), "") {

  init {
    putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)
  }

  override fun shouldSkipEventSystem(): Boolean = true

  override fun getPath(): String = name

  private class LearnIdeFileType : FakeFileType() {
    override fun getName(): @NonNls String = "Learn"

    override fun getDescription(): @NlsContexts.Label String = JBAcademyWelcomeScreenBundle.message("welcome.editor.tab.file.description")

    override fun getIcon(): Icon = AllIcons.General.Learn

    override fun isMyFileType(file: VirtualFile): Boolean {
      return file is LearnIdeVirtualFile
    }
  }
}
