// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.editorConflicts

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.getConflictMarkerType
import com.intellij.openapi.vcs.rangeWithLine
import com.intellij.openapi.vcs.substring
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement
import com.intellij.vcs.log.impl.VcsLogContentUtil

class GotoConflictCommitHandler : GotoDeclarationHandler {
  override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
    if (sourceElement == null || getConflictMarkerType(sourceElement) == null) return emptyArray()
    val document = editor?.document ?: return emptyArray()

    val lineText = document.substring(sourceElement.rangeWithLine(document))
    if (!lineText.contains(" ")) return emptyArray()

    val commitIshText = lineText.substring(lineText.indexOf(" ") + 1)
    return arrayOf(DummyPsiElementForCommit(commitIshText, sourceElement.project))
  }

  override fun getActionText(context: DataContext) = "Goto Conflict Commit"
}

class DummyPsiElementForCommit(private val commitIshText: String, private val proj: Project) : FakePsiElement() {
  override fun getParent() = null
  override fun getContainingFile() = null

  override fun navigate(requestFocus: Boolean) {
    VcsLogContentUtil.openMainLogAndExecute(proj) { it.vcsLog.jumpToReference(commitIshText) }
  }

  override fun canNavigate() = true

}