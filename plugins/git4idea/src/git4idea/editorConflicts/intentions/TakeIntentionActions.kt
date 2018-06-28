// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.editorConflicts.intentions

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.*
import com.intellij.lang.EditorConflictSupport.ConflictMarkerType.*
import com.intellij.psi.PsiElement


class TakeThisIntentionAction(element: PsiElement) : ConflictsIntention(element, "Take this"), HighPriorityAction {
  override fun doInvoke(project: Project, editor: Editor, d: Document, marker: PsiElement) {
    val endMarker = getNextMarker(marker) ?: return
    val textRange = getSectionInnerRange(marker, endMarker, d)
    val group = getMarkerGroup(marker)
    val outerRange = group.first.rangeWithLine(d).union(group.last.rangeWithLine(d))

    val textToInsert = d.substring(textRange)
    d.replaceString(outerRange.startOffset, outerRange.endOffset + 1, textToInsert)
  }
}

class TakeBothIntentionAction(element: PsiElement) : ConflictsIntention(element, "Take both"){
  override fun doInvoke(project: Project, editor: Editor, d: Document, marker: PsiElement) {
    val group = getMarkerGroup(marker)

    val beginFirst = group.getMarker(BeforeFirst) ?: return
    val endFirst = getNextMarker(beginFirst) ?: return
    val beginLast = group.getMarker(BeforeLast) ?: return
    val endLast = getNextMarker(beginLast) ?: return

    val firstTextRange = getSectionInnerRange(beginFirst, endFirst, d)
    val lastTextRange = getSectionInnerRange(beginLast, endLast, d)
    val outerRange = group.first.rangeWithLine(d).union(group.last.rangeWithLine(d))

    val textToInsert = d.substring(firstTextRange) + d.substring(lastTextRange)
    d.replaceString(outerRange.startOffset, outerRange.endOffset + 1, textToInsert)
  }
}

class TakeNoneIntentionAction(element: PsiElement) : ConflictsIntention(element, "Take none"), LowPriorityAction {
  override fun doInvoke(project: Project, editor: Editor, d: Document, marker: PsiElement) {
    val group = getMarkerGroup(marker)
    val outerRange = group.first.rangeWithLine(d).union(group.last.rangeWithLine(d))
    d.deleteString(outerRange.startOffset, outerRange.endOffset + 1)
  }
}