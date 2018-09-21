// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.actions

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.ChangeList
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.util.ArrayUtil
import com.intellij.util.ThreeState
import java.util.*

class RemoveChangeListAction : AnAction(), DumbAware {
  override fun update(e: AnActionEvent) {
    val changeListsArray = e.getData(VcsDataKeys.CHANGE_LISTS)
    val changeLists = changeListsArray?.asList() ?: emptyList()

    val hasChanges = !ArrayUtil.isEmpty(e.getData(VcsDataKeys.CHANGES))
    val enabled = canRemoveChangeLists(e.project, changeLists)

    val presentation = e.presentation
    presentation.isEnabled = enabled
    if (e.place == ActionPlaces.CHANGES_VIEW_POPUP) {
      presentation.isVisible = enabled
    }

    presentation.text = ActionsBundle.message("action.ChangesView.RemoveChangeList.text.template", changeLists.size)
    if (hasChanges) {
      val containsActiveChangelist = changeLists.any { it is LocalChangeList && it.isDefault }
      presentation.description = ActionsBundle.message("action.ChangesView.RemoveChangeList.description.template",
                                                       changeLists.size, if (containsActiveChangelist) "another" else "default")
    }
    else {
      presentation.description = null
    }
  }

  private fun canRemoveChangeLists(project: Project?, lists: List<ChangeList>): Boolean {
    if (project == null || lists.isEmpty()) return false

    val allChangeListsCount = ChangeListManager.getInstance(project).changeListsNumber
    for (changeList in lists) {
      if (changeList !is LocalChangeList) return false
      if (changeList.isReadOnly) return false
      if (changeList.isDefault && allChangeListsCount <= lists.size) return false
    }
    return true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val selectedLists = e.getRequiredData(VcsDataKeys.CHANGE_LISTS)

    @Suppress("UNCHECKED_CAST")
    deleteLists(project, Arrays.asList(*selectedLists) as Collection<LocalChangeList>)
  }

  private fun deleteLists(project: Project, lists: Collection<LocalChangeList>) {
    val manager = ChangeListManager.getInstance(project)

    val toRemove = mutableListOf<LocalChangeList>()
    val toAsk = mutableListOf<LocalChangeList>()

    for (list in lists.mapNotNull { manager.getChangeList(it.id) }) {
      when (ChangeListRemoveConfirmation.checkCanDeleteChangelist(project, list, explicitly = true)) {
        ThreeState.UNSURE -> toAsk.add(list)
        ThreeState.YES -> toRemove.add(list)
        ThreeState.NO -> {
        }
      }
    }

    if (toAsk.isNotEmpty() && askIfShouldRemoveChangeLists(project, toAsk)) {
      toRemove.addAll(toAsk)
    }

    // default changelist might have been changed in `askIfShouldRemoveChangeLists()`
    val defaultList = manager.defaultChangeList
    val shouldRemoveDefault = toRemove.remove(defaultList)

    toRemove.forEach { manager.removeChangeList(it.name) }

    if (shouldRemoveDefault && confirmActiveChangeListRemoval(project, listOf(defaultList))) {
      manager.removeChangeList(defaultList.name)
    }
  }

  private fun askIfShouldRemoveChangeLists(project: Project, lists: List<LocalChangeList>): Boolean {
    val activeChangelistSelected = lists.any { it.isDefault }
    if (activeChangelistSelected) {
      return confirmActiveChangeListRemoval(project, lists)
    }

    val haveNoChanges = lists.all { it.changes.isEmpty() }
    if (haveNoChanges) return true

    val message = if (lists.size == 1)
      VcsBundle.message("changes.removechangelist.warning.text", lists.single().name)
    else
      VcsBundle.message("changes.removechangelist.multiple.warning.text", lists.size)
    return Messages.YES == Messages.showYesNoDialog(project, message, VcsBundle.message("changes.removechangelist.warning.title"),
                                                    Messages.getQuestionIcon())
  }

  private fun confirmActiveChangeListRemoval(project: Project, lists: List<LocalChangeList>): Boolean {
    val manager = ChangeListManager.getInstance(project)
    val haveNoChanges = lists.all { it.changes.isEmpty() }

    val remainingLists = manager.changeLists.subtract(lists).toList()

    // Can't remove last changelist
    if (remainingLists.isEmpty()) {
      return false
    }

    // don't ask "Which changelist to make active" if there is only one option anyway
    // unless there are some changes to be moved - give user a chance to cancel deletion
    if (remainingLists.size == 1 && haveNoChanges) {
      manager.setDefaultChangeList(remainingLists.single())
      return true
    }

    val remainingListsNames = remainingLists.map { it.name }.toTypedArray()

    val message = if (haveNoChanges)
      VcsBundle.message("changes.remove.active.empty.prompt")
    else
      VcsBundle.message("changes.remove.active.prompt")
    val nameIndex = Messages.showChooseDialog(project, message,
                                              VcsBundle.message("changes.remove.active.title"), Messages.getQuestionIcon(),
                                              remainingListsNames, remainingListsNames.first())
    if (nameIndex < 0) return false
    manager.setDefaultChangeList(remainingLists[nameIndex])
    return true
  }
}