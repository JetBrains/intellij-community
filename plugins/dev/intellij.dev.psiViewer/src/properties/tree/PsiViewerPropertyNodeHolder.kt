package com.intellij.dev.psiViewer.properties.tree

import com.intellij.openapi.progress.checkCancelled
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener
import kotlinx.coroutines.*

class PsiViewerPropertyNodeHolder(
  val node: PsiViewerPropertyNode,
  private val depth: Int,
  private val scope: CoroutineScope
) : TreeLinkMouseListener.IsLeafProvider {
  val childrenListAsync: Deferred<List<PsiViewerPropertyNodeHolder>> = childrenListAsync()

  private fun childrenListAsync(): Deferred<List<PsiViewerPropertyNodeHolder>> {
    return when(val children = node.children) {
      is PsiViewerPropertyNode.Children.Enumeration -> {
        CompletableDeferred(children.childrenList.map { childNodeHolder(it) })
      }
      is PsiViewerPropertyNode.Children.Async -> {
        scope.async(Dispatchers.Default, start = CoroutineStart.LAZY) {
          checkCancelled()
          if (depth > 100) return@async emptyList()

          children.computeChildren().map { childNodeHolder(it) }
        }
      }
    }
  }

  private fun childNodeHolder(childNode: PsiViewerPropertyNode): PsiViewerPropertyNodeHolder {
    return PsiViewerPropertyNodeHolder(childNode, depth + 1, scope)
  }

  override fun isLeaf(): Boolean = node.isLeaf
}