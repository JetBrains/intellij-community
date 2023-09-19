// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.devkit.psiViewer.debug

import com.intellij.dev.psiViewer.PsiViewerDialog
import com.intellij.dev.psiViewer.PsiViewerSettings
import com.intellij.dev.psiViewer.ViewerNodeDescriptor
import com.intellij.dev.psiViewer.ViewerTreeStructure
import com.intellij.icons.AllIcons
import com.intellij.ide.util.treeView.IndexComparator
import com.intellij.java.devkit.psiViewer.JavaPsiViewerBundle
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode

class PsiViewerDebugPanel(
  private val project: Project,
  private val editor: EditorEx,
  private val language: Language
) : JPanel(BorderLayout()), Disposable {
  private val treeStructure = ViewerTreeStructure(project).apply {
    val settings = PsiViewerSettings.getSettings()
    setShowWhiteSpaces(settings.showWhiteSpaces)
    setShowTreeNodes(settings.showTreeNodes)
  }

  private val structureTreeModel = StructureTreeModel(treeStructure, IndexComparator.INSTANCE, this)

  private val psiTree = Tree()

  private val initialRange = TextRange.create(editor.selectionModel.selectionStart, editor.selectionModel.selectionEnd)

  init {
    val toolBar = initToolbar()
    add(toolBar, BorderLayout.WEST)
    val splitter = Splitter(false, 0.3f).apply {
      firstComponent = initPsiTree()
      secondComponent = initEditor()?.component
    }
    add(splitter, BorderLayout.CENTER)
  }

  private fun initToolbar(): JComponent {
    val toolBarActions = DefaultActionGroup().apply {
      add(OpenDialogAction())
      add(ResetSelection())
      add(ShowWhiteSpaceAction())
      add(ShowTreeNodesAction())
    }
    val toolbar = ActionManager.getInstance().createActionToolbar("PsiDump", toolBarActions, false)
    toolbar.targetComponent = psiTree
    return toolbar.component
  }

  private inner class OpenDialogAction : AnAction(
    JavaPsiViewerBundle.message("psi.viewer.show.open.dialog.action"),
    JavaPsiViewerBundle.message("psi.viewer.show.open.dialog.description"),
    AllIcons.ToolbarDecorator.Export
  ) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
      val dialog = PsiViewerDialog(project, editor)
      dialog.show()
    }
  }

  private inner class ResetSelection : AnAction(
    JavaPsiViewerBundle.message("psi.viewer.show.reset.selection.action"),
    JavaPsiViewerBundle.message("psi.viewer.show.reset.selection.description"),
    AllIcons.General.Reset
  ) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
      if (editor.contentComponent.hasFocus()) editor.selectionModel.setSelection(initialRange.startOffset, initialRange.endOffset)
      updatePsiTreeForSelection(initialRange.startOffset, initialRange.endOffset)
    }
  }


  private inner class ShowWhiteSpaceAction : ToggleAction(
    JavaPsiViewerBundle.message("psi.viewer.show.whitespace.action"),
    JavaPsiViewerBundle.message("psi.viewer.show.whitespace.description"),
    AllIcons.Diff.GutterCheckBox
  ) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean = PsiViewerSettings.getSettings().showWhiteSpaces

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      PsiViewerSettings.getSettings().showWhiteSpaces = state
      treeStructure.setShowWhiteSpaces(state)
      structureTreeModel.invalidateAsync()
    }
  }

  private inner class ShowTreeNodesAction : ToggleAction(
    JavaPsiViewerBundle.message("psi.viewer.show.tree.nodes.action"),
    JavaPsiViewerBundle.message("psi.viewer.show.tree.nodes.description"),
    AllIcons.Actions.PrettyPrint
  ) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean = PsiViewerSettings.getSettings().showTreeNodes

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      PsiViewerSettings.getSettings().showTreeNodes = state
      treeStructure.setShowTreeNodes(state)
      structureTreeModel.invalidateAsync()
    }
  }

  private fun initPsiTree(): JComponent? {
    val asyncTreeModel = AsyncTreeModel(structureTreeModel, this)
    psiTree.setModel(asyncTreeModel)

    val fileType = language.associatedFileType ?: return null
    treeStructure.rootPsiElement = PsiFileFactory.getInstance(project).createFileFromText(
      "Dummy." + fileType.defaultExtension, fileType, editor.document.text
    )

    psiTree.addTreeSelectionListener(PsiTreeSelectionListener())

    PsiViewerDialog.initTree(psiTree)
    updatePsiTreeForSelection(initialRange.startOffset, initialRange.endOffset)
    return JBScrollPane(psiTree)
  }

  private inner class PsiTreeSelectionListener : TreeSelectionListener {
    override fun valueChanged(e: TreeSelectionEvent) {
      if (!psiTree.hasFocus()) return
      val path = psiTree.selectionPath ?: return
      val selectedNode = path.lastPathComponent as DefaultMutableTreeNode
      val nodeDescriptor = selectedNode.userObject as? ViewerNodeDescriptor ?: return
      val element = nodeDescriptor.element as? PsiElement ?: return
      val elementRange = InjectedLanguageManager.getInstance(project).injectedToHost(element, element.getTextRange())
      editor.selectionModel.setSelection(elementRange.startOffset, elementRange.endOffset)
      editor.caretModel.moveToOffset(elementRange.startOffset)
      editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }
  }

  private fun initEditor(): EditorEx?  {
    val fileType = language.associatedFileType ?: return null
    val editorHighlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, fileType)
    editor.highlighter = editorHighlighter
    editor.document.setReadOnly(true)
    val editorListener = EditorListener()
    editor.caretModel.addCaretListener(editorListener)
    editor.selectionModel.addSelectionListener(editorListener)
    return editor
  }

  private inner class EditorListener : SelectionListener, CaretListener {
    override fun caretPositionChanged(event: CaretEvent) {
      if (!editor.contentComponent.hasFocus()) return
      if (editor.getSelectionModel().hasSelection()) return
      val rootElement = treeStructure.rootPsiElement as? PsiFile ?: return
      val offset = event.caret?.offset ?: return
      val element = rootElement.findElementAt(offset) ?: return
      structureTreeModel.select(element, psiTree) { }
    }

    override fun selectionChanged(e: SelectionEvent) {
      if (!editor.contentComponent.hasFocus()) return
      val startOffset = e.newRange.startOffset
      val endOffset = e.newRange.endOffset
      updatePsiTreeForSelection(startOffset, endOffset)
    }
  }

  private fun updatePsiTreeForSelection(startOffset: Int, endOffset: Int) {
    val rootElement = treeStructure.rootPsiElement as? PsiFile ?: return
    val startElement = rootElement.findElementAt(startOffset) ?: return
    val endElement = rootElement.findElementAt(endOffset - 1) ?: return
    val commonElement = PsiTreeUtil.findCommonParent(startElement, endElement) ?: return
    structureTreeModel.select(commonElement, psiTree) { }
  }

  override fun dispose() {
    if (!editor.isDisposed()) EditorFactory.getInstance().releaseEditor(editor)
  }
}