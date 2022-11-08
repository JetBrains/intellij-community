// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.conflicts

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.onChangeListAvailabilityChanged
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vcs.impl.LineStatusTrackerSettingListener
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.listCellRenderer
import javax.swing.DefaultListModel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class ChangelistConflictConfigurable(val project: Project)
  : BoundSearchableConfigurable(message("configurable.ChangelistConflictConfigurable.display.name"),
                                "project.propVCSSupport.ChangelistConflict"), NoScroll {
  override fun createPanel(): DialogPanel {
    val appSettings = VcsApplicationSettings.getInstance()
    val vcsConfiguration = VcsConfiguration.getInstance(project)

    val changeListsEnabledPredicate = when {
      project.isDefault -> ComponentPredicate.TRUE
      else -> ChangeListsEnabledPredicate(project, disposable!!)
    }
    val canTrackChangelistConflicts = when {
      project.isDefault -> ComponentPredicate.TRUE
      else -> CanTrackChangelistConflictsPredicate(project, disposable!!)
    }

    val conflictTracker = when {
      project.isDefault -> null
      else -> ChangelistConflictTracker.getInstance(project)
    }

    return panel {
      row {
        checkBox(message("settings.changelists.create.automatically.checkbox"))
          .bindSelected(appSettings::CREATE_CHANGELISTS_AUTOMATICALLY)
      }.enabledIf(changeListsEnabledPredicate)

      row {
        checkBox(message("settings.partial.changelists.enable.checkbox"))
          .bindSelected(appSettings::ENABLE_PARTIAL_CHANGELISTS)
          .onApply {
            ApplicationManager.getApplication().messageBus.syncPublisher(LineStatusTrackerSettingListener.TOPIC).settingsUpdated()
          }
      }.enabledIf(changeListsEnabledPredicate)

      group(message("settings.inactive.changelist.group.title")) {
        if (conflictTracker != null) {
          val conflictOptions = conflictTracker.options
          row {
            checkBox(message("settings.highlight.files.from.non.active.changelist.checkbox"))
              .bindSelected(conflictOptions::HIGHLIGHT_NON_ACTIVE_CHANGELIST)
              .onApply { conflictTracker.optionsChanged() }
          }

          row {
            checkBox(message("settings.show.conflict.resolve.dialog.checkbox"))
              .bindSelected(conflictOptions::SHOW_DIALOG)
              .onApply { conflictTracker.optionsChanged() }
          }.enabledIf(canTrackChangelistConflicts)
        }

        row(message("settings.label.when.empty.changelist.becomes.inactive")) {
          comboBox(EnumComboBoxModel(VcsShowConfirmationOption.Value::class.java),
                   renderer = listCellRenderer { value, _, _ ->
                     setText(when (value) {
                               VcsShowConfirmationOption.Value.SHOW_CONFIRMATION -> message("remove.changelist.combobox.show.options")
                               VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY -> message("remove.changelist.combobox.remove.silently")
                               VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY -> message("remove.changelist.combobox.do.not.remove")
                               else -> ""
                             })
                   }
          ).bindItem(vcsConfiguration::REMOVE_EMPTY_INACTIVE_CHANGELISTS.toNullableProperty())
        }
      }.enabledIf(changeListsEnabledPredicate)

      if (conflictTracker != null) {
        val conflictOptions = conflictTracker.options
        group(message("settings.changelist.conflicts.group.title")) {
          row {
            checkBox(message("settings.highlight.files.with.conflicts.checkbox"))
              .bindSelected(conflictOptions::HIGHLIGHT_CONFLICTS)
              .onApply { conflictTracker.optionsChanged() }
          }.bottomGap(BottomGap.SMALL)

          val ignoredFilesModel = DefaultListModel<String>()
          row {
            val list = JBList(ignoredFilesModel).apply {
              emptyText.text = message("no.ignored.files")
            }
            scrollCell(list)
              .onReset {
                ignoredFilesModel.clear()
                for (path in conflictTracker.ignoredConflicts) {
                  ignoredFilesModel.addElement(path)
                }
              }
              .align(Align.FILL)
              .label(message("settings.files.with.ignored.conflicts.list.title"), LabelPosition.TOP)
          }.resizableRow()
          row {
            var shouldClear = false
            button(message("button.clear")) {
              shouldClear = true
              ignoredFilesModel.clear()
            }
              .onIsModified { shouldClear }
              .onReset { shouldClear = false }
              .onApply {
                if (shouldClear) {
                  conflictTracker.clearAllIgnored()
                  shouldClear = false
                }
              }.align(AlignX.RIGHT)
              .enabledIf(ListNotEmptyPredicate(ignoredFilesModel))
          }
        }.enabledIf(canTrackChangelistConflicts)
          .resizableRow()
      }
    }
  }

  class ChangeListsEnabledPredicate(val project: Project, val disposable: Disposable) : ComponentPredicate() {
    override fun invoke(): Boolean = ChangeListManager.getInstance(project).areChangeListsEnabled()

    override fun addListener(listener: (Boolean) -> Unit) {
      val busConnection = project.messageBus.connect(disposable)
      onChangeListAvailabilityChanged(busConnection) { listener(invoke()) }
      listener(invoke())
    }
  }

  class CanTrackChangelistConflictsPredicate(val project: Project, val disposable: Disposable) : ComponentPredicate() {
    override fun invoke(): Boolean {
      if (!ChangeListManager.getInstance(project).areChangeListsEnabled()) return false

      val activeVcss = ProjectLevelVcsManager.getInstance(project).allActiveVcss
      if (activeVcss.isEmpty()) return true // show options without VCSes

      val onlyPartialChangelists = LineStatusTrackerManager.getInstance(project).arePartialChangelistsEnabled() &&
                                   activeVcss.all { vcs -> vcs.arePartialChangelistsSupported() }
      return !onlyPartialChangelists
    }

    override fun addListener(listener: (Boolean) -> Unit) {
      val busConnection = project.messageBus.connect(disposable)
      onChangeListAvailabilityChanged(busConnection) { listener(invoke()) }
      busConnection.subscribe(LineStatusTrackerSettingListener.TOPIC, LineStatusTrackerSettingListener { listener(invoke()) })
      busConnection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, VcsMappingListener { listener(invoke()) })
      listener(invoke())
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