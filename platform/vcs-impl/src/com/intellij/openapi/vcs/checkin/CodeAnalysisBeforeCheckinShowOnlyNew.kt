// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin


import com.intellij.codeInsight.CodeSmellInfo
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.Range
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CodeSmellDetector
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangeListManagerEx
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import com.intellij.openapi.vcs.changes.ui.RollbackWorker
import com.intellij.openapi.vcs.ex.compareLines
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap


object CodeAnalysisBeforeCheckinShowOnlyNew {

  @JvmStatic
  fun runAnalysis(project: Project, selectedFiles: List<VirtualFile>) : List<CodeSmellInfo> {
    val codeSmellDetector = CodeSmellDetector.getInstance(project)
    val newCodeSmells = codeSmellDetector.findCodeSmells(selectedFiles)
    val location2CodeSmell = MultiMap<Pair<VirtualFile, Int>, CodeSmellInfo>()
    val file2Changes = HashMap<PsiFile, List<Range>>()
    val changeListManager = ChangeListManager.getInstance(project)
    newCodeSmells.forEach { codeSmellInfo ->
      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(codeSmellInfo.document) ?: return@forEach
      val unchanged = file2Changes.getOrPut(psiFile) {
        try {
          val contentFromVcs = changeListManager.getChange(psiFile.virtualFile)?.beforeRevision?.content ?: return@getOrPut emptyList()
          val documentContent = codeSmellInfo.document.immutableCharSequence
          ContainerUtil.newArrayList(compareLines(documentContent, contentFromVcs,
                                         LineOffsetsUtil.create(documentContent), LineOffsetsUtil.create(contentFromVcs)).iterateUnchanged())

        }
        catch (e: VcsException) {
          emptyList()
        }
      }
      val startLine = codeSmellInfo.startLine
      val range = unchanged.firstOrNull { it.start1 <= startLine && startLine < it.end1 } ?: return@forEach
      location2CodeSmell.putValue(Pair(psiFile.virtualFile, range.start2 + startLine - range.start1), codeSmellInfo)
    }

    val commonCodeSmells = HashSet<CodeSmellInfo>()
    runAnalysisAfterShelvingSync(project) {
      codeSmellDetector.findCodeSmells(selectedFiles.filter { it.exists() }).forEach { oldCodeSmell ->
        val file = FileDocumentManager.getInstance().getFile(oldCodeSmell.document) ?: return@forEach
        location2CodeSmell[Pair(file, oldCodeSmell.startLine)].forEach inner@{ newCodeSmell ->
          if (oldCodeSmell.description == newCodeSmell.description) {
            commonCodeSmells.add(newCodeSmell)
            return@inner
          }
        }
      }
    }
    return newCodeSmells.filter { !commonCodeSmells.contains(it) }
  }

  private fun runAnalysisAfterShelvingSync(project: Project, afterShelve: () -> Unit) {
    val operation = StashOperation(project)
    operation.changeListManager.blockModalNotifications()
    val changes = operation.save()
    val rollbackWorker = RollbackWorker(project, "Code Analysis", true)
    operation.changeListManager.freeze("Performing rollback")
    rollbackWorker.doRollback(changes, true, false, null, null)
    try {
      afterShelve()
    }
    finally {
      try {
        operation.load()
      }
      finally {
        operation.changeListManager.unblockModalNotifications()
        operation.changeListManager.unfreeze()
      }
    }
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