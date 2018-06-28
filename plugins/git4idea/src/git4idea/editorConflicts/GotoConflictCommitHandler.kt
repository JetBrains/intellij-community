// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.editorConflicts

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.actions.TabbedShowHistoryAction
import com.intellij.openapi.vcs.actions.TabbedShowHistoryForRevisionAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement
import com.intellij.vcs.log.impl.VcsLogContentUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.history.GitHistoryUtils

class GotoConflictCommitHandler : GotoDeclarationHandler {
  override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
    if (sourceElement == null || getConflictMarkerType(sourceElement) == null) return emptyArray()
    val document = editor?.document ?: return emptyArray()
    val markerText = getMarkerText(sourceElement, document) ?: return emptyArray()
    val commitishText = getCommitishByText(markerText, sourceElement, document) ?: return emptyArray()

    return arrayOf(DummyPsiElementForCommit(commitishText, sourceElement.project, sourceElement.containingFile.virtualFile))
  }

  private fun getCommitishByText(text: String, marker: PsiElement, document: Document): String? {
    if (text != "merged common ancestors") {
      return text
    }

    val first = getMarkerText(getPrevMarker(marker) ?: return null, document) ?: return null
    val second = getMarkerText(getNextMarker(getNextMarker(marker) ?: return null) ?: return null, document) ?: return null

    val repo = GitUtil.getRepositoryManager(marker.project).getRepositoryForFile(marker.containingFile.virtualFile) ?: return null

    return GitHistoryUtils.getMergeBase(marker.project, repo.root, first, second)?.rev
  }

  private fun getMarkerText(marker: PsiElement, document: Document): String? {
    val lineText = document.substring(marker.rangeWithLine(document))
    return if (lineText.contains(" ")) lineText.substring(lineText.indexOf(" ") + 1).trim() else lineText.trim()
  }

  override fun getActionText(context: DataContext) = "Goto Conflict Commit"
}

class DummyPsiElementForCommit(private val commitIshText: String,
                               private val proj: Project,
                               private val vFile: VirtualFile) : FakePsiElement() {
  override fun getParent() = null
  override fun getContainingFile() = null

  override fun navigate(requestFocus: Boolean) {
    val repo = GitUtil.getRepositoryManager(proj).getRepositoryForFile(vFile) ?: return
    val h = GitLineHandler(proj, repo.root, GitCommand.REV_PARSE)
    h.setSilent(true)
    h.addParameters(commitIshText)
    val output = Git.getInstance().runCommand(h).getOutputOrThrow().trim { it <= ' ' }
    TabbedShowHistoryForRevisionAction.showNewFileHistory(proj, VcsUtil.getFilePath(vFile), output)
//    VcsLogContentUtil.openMainLogAndExecute(proj) { it.vcsLog.jumpToReference(commitIshText) }
  }

  override fun canNavigate() = true

}