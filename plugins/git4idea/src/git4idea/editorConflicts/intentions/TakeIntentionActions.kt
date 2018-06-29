// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.editorConflicts.intentions

import com.intellij.lang.EditorConflictSupport
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupAdapter
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.vcs.*
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import javax.swing.ListSelectionModel


class TakeIntentionAction(element: PsiElement) : ConflictsIntention(element, "Take...") {
  override fun doInvoke(project: Project, editor: Editor, d: Document, marker: PsiElement) {
    val group = getMarkerGroup(marker)
    val outerRange = group.first.rangeWithLine(d).union(group.last.rangeWithLine(d))

    getOtherMarkerAndRun(marker, editor) { markers ->
      WriteCommandAction.runWriteCommandAction(project) {
        val newText = markers.joinToString(separator = "") { getInnerText(it, d) }
        d.replaceString(outerRange.startOffset, outerRange.endOffset + 1, newText)
      }
    }
  }

  private fun getInnerText(marker: PsiElement, d: Document): String {
    val end = getNextMarker(marker) ?: return ""
    val range = getSectionInnerRange(marker, end, d)
    return d.substring(range)
  }

  private fun getOtherMarkerAndRun(marker: PsiElement, editor: Editor, continuation: (List<PsiElement>) -> Unit) {
    val group = getMarkerGroup(marker)
    val type = getConflictMarkerType(marker) ?: return

    val thisPtr = SmartPointerManager.createPointer(marker)
    val otherPtrs = EditorConflictSupport.ConflictMarkerType.values()
      .filter { it != type && it != EditorConflictSupport.ConflictMarkerType.AfterLast }
      .mapNotNull { group.getMarker(it) }
      .map { SmartPointerManager.createPointer(it) }

    val options = mutableListOf(Pair("This", listOf(thisPtr)))
    for (otherPtr in otherPtrs) {
      options.add(Pair("This and ${typeToText(otherPtr.element)}", listOf(thisPtr, otherPtr)))
    }
    options.add(Pair("None", emptyList()))

    showPopup(editor, options, continuation)
  }

  private fun typeToText(marker: PsiElement?): String {
    if (marker == null) return "???"
    val type = getConflictMarkerType(marker)
    return when(type) {
      EditorConflictSupport.ConflictMarkerType.BeforeFirst -> "Current"
      EditorConflictSupport.ConflictMarkerType.BeforeMerged -> "Common"
      EditorConflictSupport.ConflictMarkerType.BeforeLast -> "Incoming"
      EditorConflictSupport.ConflictMarkerType.AfterLast, null -> "???"
    }
  }

  private fun showPopup(editor: Editor,
                        options: List<Pair<String, List<SmartPsiElementPointer<PsiElement>>>>,
                        continuation: (List<PsiElement>) -> Unit) {

    val highlighter = Highlighter(editor)

    data class ModelItem(val elementPtrs: Pair<String, List<SmartPsiElementPointer<PsiElement>>>) {
      override fun toString() = elementPtrs.first
    }

    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(options.map(::ModelItem))
      .setTitle("Pick what to take")
      .setMovable(false)
      .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      .setResizable(false)
      .setRequestFocus(true)
      .setItemChosenCallback {
        if (it != null)
          continuation(it.elementPtrs.second.mapNotNull { ptr -> ptr.element })
      }
      .setItemSelectedCallback {
        if (it != null && !editor.isDisposed) {
          val ranges = it.elementPtrs.second
            .mapNotNull { ptr -> ptr.element }
            .mapNotNull { marker -> getSectionInnerRange(marker, getNextMarker(marker) ?: return@mapNotNull null, editor.document)}
          highlighter.highlight(ranges)
        }
      }
      .addListener(object : JBPopupAdapter() {
        override fun onClosed(event: LightweightWindowEvent) {
          highlighter.dropHighlight()
        }
      })
      .createPopup()
      .showInBestPositionFor(editor)

  }

}
