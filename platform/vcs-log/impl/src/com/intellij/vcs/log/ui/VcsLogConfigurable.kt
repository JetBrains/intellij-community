// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.application.options.editor.checkBox
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsListener
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.data.index.VcsLogPersistentIndex
import com.intellij.vcs.log.history.FileHistoryUiProperties
import com.intellij.vcs.log.history.isNewFileHistoryAvailable
import com.intellij.vcs.log.history.isNewHistoryEnabled
import com.intellij.vcs.log.impl.*
import com.intellij.vcs.log.ui.table.column.*
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

internal class VcsLogConfigurable(private val project: Project) : BoundConfigurable(VcsLogBundle.message("vcs.log.settings.group.title")),
                                                                  SearchableConfigurable {
  private val sharedSettings get() = project.service<VcsLogSharedSettings>()
  private val applicationSettings get() = ApplicationManager.getApplication().service<VcsLogApplicationSettings>()
  private val fileHistorySettings get() = project.service<FileHistoryUiProperties>()

  override fun createPanel(): DialogPanel {
    val vcsNamesToShow = getVcsNames()
    return panel {
      group(VcsLogBundle.message("group.Vcs.Log.PresentationSettings.text")) {
        booleanPropertyCheckboxRow("action.Vcs.Log.CompactReferencesView.description", CommonUiProperties.COMPACT_REFERENCES_VIEW,
                                   applicationSettings)
        booleanPropertyCheckboxRow("action.Vcs.Log.ShowTagNames.description", CommonUiProperties.SHOW_TAG_NAMES, applicationSettings)
        booleanPropertyCheckboxRow("action.Vcs.Log.PreferCommitDate.description", CommonUiProperties.PREFER_COMMIT_DATE, applicationSettings)
        booleanPropertyCheckboxRow("action.Vcs.Log.AlignLabels.description", CommonUiProperties.LABELS_LEFT_ALIGNED, applicationSettings)
        booleanPropertyCheckboxRow("action.Vcs.Log.ShowChangesFromParents.description",
                                   MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS, applicationSettings)
        diffPreviewLocationGroup(applicationSettings)
        columnVisibilityGroup(applicationSettings)
      }
      if (VcsLogPersistentIndex.getAvailableIndexers(project).isNotEmpty()) {
        group(VcsLogBundle.message("vcs.log.settings.group.indexing.title")) {
          row {
            checkBox(CheckboxDescriptor(VcsLogBundle.message("vcs.log.settings.enable.index.checkbox",
                                                             vcsNamesToShow.length, vcsNamesToShow),
                                        sharedSettings::isIndexSwitchedOn, sharedSettings::setIndexSwitchedOn))
              .comment(VcsLogBundle.message("vcs.log.settings.enable.index.checkbox.comment"))
              .enabledIf(VcsLogIndexAvailabilityPredicate(project, disposable!!))
          }
        }
      }
      if (isNewFileHistoryAvailable()) {
        group(VcsLogBundle.message("vcs.log.settings.group.file.history.title")) {
          booleanPropertyCheckboxRow("action.Vcs.Log.ShowDetailsAction.description", CommonUiProperties.SHOW_DETAILS, fileHistorySettings)
          booleanPropertyCheckboxRow("vcs.log.settings.show.file.names", CommonUiProperties.SHOW_ROOT_NAMES, fileHistorySettings)
          diffPreviewLocationGroup(fileHistorySettings)
          columnVisibilityGroup(fileHistorySettings)
        }
      }
    }
  }

  private fun Panel.columnVisibilityGroup(properties: VcsLogUiProperties) {
    if (properties.supportsColumnsToggling()) {
      group(VcsLogBundle.message("vcs.log.settings.visible.columns")) {
        val availableColumns = getDynamicColumns().filter {
          (it !is VcsLogCustomColumn) || it.isAvailable(project, VcsProjectLog.getLogProviders(project).keys)
        }
        for (column in availableColumns) {
          columnCheckboxRow(column, properties)
        }
      }
    }
  }

  private fun Panel.diffPreviewLocationGroup(properties: VcsLogUiProperties) {
    if (!properties.exists(CommonUiProperties.SHOW_DIFF_PREVIEW)) return
    lateinit var diffPreviewCheckbox: Cell<JBCheckBox>
    row {
      diffPreviewCheckbox = booleanPropertyCheckbox(VcsLogBundle.message("action.Vcs.Log.ShowDiffPreview.description"),
                                                    CommonUiProperties.SHOW_DIFF_PREVIEW, properties)
    }
    if (properties.exists(MainVcsLogUiProperties.DIFF_PREVIEW_VERTICAL_SPLIT)) {
      buttonsGroup(indent = true) {
        row(VcsLogBundle.message("vcs.log.settings.diff.preview.location")) {
          radioButton(VcsLogBundle.message("action.Vcs.Log.MoveDiffPreviewToBottom.text"), true)
          radioButton(VcsLogBundle.message("action.Vcs.Log.MoveDiffPreviewToRight.text"), false)
        }
      }.bind({ properties[MainVcsLogUiProperties.DIFF_PREVIEW_VERTICAL_SPLIT] },
             { properties[MainVcsLogUiProperties.DIFF_PREVIEW_VERTICAL_SPLIT] = it })
        .enabledIf(diffPreviewCheckbox.selected)
    }
  }

  private fun Panel.columnCheckboxRow(column: VcsLogColumn<*>, properties: VcsLogUiProperties) {
    row {
      checkBox(CheckboxDescriptor(column.localizedName,
                                  { column.isVisible(properties) },
                                  {
                                    if (it) properties.addColumn(column)
                                    else properties.removeColumn(column)
                                  }))
    }
  }

  private fun Panel.booleanPropertyCheckboxRow(textKey: @PropertyKey(resourceBundle = VcsLogBundle.BUNDLE) String,
                                               property: VcsLogUiProperties.VcsLogUiProperty<Boolean>,
                                               properties: VcsLogUiProperties) {
    if (!properties.exists(property)) return
    row {
      booleanPropertyCheckbox(VcsLogBundle.message(textKey), property, properties)
    }
  }

  private fun Row.booleanPropertyCheckbox(text: @Nls String,
                                          property: VcsLogUiProperties.VcsLogUiProperty<Boolean>,
                                          properties: VcsLogUiProperties): Cell<JBCheckBox> {
    return checkBox(CheckboxDescriptor(text, { properties[property] }, { properties[property] = it }))
  }

  private fun getVcsNames(): String {
    val allVcsKeys = ProjectLevelVcsManager.getInstance(project).allActiveVcss.mapTo(mutableSetOf()) { it.keyInstanceMethod }
    val indexedVcsKeys = VcsLogPersistentIndex.getAvailableIndexers(project).mapTo(mutableSetOf()) { it.supportedVcs }
    if (indexedVcsKeys != allVcsKeys) {
      return indexedVcsKeys.mapNotNull { VcsUtil.findVcsByKey(project, it)?.displayName }.joinToString()
    }
    return ""
  }

  private fun isNewFileHistoryAvailable(): Boolean {
    if (!isNewHistoryEnabled()) return false
    return VcsProjectLog.getLogProviders(project).values.any { isNewFileHistoryAvailable(project, it) }
  }

  override fun getId(): String = "vcs.log"
}

private class VcsLogIndexAvailabilityPredicate(private val project: Project, private val disposable: Disposable) : ComponentPredicate() {
  private val isVcsLogIndexAvailable
    get() = VcsLogPersistentIndex.getAvailableIndexers(project).isNotEmpty()

  override fun addListener(listener: (Boolean) -> Unit) {
    project.messageBus.connect(disposable).subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED,
                                                     VcsListener { listener(isVcsLogIndexAvailable) })
  }

  override fun invoke() = isVcsLogIndexAvailable
}

internal class VcsLogConfigurableProvider(private val project: Project) : ConfigurableProvider() {
  override fun createConfigurable(): Configurable? {
    if (VcsProjectLog.getLogProviders(project).isEmpty()) return null
    return VcsLogConfigurable(project)
  }

  override fun canCreateConfigurable() = !VcsProjectLog.getLogProviders(project).isEmpty()
}
