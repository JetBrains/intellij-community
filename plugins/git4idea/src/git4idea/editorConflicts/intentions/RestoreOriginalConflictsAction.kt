// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.editorConflicts.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler

class RestoreOriginalConflictsAction(element: PsiElement) : ConflictsIntention(element, "Restore original conflicts"), LowPriorityAction {
  override fun doInvoke(project: Project, editor: Editor, d: Document, marker: PsiElement) {
    val file = marker.containingFile.virtualFile ?: return
    val repo = GitUtil.getRepositoryManager(marker.project).getRepositoryForFile(marker.containingFile.virtualFile) ?: return

    FileDocumentManager.getInstance().saveAllDocuments()

    val h = GitLineHandler(project, repo.root, GitCommand.CHECKOUT)
    h.addParameters("-m")
    h.endOptions()
    h.addRelativeFiles(listOf(file))
    Git.getInstance().runCommand(h).getOutputOrThrow()

    file.refresh(false, false)
  }
}