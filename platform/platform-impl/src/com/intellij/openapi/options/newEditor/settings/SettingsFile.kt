// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor.settings

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager.OptionallyIncluded
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.annotations.NonNls
import javax.swing.Icon
import javax.swing.JComponent

class SettingsFile(val component: JComponent?) :
  LightVirtualFile(CommonBundle.settingsTitle(), SettingFileType(), ""), OptionallyIncluded {

  init {
    putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)
  }

  override fun isIncludedInEditorHistory(project: Project): Boolean  = false


  private class SettingFileType: FakeFileType() {
    override fun getName(): @NonNls String  = CommonBundle.settingsTitle()

    override fun getDescription(): @NlsContexts.Label String  = CommonBundle.settingsTitle()

    override fun getIcon(): Icon?  = AllIcons.General.Settings

    override fun isMyFileType(file: VirtualFile): Boolean {
      return file is SettingsFile
    }
  }
}
