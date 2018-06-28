// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.editorConflicts.intentions

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.EditorConflictSupport
import com.intellij.openapi.vcs.getConflictMarkerType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.FileContentUtil

class SetActiveIntentionAction(element: PsiElement) : ConflictsIntention(element, "Set active"), HighPriorityAction {
  override fun doInvoke(project: Project, editor: Editor, d: Document, marker: PsiElement) {
    EditorConflictSupport.setActiveMarkerType(project, getConflictMarkerType(marker))
    FileContentUtil.reparseOpenedFiles()
    PsiManager.getInstance(project).dropPsiCaches()
    DaemonCodeAnalyzer.getInstance(project).restart()
  }
}