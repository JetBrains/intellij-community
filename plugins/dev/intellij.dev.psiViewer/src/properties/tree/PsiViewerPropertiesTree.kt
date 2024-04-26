package com.intellij.dev.psiViewer.properties.tree

import com.intellij.dev.psiViewer.PsiViewerDialog
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.BaseTreeModel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.concurrency.Invoker
import com.intellij.util.concurrency.InvokerSupplier
import javax.swing.JComponent
import javax.swing.JTree

class PsiViewerPropertiesTree(
  viewModel: PsiViewerPropertiesTreeViewModel,
  disposable: Disposable,
) {
  private val _component: Lazy<JComponent>
  val component: JComponent
    get() = _component.value

  init {
    _component = lazy {
      val treeModel = PsiViewerPropertiesTreeModel(viewModel.root, disposable)

      val tree = Tree(AsyncTreeModel(treeModel, disposable)).apply {
        PsiViewerDialog.initTree(this)
        cellRenderer = PsiViewerPropertiesTreeCellRenderer()
        isRootVisible = true
        showsRootHandles = true
      }

      object : TreeLinkMouseListener(PsiViewerPropertiesTreeCellRenderer()) {
        override fun doCacheLastNode(): Boolean = false
      }.installOn(tree)

      ScrollPaneFactory.createScrollPane(tree, true)
    }
  }
}

private class PsiViewerPropertiesTreeModel(
  private val rootNode: PsiViewerPropertyNodeHolder,
  parentDisposable: Disposable
) : BaseTreeModel<PsiViewerPropertyNodeHolder>(), InvokerSupplier {
  init {
    Disposer.register(parentDisposable, this)
  }

  private val invoker = Invoker.forBackgroundPoolWithoutReadAction(this)

  override fun getRoot(): Any = rootNode

  override fun getChildren(parent: Any?): List<PsiViewerPropertyNodeHolder> {
    assert(invoker.isValidThread) { "unexpected thread" }

    val parentNode = parent as? PsiViewerPropertyNodeHolder ?: return emptyList()
    val children = runBlockingCancellable {
      parentNode.childrenListAsync.await().sortedBy { it.node.weight }
    }
    return children
  }

  override fun getInvoker(): Invoker = invoker
}

private class PsiViewerPropertiesTreeCellRenderer : ColoredTreeCellRenderer() {

  override fun customizeCellRenderer(tree: JTree,
                                     value: Any?,
                                     selected: Boolean,
                                     expanded: Boolean,
                                     leaf: Boolean,
                                     row: Int,
                                     hasFocus: Boolean) {
    val nodeHolder = value as? PsiViewerPropertyNodeHolder ?: return
    nodeHolder.node.presentation.build(this)
  }
}