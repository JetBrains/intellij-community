// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs.changes.ui

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.ignore.IgnoreConfigurationProperty.ASKED_MANAGE_IGNORE_FILES_PROPERTY
import com.intellij.openapi.vcs.changes.ignore.IgnoreConfigurationProperty.MANAGE_IGNORE_FILES_PROPERTY
import com.intellij.openapi.vcs.changes.ui.IgnoredSettingsPanel.ManageIgnoredOption.*
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toNullableProperty

internal class IgnoredSettingsPanel(private val project: Project) :
  BoundSearchableConfigurable(message("ignored.file.tab.title"), "project.propVCSSupport.Ignored.Files") {

  private var selectedManageIgnoreOption = getIgnoredOption()
  private val settings = VcsApplicationSettings.getInstance()

  override fun apply() {
    val modified = isModified
    super.apply()

    if (modified) {
      updateIgnoredOption(selectedManageIgnoreOption)
    }
  }

  override fun createPanel() =
    panel {
      group(message("ignored.file.general.settings.title")) {
        row(message("ignored.file.manage.policy.label")) {
          comboBox(listOf(AlwaysAsk,
                           AllProjectsManage,
                           CurrentProjectManage,
                           DoNotManageForCurrentProject,
                           DoNotManageForAllProject))
            .bindItem(::selectedManageIgnoreOption.toNullableProperty())
        }
      }
      group(message("ignored.file.excluded.settings.title")) {
        row {
          checkBox(message("ignored.file.excluded.to.ignored.label"))
            .bindSelected(settings::MARK_EXCLUDED_AS_IGNORED)
        }
      }
    }

  private fun updateIgnoredOption(option: ManageIgnoredOption) {
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
      AlwaysAsk -> {
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
      else -> AlwaysAsk
    }
  }

  enum class ManageIgnoredOption(val displayName: String) {
    AlwaysAsk(message("ignored.file.manage.always.ask.option")),
    AllProjectsManage(message("ignored.file.manage.all.projects.option")),
    CurrentProjectManage(message("ignored.file.manage.this.project.option")),
    DoNotManageForCurrentProject(message("ignored.file.not.manage.this.project.option")),
    DoNotManageForAllProject(message("ignored.file.not.manage.option"));

    override fun toString() = displayName
  }
}
