// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.actions

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.ChangeList
import com.intellij.openapi.vcs.changes.ChangeListManagerEx
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.util.ArrayUtil
import com.intellij.util.ThreeState
import java.util.*

class RemoveChangeListAction : AbstractChangeListAction() {
  private val LOG = logger<RemoveChangeListAction>()

  override fun update(e: AnActionEvent) {
    val changeListsArray = e.getData(VcsDataKeys.CHANGE_LISTS)
    val changeLists = changeListsArray?.asList() ?: emptyList()
    val enabled = canRemoveChangeLists(e.project, changeLists)

    updateEnabledAndVisible(e, enabled)

    val presentation = e.presentation
    presentation.text = ActionsBundle.message("action.ChangesView.RemoveChangeList.text.template", changeLists.size)

    val hasChanges = !ArrayUtil.isEmpty(e.getData(VcsDataKeys.CHANGES))
    if (hasChanges) {
      val containsActiveChangelist = changeLists.any { it is LocalChangeList && it.isDefault }
      val changeListName =
        if (containsActiveChangelist) VcsBundle.message("changes.another.change.list")
        else VcsBundle.message("changes.default.change.list")
      presentation.description = ActionsBundle.message("action.ChangesView.RemoveChangeList.description.template",
                                                       changeLists.size, changeListName)
    }
    else {
      presentation.description = null
    }
  }

  private fun canRemoveChangeLists(project: Project?, lists: List<ChangeList>): Boolean {
    if (project == null || lists.isEmpty()) return false

    for (changeList in lists) {
      if (changeList !is LocalChangeList) return false
      if (changeList.isReadOnly) return false
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
    val manager = ChangeListManagerEx.getInstanceEx(project)

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

    val pendingLists = toAsk + toRemove
    val activeChangelistSelected = pendingLists.any { it.isDefault }

    if (activeChangelistSelected) {
      val remainingLists = manager.changeLists.subtract(pendingLists).toList()

      if (remainingLists.isEmpty()) {
        if (!confirmAllChangeListsRemoval(project, pendingLists, toAsk)) return

        var defaultList = manager.getChangeList(LocalChangeList.getDefaultName())
        if (defaultList == null) {
          defaultList = manager.addChangeList(LocalChangeList.getDefaultName(), null)
        }
        else {
          manager.editComment(defaultList.name, null)
          manager.editChangeListData(defaultList.name, null)
        }

        manager.setDefaultChangeList(defaultList!!)

        toRemove.addAll(toAsk)
        toRemove.remove(defaultList)
      }
      else {
        val newDefault = askNewDefaultChangeList(project, toAsk, remainingLists) ?: return
        manager.setDefaultChangeList(newDefault)

        toRemove.addAll(toAsk)
        if (toRemove.remove(newDefault)) LOG.error("New default changelist should be selected among remaining")
      }
    }
    else {
      if (confirmChangeListRemoval(project, toAsk)) {
        toRemove.addAll(toAsk)
      }
    }

    toRemove.forEach {
      manager.removeChangeList(it.name)
    }
  }

  private fun confirmChangeListRemoval(project: Project, lists: List<LocalChangeList>): Boolean {
    val haveNoChanges = lists.all { it.changes.isEmpty() }
    if (haveNoChanges) return true

    val message = if (lists.size == 1)
      VcsBundle.message("changes.removechangelist.warning.text", lists.single().name)
    else
      VcsBundle.message("changes.removechangelist.multiple.warning.text", lists.size)
    return Messages.YES == Messages.showYesNoDialog(project, message, VcsBundle.message("changes.removechangelist.warning.title"),
                                                    Messages.getQuestionIcon())
  }

  private fun confirmAllChangeListsRemoval(project: Project, pendingLists: List<LocalChangeList>, toAsk: List<LocalChangeList>): Boolean {
    if (pendingLists.size == 1) return true
    if (toAsk.isEmpty()) return true

    val haveNoChanges = pendingLists.all { it.changes.isEmpty() }
    if (haveNoChanges) return true

    val message = VcsBundle.message("changes.removechangelist.all.lists.warning.text", pendingLists.size)
    return Messages.YES == Messages.showYesNoDialog(project, message, VcsBundle.message("changes.removechangelist.warning.title"),
                                                    Messages.getQuestionIcon())
  }

  private fun askNewDefaultChangeList(project: Project,
                                      lists: List<LocalChangeList>,
                                      remainingLists: List<LocalChangeList>): LocalChangeList? {
    assert(remainingLists.isNotEmpty())
    val haveNoChanges = lists.all { it.changes.isEmpty() }

    // don't ask "Which changelist to make active" if there is only one option anyway
    // unless there are some changes to be moved - give user a chance to cancel deletion
    if (remainingLists.size == 1 && haveNoChanges) {
      return remainingLists.single()
    }
    else {
      val remainingListsNames = remainingLists.map { it.name }.toTypedArray()

      val message = if (haveNoChanges)
        VcsBundle.message("changes.remove.active.empty.prompt")
      else
        VcsBundle.message("changes.remove.active.prompt")
      val nameIndex = Messages.showChooseDialog(project, message,
                                                VcsBundle.message("changes.remove.active.title"), Messages.getQuestionIcon(),
                                                remainingListsNames, remainingListsNames.first())
      if (nameIndex < 0) return null

      return remainingLists[nameIndex]
    }
  }
}