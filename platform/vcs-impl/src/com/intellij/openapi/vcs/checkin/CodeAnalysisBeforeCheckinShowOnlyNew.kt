// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin


import com.intellij.codeInsight.CodeSmellInfo
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.Range
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CodeSmellDetector
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import com.intellij.openapi.vcs.ex.compareLines
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap


internal object CodeAnalysisBeforeCheckinShowOnlyNew {
  val LOG = Logger.getInstance(CodeAnalysisBeforeCheckinShowOnlyNew.javaClass)

  @JvmStatic
  fun runAnalysis(project: Project, selectedFiles: List<VirtualFile>, progressIndicator: ProgressIndicator) : List<CodeSmellInfo> {
    val codeSmellDetector = CodeSmellDetector.getInstance(project)
    val newCodeSmells = codeSmellDetector.findCodeSmells(selectedFiles)
    val location2CodeSmell = MultiMap<Pair<VirtualFile, Int>, CodeSmellInfo>()
    val file2Changes = HashMap<VirtualFile, List<Range>>()
    val changeListManager = ChangeListManager.getInstance(project)
    newCodeSmells.forEach { codeSmellInfo ->
      val virtualFile = FileDocumentManager.getInstance().getFile(codeSmellInfo.document) ?: return@forEach
      val unchanged = file2Changes.getOrPut(virtualFile) {
        try {
          val contentFromVcs = changeListManager.getChange(virtualFile)?.beforeRevision?.content ?: return@getOrPut emptyList()
          val documentContent = codeSmellInfo.document.immutableCharSequence
          ContainerUtil.newArrayList(compareLines(documentContent, contentFromVcs,
                                                  LineOffsetsUtil.create(documentContent),
                                                  LineOffsetsUtil.create(contentFromVcs)).iterateUnchanged())

        }
        catch (e: VcsException) {
          LOG.warn("Couldn't load content", e)
          emptyList()
        }
      }
      val startLine = codeSmellInfo.startLine
      val range = unchanged.firstOrNull { it.start1 <= startLine && startLine < it.end1 } ?: return@forEach
      location2CodeSmell.putValue(Pair(virtualFile, range.start2 + startLine - range.start1), codeSmellInfo)
    }

    val commonCodeSmells = HashSet<CodeSmellInfo>()
    runAnalysisAfterShelvingSync(project, progressIndicator) {
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

  private fun runAnalysisAfterShelvingSync(project: Project, progressIndicator: ProgressIndicator,  afterShelve: () -> Unit) {
    val operation = ShelveOperation(project)

    VcsFreezingProcess(project, VcsBundle.message("searching.for.code.smells.freezing.process")) {
      val progressManager = ProgressManager.getInstance()
      progressManager.executeNonCancelableSection { operation.save(progressIndicator) }
      try {
        afterShelve()
      }
      finally {
        progressManager.executeNonCancelableSection { operation.load(progressIndicator) }
      }
    }.execute()
  }

  private class ShelveOperation(project: Project) {
    val changeListManager = ChangeListManager.getInstance(project) as ChangeListManagerEx
    private val shelveChangeManager = ShelveChangesManager.getInstance(project)
    private var shelvedChangeListPairs = ArrayList<Pair<LocalChangeList, ShelvedChangeList>>()

    fun save(progressIndicator: ProgressIndicator): List<Change>  {
      val changes = ArrayList<Change>()
      var i = 0
      val changeLists = changeListManager.changeLists
      val size = changeLists.size
      changeLists.filter { it.changes.isNotEmpty() }.forEach {
        progressIndicator.fraction = (i++).toDouble() / size.toDouble()
        progressIndicator.text = VcsBundle.message("searching.for.code.smells.shelving", it.name)
        val shelveChanges = shelveChangeManager.shelveChanges(it.changes, it.name, true, true)
        changes.addAll(it.changes)
        shelvedChangeListPairs.add(Pair(it, shelveChanges))
      }
      return changes
    }

    fun load(progressIndicator: ProgressIndicator) {
      var i = 0
      val size = shelvedChangeListPairs.size
      shelvedChangeListPairs.forEach { (local, shelved) ->
        progressIndicator.fraction = (i++).toDouble() / size.toDouble()
        progressIndicator.text = VcsBundle.message("searching.for.code.smells.unshelving", shelved.name)
        shelveChangeManager.unshelveChangeList(shelved, null, null, local, false, true, false, null, null)
      }
    }
  }
}