// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.editorConflicts.intentions

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.tools.simple.SimpleDiffTool
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.getNextMarker
import com.intellij.openapi.vcs.getSectionInnerRange
import com.intellij.openapi.vcs.substring
import com.intellij.psi.PsiElement

class CompareChangesAction(element: PsiElement) : ConflictsIntention(element, "Compare with...") {
  override fun doInvoke(project: Project, editor: Editor, d: Document, marker: PsiElement) {
    val otherMarker = getNextMarker(marker) ?: return
    val oneMoreMarker = getNextMarker(otherMarker) ?: return

    val leftText = d.substring(getSectionInnerRange(marker, otherMarker, d))
    val rightText = d.substring(getSectionInnerRange(otherMarker, oneMoreMarker, d))

    val fileType = marker.containingFile.fileType
    val content1 = DiffContentFactory.getInstance().createEditable(project, leftText, fileType)
    val content2 = DiffContentFactory.getInstance().createEditable(project, rightText, fileType)

    val request = SimpleDiffRequest("Diff", content1, content2, "Left", "Right")

    val chain = SimpleDiffRequestChain(request)
    chain.putUserData(DiffUserDataKeysEx.FORCE_DIFF_TOOL, SimpleDiffTool.INSTANCE)

    DiffManager.getInstance().showDiff(project, chain, DiffDialogHints.DEFAULT)
  }
}