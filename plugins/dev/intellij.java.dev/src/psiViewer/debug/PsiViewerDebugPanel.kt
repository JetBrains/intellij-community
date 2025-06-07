// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.dev.psiViewer.debug

import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.ui.impl.watch.NodeDescriptorProvider
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.dev.psiViewer.PsiViewerDialog
import com.intellij.dev.psiViewer.PsiViewerSettings
import com.intellij.dev.psiViewer.ViewerNodeDescriptor
import com.intellij.dev.psiViewer.ViewerTreeStructure
import com.intellij.icons.AllIcons
import com.intellij.ide.util.treeView.IndexComparator
import com.intellij.java.dev.JavaDevBundle
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
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
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.text.DateFormatUtil
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.sun.jdi.ObjectReference
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode

private val LOG = Logger.getInstance(PsiViewerDebugAction::class.java)

class PsiViewerDebugPanel(
  private val project: Project,
  private val editor: EditorEx,
  private val language: Language,
  private val expression: @NlsSafe String,
  private val debugSession: XDebugSession,
  private val fileName: String? = null
) : JPanel(BorderLayout()), Disposable {
  private var watchMode = PsiViewerDebugSettings.getInstance().watchMode

  private var showWhiteSpace = PsiViewerSettings.getSettings().showWhiteSpaces

  private var showTreeNodes = PsiViewerSettings.getSettings().showTreeNodes

  private val treeStructure = ViewerTreeStructure(project).apply {
    val settings = PsiViewerSettings.getSettings()
    setShowWhiteSpaces(settings.showWhiteSpaces)
    setShowTreeNodes(settings.showTreeNodes)
  }

  private val structureTreeModel = StructureTreeModel(treeStructure, IndexComparator.getInstance(), this)

  private val psiTree = Tree()

  private var expressionRange = TextRange.create(editor.selectionModel.selectionStart, editor.selectionModel.selectionEnd)

  private val watchListener = DebugWatchSessionListener()

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
      add(WatchModeAction())
      add(ResetSelection())
      add(ShowWhiteSpaceAction())
      add(ShowTreeNodesAction())
    }
    val toolbar = ActionManager.getInstance().createActionToolbar("PsiDump", toolBarActions, false)
    toolbar.targetComponent = psiTree
    return toolbar.component
  }

  private inner class OpenDialogAction : AnAction(
    JavaDevBundle.message("psi.viewer.show.open.dialog.action"),
    JavaDevBundle.message("psi.viewer.show.open.dialog.description"),
    AllIcons.ToolbarDecorator.Export
  ) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
      val dialog = PsiViewerDialog(project, editor)
      dialog.show()
    }
  }

  private inner class WatchModeAction : ToggleAction(
    JavaDevBundle.message("psi.viewer.toggle.watch.mode.action"),
    JavaDevBundle.message("psi.viewer.toggle.watch.mode.description"),
    AllIcons.Debugger.Watch,
  ) {
    init {
      if (PsiViewerDebugSettings.getInstance().watchMode) {
        debugSession.addSessionListener(watchListener)
      }
      else {
        debugSession.removeSessionListener(watchListener)
      }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean = watchMode

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      val runnerLayout = debugSession.ui
      if (runnerLayout == null) {
        // TODO [Debugger.RunnerLayoutUi]
        return
      }
      val content = runnerLayout.contentManager.getContent(this@PsiViewerDebugPanel)
      watchMode = state
      content.displayName = getTitle(expression, state)
      PsiViewerDebugSettings.getInstance().watchMode = state
      if (state) {
        refreshPanel()
        debugSession.addSessionListener(watchListener)
      }
      else {
        debugSession.removeSessionListener(watchListener)
      }
    }
  }

  inner class DebugWatchSessionListener : XDebugSessionListener {
    override fun sessionPaused() {
      refreshPanel()
    }
  }

  private fun refreshPanel() {
    val debugProcess = (debugSession.debugProcess as? JavaDebugProcess)?.debuggerSession?.process
    val suspendContext = debugProcess?.suspendManager?.getPausedContext()
    debugProcess?.managerThread?.schedule(object : SuspendContextCommandImpl(suspendContext) {
      override fun contextAction(suspendContext: SuspendContextImpl) {
        val evalContext = EvaluationContextImpl(suspendContext, suspendContext.frameProxy)
        val evaluator = debugSession.debugProcess.evaluator ?: return
        evaluator.evaluate(expression, object : XDebuggerEvaluator.XEvaluationCallback {
          override fun errorOccurred(errorMessage: String) {
            XDebuggerManagerImpl.getNotificationGroup().createNotification(
              JavaDevBundle.message("psi.viewer.debug.evaluation.failed"),
              NotificationType.ERROR,
            )
            LOG.warn("Failed to evaluate PSI expression")
          }

          override fun evaluated(result: XValue) {
            try {
              val descriptor = (result as? NodeDescriptorProvider)?.descriptor as? ValueDescriptorImpl ?: return
              val psiElemObj = descriptor.value as? ObjectReference ?: return
              val psiFileObj = debugProcess.invokeMethod(psiElemObj, GET_CONTAINING_FILE, evalContext) as? ObjectReference ?: return
              val fileText = psiFileObj.getText(debugProcess, evalContext) ?: return
              val psiRangeInFile = psiElemObj.getTextRange(debugProcess, evalContext) ?: return
              DebuggerUIUtil.invokeLater {
                expressionRange = psiRangeInFile
                editor.document.setReadOnly(false)
                try {
                  runWriteAction {
                    editor.document.setText(fileText)
                  }
                }
                finally {
                  editor.document.setReadOnly(true)
                }
                editor.selectAndScroll(psiRangeInFile)
              }
            }
            catch (e: EvaluateException) {
              XDebuggerManagerImpl.getNotificationGroup().createNotification(
                JavaDevBundle.message("psi.viewer.debug.evaluation.failed"),
                NotificationType.ERROR,
              )
              LOG.warn("Failed to evaluate PSI expression", e)
            }

          }
        }, debugSession.currentPosition)
      }
    })
  }

  private inner class ResetSelection : AnAction(
    JavaDevBundle.message("psi.viewer.show.reset.selection.action"),
    JavaDevBundle.message("psi.viewer.show.reset.selection.description"),
    AllIcons.General.Reset
  ) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
      if (editor.contentComponent.hasFocus()) editor.selectAndScroll(expressionRange)
      updatePsiTreeForSelection(expressionRange.startOffset, expressionRange.endOffset)
    }
  }


  private inner class ShowWhiteSpaceAction : ToggleAction(
    JavaDevBundle.message("psi.viewer.show.whitespace.action"),
    JavaDevBundle.message("psi.viewer.show.whitespace.description"),
    AllIcons.Diff.GutterCheckBox
  ) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean = showWhiteSpace

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      showWhiteSpace = state
      PsiViewerSettings.getSettings().showWhiteSpaces = state
      treeStructure.setShowWhiteSpaces(state)
      structureTreeModel.invalidateAsync()
    }
  }

  private inner class ShowTreeNodesAction : ToggleAction(
    JavaDevBundle.message("psi.viewer.show.tree.nodes.action"),
    JavaDevBundle.message("psi.viewer.show.tree.nodes.description"),
    AllIcons.Actions.PrettyPrint
  ) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean = showTreeNodes

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      showTreeNodes = state
      PsiViewerSettings.getSettings().showTreeNodes = state
      treeStructure.setShowTreeNodes(state)
      structureTreeModel.invalidateAsync()
    }
  }

  private fun initPsiTree(): JComponent? {
    val asyncTreeModel = AsyncTreeModel(structureTreeModel, this)
    psiTree.setModel(asyncTreeModel)

    val fileType = language.associatedFileType ?: return null
    val fileName = fileName ?: "Dummy.${fileType.defaultExtension}"
    treeStructure.rootPsiElement = PsiFileFactory.getInstance(project).createFileFromText(fileName, fileType, editor.document.text)

    psiTree.addTreeSelectionListener(PsiTreeSelectionListener())

    PsiViewerDialog.initTree(psiTree)
    updatePsiTreeForSelection(expressionRange.startOffset, expressionRange.endOffset)
    return JBScrollPane(psiTree)
  }

  private fun EditorEx.selectAndScroll(textRange: TextRange) {
    selectionModel.setSelection(textRange.startOffset, textRange.endOffset)
    caretModel.moveToOffset(textRange.startOffset)
    scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
  }

  private inner class PsiTreeSelectionListener : TreeSelectionListener {
    override fun valueChanged(e: TreeSelectionEvent) {
      if (!psiTree.hasFocus()) return
      val path = psiTree.selectionPath ?: return
      val selectedNode = path.lastPathComponent as DefaultMutableTreeNode
      val nodeDescriptor = selectedNode.userObject as? ViewerNodeDescriptor ?: return
      val element = nodeDescriptor.element as? PsiElement ?: return
      val elementRange = InjectedLanguageManager.getInstance(project).injectedToHost(element, element.getTextRange())
      editor.selectAndScroll(elementRange)
    }
  }

  private fun initEditor(): EditorEx? {
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
    debugSession.removeSessionListener(watchListener)
  }

  internal companion object {
    fun getTitle(name: @Nls String, inWatchMode: Boolean): @Nls String {
      return if (inWatchMode) name else "$name ${DateFormatUtil.formatTimeWithSeconds(System.currentTimeMillis())}"
    }
  }
}