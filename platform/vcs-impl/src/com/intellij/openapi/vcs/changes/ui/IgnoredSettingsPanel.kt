// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.changes.ignore.IgnoreConfigurationProperty.ASKED_MANAGE_IGNORE_FILES_PROPERTY
import com.intellij.openapi.vcs.changes.ignore.IgnoreConfigurationProperty.MANAGE_IGNORE_FILES_PROPERTY
import com.intellij.openapi.vcs.changes.ui.IgnoredSettingsPanel.ManageIgnoredOption.*
import com.intellij.ui.components.Label
import com.intellij.ui.layout.*
import javax.swing.DefaultComboBoxModel

internal class IgnoredSettingsPanel(private val project: Project) : BoundConfigurable("Ignored Files",
                                                                                      "project.propVCSSupport.Ignored.Files"), SearchableConfigurable {
  var selectedManageIgnoreOption = getIgnoredOption()

  override fun apply() {
    val modified = isModified
    super.apply()

    if (modified) {
      updateIgnoredOption(selectedManageIgnoreOption)
    }
  }

  override fun createPanel() =
    panel {
      titledRow("VCS ignore settings") {
        row {
          cell {
            Label("Ignore manage policy")()
            comboBox(
              DefaultComboBoxModel(arrayOf(None,
                                           AllProjectsManage,
                                           CurrentProjectManage,
                                           DoNotManageForCurrentProject,
                                           DoNotManageForAllProject)),
              ::selectedManageIgnoreOption
            )
          }
        }
      }
    }

  private fun updateIgnoredOption(option: ManageIgnoredOption?) {
    val applicationSettings = VcsApplicationSettings.getInstance()
    val propertiesComponent = PropertiesComponent.getInstance(project)
    when (option) {
      DoNotManageForAllProject -> {
        propertiesComponent.setValue(MANAGE_IGNORE_FILES_PROPERTY, false)
        propertiesComponent.setValue(ASKED_MANAGE_IGNORE_FILES_PROPERTY, true)
        applicationSettings.MANAGE_IGNORE_FILES = false
        applicationSettings.DISABLE_MANAGE_IGNORE_FILES = true
      }
      AllProjectsManage -> {
        propertiesComponent.setValue(MANAGE_IGNORE_FILES_PROPERTY, true)
        propertiesComponent.setValue(ASKED_MANAGE_IGNORE_FILES_PROPERTY, true)
        applicationSettings.MANAGE_IGNORE_FILES = true
        applicationSettings.DISABLE_MANAGE_IGNORE_FILES = false
      }
      CurrentProjectManage -> {
        propertiesComponent.setValue(MANAGE_IGNORE_FILES_PROPERTY, true)
        propertiesComponent.setValue(ASKED_MANAGE_IGNORE_FILES_PROPERTY, true)
        applicationSettings.MANAGE_IGNORE_FILES = false
        applicationSettings.DISABLE_MANAGE_IGNORE_FILES = false
      }
      DoNotManageForCurrentProject -> {
        propertiesComponent.setValue(MANAGE_IGNORE_FILES_PROPERTY, false)
        propertiesComponent.setValue(ASKED_MANAGE_IGNORE_FILES_PROPERTY, true)
        applicationSettings.MANAGE_IGNORE_FILES = false
        applicationSettings.DISABLE_MANAGE_IGNORE_FILES = false
      }
      None -> {
        propertiesComponent.setValue(MANAGE_IGNORE_FILES_PROPERTY, false)
        propertiesComponent.setValue(ASKED_MANAGE_IGNORE_FILES_PROPERTY, false)
        applicationSettings.MANAGE_IGNORE_FILES = false
        applicationSettings.DISABLE_MANAGE_IGNORE_FILES = false
      }
    }
  }

  private fun getIgnoredOption(): ManageIgnoredOption {
    val applicationSettings = VcsApplicationSettings.getInstance()
    val propertiesComponent = PropertiesComponent.getInstance(project)
    return when {
      applicationSettings.DISABLE_MANAGE_IGNORE_FILES -> DoNotManageForAllProject
      applicationSettings.MANAGE_IGNORE_FILES -> AllProjectsManage
      propertiesComponent.getBoolean(MANAGE_IGNORE_FILES_PROPERTY, false) -> CurrentProjectManage
      propertiesComponent.getBoolean(ASKED_MANAGE_IGNORE_FILES_PROPERTY, false) -> DoNotManageForCurrentProject
      else -> None
    }
  }

  enum class ManageIgnoredOption(val displayName: String) {
    None(""),
    AllProjectsManage("Manage for all projects"),
    CurrentProjectManage("Manage for this project only"),
    DoNotManageForCurrentProject("Don’t manage for this project only, but show notification in other projects"),
    DoNotManageForAllProject("Turn off app-wide");

    override fun toString() = displayName
  }

  override fun getId() = helpTopic!!
}
