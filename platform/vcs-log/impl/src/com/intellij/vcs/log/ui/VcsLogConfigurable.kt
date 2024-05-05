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
import com.intellij.vcs.log.impl.*
import com.intellij.vcs.log.ui.table.column.*
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private class VcsLogConfigurable(private val project: Project) : BoundConfigurable(VcsLogBundle.message("vcs.log.settings.group.title")),
                                                                 SearchableConfigurable {
  private val sharedSettings get() = project.service<VcsLogSharedSettings>()
  private val applicationSettings get() = ApplicationManager.getApplication().service<VcsLogApplicationSettings>()

  override fun createPanel(): DialogPanel {
    val vcsNamesToShow = getVcsNames()
    return panel {
      group(VcsLogBundle.message("group.Vcs.Log.PresentationSettings.text")) {
        booleanPropertyCheckboxRow("vcs.log.action.description.show.compact.references.view", CommonUiProperties.COMPACT_REFERENCES_VIEW)
        booleanPropertyCheckboxRow("vcs.log.action.description.show.tag.names", CommonUiProperties.SHOW_TAG_NAMES)
        booleanPropertyCheckboxRow("prefer.commit.timestamp.action.description", CommonUiProperties.PREFER_COMMIT_DATE)
        booleanPropertyCheckboxRow("vcs.log.action.description.align.labels", CommonUiProperties.LABELS_LEFT_ALIGNED)
        booleanPropertyCheckboxRow("vcs.log.action.description.show.all.changes.from.parent",
                                   MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS)
        diffPreviewLocationGroup()
        if (applicationSettings.supportsColumnsToggling()) {
          group(VcsLogBundle.message("vcs.log.settings.visible.columns")) {
            val availableColumns = getDynamicColumns().filter {
              (it !is VcsLogCustomColumn) || it.isAvailable(project, VcsProjectLog.getLogProviders(project).keys)
            }
            for (column in availableColumns) {
              columnCheckboxRow(column)
            }
          }
        }
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
    }
  }

  private fun Panel.diffPreviewLocationGroup() {
    if (!applicationSettings.exists(CommonUiProperties.SHOW_DIFF_PREVIEW)) return
    lateinit var diffPreviewCheckbox: Cell<JBCheckBox>
    row {
      diffPreviewCheckbox = booleanPropertyCheckbox(VcsLogBundle.message("vcs.log.action.description.show.diff.preview"),
                                                    CommonUiProperties.SHOW_DIFF_PREVIEW)
    }
    if (applicationSettings.exists(MainVcsLogUiProperties.DIFF_PREVIEW_VERTICAL_SPLIT)) {
      buttonsGroup(indent = true) {
        row(VcsLogBundle.message("vcs.log.settings.diff.preview.location")) {
          radioButton(VcsLogBundle.message("action.Vcs.Log.MoveDiffPreviewToBottom.text"), true)
          radioButton(VcsLogBundle.message("action.Vcs.Log.MoveDiffPreviewToRight.text"), false)
        }
      }.bind({ applicationSettings[MainVcsLogUiProperties.DIFF_PREVIEW_VERTICAL_SPLIT] },
             { applicationSettings[MainVcsLogUiProperties.DIFF_PREVIEW_VERTICAL_SPLIT] = it })
        .enabledIf(diffPreviewCheckbox.selected)
    }
  }

  private fun Panel.columnCheckboxRow(column: VcsLogColumn<*>) {
    row {
      checkBox(CheckboxDescriptor(column.localizedName,
                                  { column.isVisible(applicationSettings) },
                                  {
                                    if (it) applicationSettings.addColumn(column)
                                    else applicationSettings.removeColumn(column)
                                  }))
    }
  }

  private fun Panel.booleanPropertyCheckboxRow(textKey: @PropertyKey(resourceBundle = VcsLogBundle.BUNDLE) String,
                                               property: VcsLogUiProperties.VcsLogUiProperty<Boolean>) {
    if (!applicationSettings.exists(property)) return
    row {
      booleanPropertyCheckbox(VcsLogBundle.message(textKey), property)
    }
  }

  private fun Row.booleanPropertyCheckbox(text: @Nls String, property: VcsLogUiProperties.VcsLogUiProperty<Boolean>): Cell<JBCheckBox> {
    return checkBox(CheckboxDescriptor(text, { applicationSettings[property] }, { applicationSettings[property] = it }))
  }

  private fun getVcsNames(): String {
    val allVcsKeys = ProjectLevelVcsManager.getInstance(project).allActiveVcss.mapTo(mutableSetOf()) { it.keyInstanceMethod }
    val indexedVcsKeys = VcsLogPersistentIndex.getAvailableIndexers(project).mapTo(mutableSetOf()) { it.supportedVcs }
    if (indexedVcsKeys != allVcsKeys) {
      return indexedVcsKeys.mapNotNull { VcsUtil.findVcsByKey(project, it)?.displayName }.joinToString()
    }
    return ""
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

@ApiStatus.Internal
class VcsLogConfigurableProvider(private val project: Project) : ConfigurableProvider() {
  override fun createConfigurable(): Configurable? {
    if (VcsProjectLog.getLogProviders(project).isEmpty()) return null
    return VcsLogConfigurable(project)
  }

  override fun canCreateConfigurable() = !VcsProjectLog.getLogProviders(project).isEmpty()
}