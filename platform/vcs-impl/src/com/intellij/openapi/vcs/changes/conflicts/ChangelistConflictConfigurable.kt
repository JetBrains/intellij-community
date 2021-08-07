// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.conflicts

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsShowConfirmationOption
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.onChangeListAvailabilityChanged
import com.intellij.openapi.vcs.impl.LineStatusTrackerSettingListener
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.components.JBList
import com.intellij.ui.layout.*
import javax.swing.DefaultListModel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class ChangelistConflictConfigurable(val project: Project)
  : BoundSearchableConfigurable(message("configurable.ChangelistConflictConfigurable.display.name"),
                                "project.propVCSSupport.ChangelistConflict"), NoScroll {
  override fun createPanel(): DialogPanel {
    val appSettings = VcsApplicationSettings.getInstance()
    val conflictTracker = ChangelistConflictTracker.getInstance(project)
    val conflictOptions = conflictTracker.options
    val vcsConfiguration = VcsConfiguration.getInstance(project)

    val changeListsEnabledPredicate = ChangeListsEnabledPredicate(project, disposable!!)

    return panel {
      blockRow {
        row {
          enableIf(changeListsEnabledPredicate)
          checkBox(message("settings.changelists.create.automatically.checkbox"), appSettings::CREATE_CHANGELISTS_AUTOMATICALLY)
        }

        row {
          enableIf(changeListsEnabledPredicate)
          checkBox(message("settings.partial.changelists.enable.checkbox"), appSettings::ENABLE_PARTIAL_CHANGELISTS)
            .onApply {
              ApplicationManager.getApplication().messageBus.syncPublisher(LineStatusTrackerSettingListener.TOPIC).settingsUpdated()
            }
        }
      }

      titledRow(message("settings.inactive.changelist.group.title")) {
        enableSubRowsIf(changeListsEnabledPredicate)
        row {
          checkBox(message("settings.highlight.files.from.non.active.changelist.checkbox"),
                   conflictOptions::HIGHLIGHT_NON_ACTIVE_CHANGELIST)
            .onApply { conflictTracker.optionsChanged() }
        }

        row {
          checkBox(message("settings.show.conflict.resolve.dialog.checkbox"), conflictOptions::SHOW_DIALOG)
            .onApply { conflictTracker.optionsChanged() }
        }

        fullRow {
          label(message("settings.label.when.empty.changelist.becomes.inactive"))
          comboBox(EnumComboBoxModel(VcsShowConfirmationOption.Value::class.java), vcsConfiguration::REMOVE_EMPTY_INACTIVE_CHANGELISTS,
                   renderer = listCellRenderer { value, _, _ ->
                     setText(when (value) {
                               VcsShowConfirmationOption.Value.SHOW_CONFIRMATION -> message("remove.changelist.combobox.show.options")
                               VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY -> message("remove.changelist.combobox.remove.silently")
                               VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY -> message("remove.changelist.combobox.do.not.remove")
                               else -> ""
                             })
                   }
          )
        }
      }

      titledRow(message("settings.changelist.conflicts.group.title")) {
        enableSubRowsIf(changeListsEnabledPredicate)
        row {
          checkBox(message("settings.highlight.files.with.conflicts.checkbox"), conflictOptions::HIGHLIGHT_CONFLICTS)
            .onApply { conflictTracker.optionsChanged() }
        }.largeGapAfter()

        val ignoredFilesModel = DefaultListModel<String>()
        row {
          label(message("settings.files.with.ignored.conflicts.list.title"))
        }
        row {
          val list = JBList(ignoredFilesModel).apply {
            emptyText.text = message("no.ignored.files")
          }
          scrollPane(list)
            .onReset {
              ignoredFilesModel.clear()
              for (path in conflictTracker.ignoredConflicts) {
                ignoredFilesModel.addElement(path)
              }
            }
        }
        row {
          right {
            var shouldClear = false
            button(message("button.clear")) {
              shouldClear = true
              ignoredFilesModel.clear()
            }
              .onIsModified { shouldClear }
              .onReset { shouldClear = false }
              .onApply {
                if (shouldClear) {
                  for (conflict in conflictTracker.conflicts.values) {
                    conflict.ignored = false
                  }
                  shouldClear = false
                }
              }
              .enableIf(ListNotEmptyPredicate(ignoredFilesModel))
          }
        }
      }
    }
  }

  class ChangeListsEnabledPredicate(val project: Project, val disposable: Disposable) : ComponentPredicate() {
    override fun invoke(): Boolean = ChangeListManager.getInstance(project).areChangeListsEnabled()

    override fun addListener(listener: (Boolean) -> Unit) {
      onChangeListAvailabilityChanged(project, disposable, true) {
        listener(invoke())
      }
    }
  }

  class ListNotEmptyPredicate(val listModel: DefaultListModel<*>) : ComponentPredicate() {
    override fun invoke(): Boolean = !listModel.isEmpty

    override fun addListener(listener: (Boolean) -> Unit) {
      listModel.addListDataListener(object : ListDataListener {
        override fun intervalAdded(e: ListDataEvent?) {
          listener(invoke())
        }

        override fun intervalRemoved(e: ListDataEvent?) {
          listener(invoke())
        }

        override fun contentsChanged(e: ListDataEvent?) {
          listener(invoke())
        }
      })
    }
  }
}