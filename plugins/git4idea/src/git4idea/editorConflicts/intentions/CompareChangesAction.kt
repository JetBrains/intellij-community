// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.editorConflicts.intentions

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.tools.simple.SimpleDiffTool
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.lang.EditorConflictSupport
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

class CompareChangesAction(element: PsiElement) : ConflictsIntention(element, "Compare...") {
  override fun doInvoke(project: Project, editor: Editor, d: Document, marker: PsiElement) {
    getOtherMarkerAndRun(marker, editor) { thisMarker, otherMarker ->
      val nextForThis = getNextMarker(thisMarker) ?: return@getOtherMarkerAndRun
      val nextForOther = getNextMarker(otherMarker) ?: return@getOtherMarkerAndRun

      val leftText = d.substring(getSectionInnerRange(thisMarker, nextForThis, d))
      val rightText = d.substring(getSectionInnerRange(otherMarker, nextForOther, d))

      val fileType = thisMarker.containingFile.fileType
      val content1 = DiffContentFactory.getInstance().createEditable(project, leftText, fileType)
      val content2 = DiffContentFactory.getInstance().createEditable(project, rightText, fileType)

      val request = SimpleDiffRequest("Diff", content1, content2, "Left", "Right")

      val chain = SimpleDiffRequestChain(request)
      chain.putUserData(DiffUserDataKeysEx.FORCE_DIFF_TOOL, SimpleDiffTool.INSTANCE)

      DiffManager.getInstance().showDiff(project, chain, DiffDialogHints.DEFAULT)
    }
  }

  private fun getOtherMarkerAndRun(marker: PsiElement, editor: Editor, continuation: (PsiElement, PsiElement) -> Unit) {
    val group = getMarkerGroup(marker)
    val type = getConflictMarkerType(marker) ?: return

    val otherPtrs = EditorConflictSupport.ConflictMarkerType.values()
      .filter { it != type && it != EditorConflictSupport.ConflictMarkerType.AfterLast }
      .mapNotNull { group.getMarker(it) }
      .map { SmartPointerManager.createPointer(it) }
    val thisPtr = SmartPointerManager.createPointer(marker)

    showPopup(editor, otherPtrs) { selectedElement ->
      val thisElement = thisPtr.element ?: return@showPopup
      continuation(thisElement, selectedElement)
    }
  }


  private fun showPopup(editor: Editor,
                        otherPtrs: List<SmartPsiElementPointer<PsiElement>>,
                        continuation: (PsiElement) -> Unit) {

    val highlighter = Highlighter(editor)

    data class ModelItem(val elementPtr: SmartPsiElementPointer<PsiElement>) {
      override fun toString() = elementPtr.element?.text ?: "obsolete"
    }

    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(otherPtrs.map(::ModelItem))
      .setTitle("Pick other section")
      .setMovable(false)
      .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      .setResizable(false)
      .setRequestFocus(true)
      .setItemChosenCallback {
        if (it != null)
          continuation(it.elementPtr.element ?: return@setItemChosenCallback)
      }
      .setItemSelectedCallback {
        if (it != null && !editor.isDisposed) {
          val marker = it.elementPtr.element ?: return@setItemSelectedCallback
          val nextMarker = getNextMarker(marker) ?: return@setItemSelectedCallback
          highlighter.highlight(listOf(getSectionInnerRange(marker, nextMarker, editor.document)))
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

