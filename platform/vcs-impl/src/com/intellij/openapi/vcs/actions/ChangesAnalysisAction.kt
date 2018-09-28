// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions

import com.intellij.codeInsight.CodeSmellInfo
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CodeSmellDetector
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangeListManagerEx
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import com.intellij.openapi.vcs.changes.ui.RollbackWorker
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.MultiMap

class ChangesAnalysisAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val lineStatusTrackerManager = LineStatusTrackerManager.getInstance(project)
    val changeListManager = ChangeListManager.getInstance(project)
    val allChanges = changeListManager.allChanges
    val codeSmellDetector = CodeSmellDetector.getInstance(project)
    val newCodeSmells = codeSmellDetector.findCodeSmells(allChanges.mapNotNull { it.virtualFile })
    val location2CodeSmell = MultiMap<Pair<VirtualFile, Int>, CodeSmellInfo>()
    newCodeSmells.forEach {
      val file = FileDocumentManager.getInstance().getFile(it.document) ?: return@forEach
      val lineStatusTracker = lineStatusTrackerManager.getLineStatusTracker(it.document) ?: return@forEach
      val oldPosition = lineStatusTracker.transferLineToVcs(it.startLine, false)
      location2CodeSmell.putValue(Pair(file,oldPosition), it)
    }
    performAfterStashing(project) {
      val oldCodeSmells = codeSmellDetector.findCodeSmells(allChanges.mapNotNull { it.virtualFile }
                                                             .filter { it.exists() })
      val commonCodeSmells = HashSet<CodeSmellInfo>()
      oldCodeSmells.forEach { oldCodeSmell ->
        val file = FileDocumentManager.getInstance().getFile(oldCodeSmell.document) ?: return@forEach
        location2CodeSmell[Pair(file, oldCodeSmell.startLine)].forEach inner@{ newCodeSmell ->
          if (oldCodeSmell.description == newCodeSmell.description) {
            commonCodeSmells.add(newCodeSmell)
            return@inner
          }
        }
      }
      val introducedCodeSmells = newCodeSmells.filter { !commonCodeSmells.contains(it) }
      codeSmellDetector.showCodeSmellErrors(introducedCodeSmells)
    }
  }

  private companion object {
    @Synchronized
    fun performAfterStashing(project: Project, action: () -> Unit) {
      val operation = StashOperation(project)
      operation.changeListManager.blockModalNotifications()
      val rollbackWorker = RollbackWorker(project)
      val changes = operation.save()
      val analyze = {
        try {
          action()
          operation.load()
        } finally {
          operation.changeListManager.unblockModalNotifications()
        }
      }
      rollbackWorker.doRollback(changes, true, false, analyze, null)
    }

    private class StashOperation(project: Project) {
      val changeListManager = ChangeListManager.getInstance(project) as ChangeListManagerEx
      private val shelveChangeManager = ShelveChangesManager.getInstance(project)
      private var shelvedChangeListPairs = ArrayList<Pair<LocalChangeList, ShelvedChangeList>>()

      fun save(): List<Change>  {
        val changes = ArrayList<Change>()
        changeListManager.changeLists.forEach {
          val shelveChanges = shelveChangeManager.shelveChanges(it.changes, it.name, false)
          changes.addAll(it.changes)
          shelvedChangeListPairs.add(Pair(it, shelveChanges))
        }
        return changes
      }

      fun load() {
        shelvedChangeListPairs.forEach { (local, shelved) ->
          shelveChangeManager.unshelveChangeList(shelved, null, null, local, false)
          shelveChangeManager.deleteChangeList(shelved)
        }
      }
    }
  }
}