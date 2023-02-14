// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin

import com.intellij.codeInsight.CodeSmellInfo
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.Range
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CodeSmellDetector
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.VcsPreservingExecutor
import com.intellij.openapi.vcs.ex.compareLines
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.impl.PsiDocumentManagerImpl
import com.intellij.util.containers.MultiMap

internal object CodeAnalysisBeforeCheckinShowOnlyNew {
  private val LOG = logger<CodeAnalysisBeforeCheckinShowOnlyNew>()

  @JvmStatic
  fun runAnalysis(project: Project, selectedFiles: List<VirtualFile>, progressIndicator: ProgressIndicator) : List<CodeSmellInfo> {
    progressIndicator.isIndeterminate = false
    val codeSmellDetector = CodeSmellDetector.getInstance(project)
    val newCodeSmells = codeSmellDetector.findCodeSmells(selectedFiles)
    val location2CodeSmell = MultiMap<Pair<VirtualFile, Int>, CodeSmellInfo>()
    val fileToChanges: MutableMap<VirtualFile, List<Range>> = HashMap()
    val changeListManager = ChangeListManager.getInstance(project)
    val changes4Update = changeListManager.allChanges
    val fileDocumentManager = FileDocumentManager.getInstance()
    newCodeSmells.forEach { codeSmellInfo ->
      val virtualFile = fileDocumentManager.getFile(codeSmellInfo.document) ?: return@forEach
      val unchanged = fileToChanges.getOrPut(virtualFile) {
        try {
          val contentFromVcs = changeListManager.getChange(virtualFile)?.beforeRevision?.content ?: return@getOrPut emptyList()
          val documentContent = codeSmellInfo.document.immutableCharSequence
          compareLines(documentContent, contentFromVcs,
                       LineOffsetsUtil.create(documentContent),
                       LineOffsetsUtil.create(contentFromVcs)).iterateUnchanged().toList()
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
    runAnalysisAfterShelvingSync(project, selectedFiles, progressIndicator) {
      RefreshVFsSynchronously.updateChanges(changes4Update)
      WriteAction.runAndWait<Exception> { PsiDocumentManagerImpl.getInstance(project).commitAllDocuments() }
      codeSmellDetector.findCodeSmells(selectedFiles.filter { it.exists() }).forEach { oldCodeSmell ->
        val file = fileDocumentManager.getFile(oldCodeSmell.document) ?: return@forEach
        location2CodeSmell[Pair(file, oldCodeSmell.startLine)].forEach inner@{ newCodeSmell ->
          if (oldCodeSmell.description == newCodeSmell.description) {
            commonCodeSmells.add(newCodeSmell)
            return@inner
          }
        }
      }
    }
    return newCodeSmells.filter { !commonCodeSmells.contains(it) }.map {
      val file = fileDocumentManager.getFile(it.document) ?: return@map it
      if (file.isValid) {
        return@map it
      }
      val newFile = VirtualFileManager.getInstance().findFileByUrl(file.url) ?: return@map it
      val document = ReadAction.compute<Document?, Exception> { fileDocumentManager.getDocument(newFile) } ?: return@map it
      CodeSmellInfo(document, it.description, it.textRange, it.severity)
    }
  }

  private fun runAnalysisAfterShelvingSync(project: Project, files: List<VirtualFile>, progressIndicator: ProgressIndicator,  afterShelve: () -> Unit) {
    val versionedRoots = files.mapNotNull { ProjectLevelVcsManager.getInstance(project).getVcsRootFor(it) }.toSet()
    val message = VcsBundle.message("searching.for.code.smells.freezing.process")
    VcsPreservingExecutor.executeOperation(project, versionedRoots, message, progressIndicator) { afterShelve() }
  }
}