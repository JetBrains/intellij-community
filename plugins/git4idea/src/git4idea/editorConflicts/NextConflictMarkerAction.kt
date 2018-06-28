// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.editorConflicts

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.TokenType
import com.intellij.util.PsiNavigateUtil

class PrevConflictMarkerAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val psi = e.getData(CommonDataKeys.PSI_FILE) ?: return
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return

    val prevMarker = SyntaxTraverser.psiTraverser(psi).lastOrNull {
      it.node.elementType == TokenType.CONFLICT_MARKER && it.textRange.endOffset < editor.caretModel.offset
    }
    PsiNavigateUtil.navigate(prevMarker)
  }
}

class NextConflictMarkerAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val psi = e.getData(CommonDataKeys.PSI_FILE) ?: return
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return

    val nextMarker = SyntaxTraverser.psiTraverser(psi).firstOrNull {
      it.node.elementType == TokenType.CONFLICT_MARKER && it.textRange.startOffset > editor.caretModel.offset
    }
    PsiNavigateUtil.navigate(nextMarker)
  }
}