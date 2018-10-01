// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions

import com.intellij.codeInsight.CodeSmellInfo
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.vcs.CodeSmellDetector
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangeListManagerEx
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import com.intellij.openapi.vcs.changes.ui.RollbackWorker
import com.intellij.openapi.vcs.ex.Range
import com.intellij.openapi.vcs.ex.createRanges
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.containers.MultiMap

class ChangesAnalysisAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val changeListManager = ChangeListManager.getInstance(project)
    val allChanges = changeListManager.allChanges
    val codeSmellDetector = CodeSmellDetector.getInstance(project)
    val newCodeSmells = codeSmellDetector.findCodeSmells(allChanges.mapNotNull { it.virtualFile })
    val location2CodeSmell = MultiMap<Pair<VirtualFile, Int>, CodeSmellInfo>()
    val file2Changes = HashMap<PsiFile, List<Range>>()
    newCodeSmells.forEach { codeSmellInfo ->
      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(codeSmellInfo.document) ?: return@forEach
      val ranges = file2Changes.getOrElse(psiFile) {
        try {
          val contentFromVcs = changeListManager.getChange(psiFile.virtualFile)?.beforeRevision?.content ?: return@getOrElse emptyList()
          getRanges(codeSmellInfo.document, contentFromVcs)
        }
        catch (e: VcsException) {
          emptyList()
        }
      }
      val oldLine = findVcsLine(ranges, codeSmellInfo.startLine)
      if (oldLine >= 0) {
        location2CodeSmell.putValue(Pair(psiFile.virtualFile, oldLine), codeSmellInfo)
      }
    }
    val commonCodeSmells = HashSet<CodeSmellInfo>()
    performShelve(project, {
      val oldCodeSmells = codeSmellDetector.findCodeSmells(allChanges.mapNotNull { it.virtualFile }.filter { it.exists() })
      oldCodeSmells.forEach { oldCodeSmell ->
        val file = FileDocumentManager.getInstance().getFile(oldCodeSmell.document) ?: return@forEach
        location2CodeSmell[Pair(file, oldCodeSmell.startLine)].forEach inner@{ newCodeSmell ->
          if (oldCodeSmell.description == newCodeSmell.description) {
            commonCodeSmells.add(newCodeSmell)
            return@inner
          }
        }
      }
    }, {
      val introducedCodeSmells = newCodeSmells.filter { !commonCodeSmells.contains(it) }
      codeSmellDetector.showCodeSmellErrors(introducedCodeSmells)
    })
  }

  private companion object {

    private fun getRanges(document: Document,
                          contentFromVcs: CharSequence): List<Range> {
      return createRanges(document.immutableCharSequence, StringUtilRt.convertLineSeparators(contentFromVcs, "\n"))
    }

    private fun findVcsLine(ranges: List<Range>, line: Int): Int {
      ranges.forEachIndexed { index, range ->
        when {
          index == 0 && line < range.line1 ->
            return line
          range.line1 <= line && line < range.line2 ->
            return if (range.type != Range.INSERTED) range.vcsLine1 + line - range.line1 else -1
          index == ranges.size - 1 || range.line2 <= line && line < ranges[index + 1].line1 ->
            return range.vcsLine2 + line - range.line2
        }
      }
      return -1
    }

    @Synchronized
    fun performShelve(project: Project, afterShelve: () -> Unit, afterUnshelve: () -> Unit) {
      val operation = StashOperation(project)
      operation.changeListManager.blockModalNotifications()
      val rollbackWorker = RollbackWorker(project)
      val changes = operation.save()
      val analyze = {
        try {
          afterShelve()
          operation.load()
          afterUnshelve()
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