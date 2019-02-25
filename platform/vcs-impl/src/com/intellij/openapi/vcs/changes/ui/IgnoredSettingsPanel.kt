// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.ui.layout.*
import org.jetbrains.annotations.Nls
import javax.swing.JCheckBox

class IgnoredSettingsPanel(private val project: Project) : SearchableConfigurable, Configurable.NoScroll {
  private lateinit var manageIgnoreFiles: JCheckBox

  override fun reset() {
    val applicationSettings = VcsApplicationSettings.getInstance()
    manageIgnoreFiles.isSelected = applicationSettings.MANAGE_IGNORE_FILES
  }

  override fun apply() {
    val applicationSettings = VcsApplicationSettings.getInstance()
    applicationSettings.MANAGE_IGNORE_FILES = manageIgnoreFiles.isSelected
  }

  override fun isModified(): Boolean {
    val applicationSettings = VcsApplicationSettings.getInstance()
    return applicationSettings.MANAGE_IGNORE_FILES != manageIgnoreFiles.isSelected
  }

  override fun createComponent() = panel {
    row {
      createManageIgnoreFiles()
    }
  }

  private fun Row.createManageIgnoreFiles(): JCheckBox {
    manageIgnoreFiles = checkBox("Manage ignore files").apply { setMnemonic('m') }
    return manageIgnoreFiles
  }

  @Nls
  override fun getDisplayName() = "Ignored Files"

  override fun getHelpTopic() = "project.propVCSSupport.Ignored.Files"
  override fun getId() = helpTopic
}
