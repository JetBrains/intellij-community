package com.intellij.dev.psiViewer.properties.tree

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.checkCanceled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

class PsiViewerPropertyNodeHolder(
  val node: PsiViewerPropertyNode,
  private val nodeContext: PsiViewerPropertyNode.Context,
  private val depth: Int,
  private val scope: CoroutineScope
) {
  val childrenListAsync: Deferred<List<PsiViewerPropertyNodeHolder>> = childrenListAsync()

  private fun childrenListAsync(): Deferred<List<PsiViewerPropertyNodeHolder>> {
    return scope.async(Dispatchers.Default, start = CoroutineStart.LAZY) {
      checkCanceled()
      val mainChildren = async {
        when (val children = node.children) {
          is PsiViewerPropertyNode.Children.Enumeration -> {
            children.childrenList
          }
          is PsiViewerPropertyNode.Children.Async -> {
            if (depth > 100) emptyList() else children.computeChildren()
          }
        }
      }
      val additionalChildren: Deferred<List<PsiViewerPropertyNode>> = async {
        PsiViewerPropertyNodeAppender.EP_NAME.extensionList.flatMap { it.appendChildren(nodeContext, parent = node) }
      }
      val allChildren = mainChildren.await() + additionalChildren.await()
      allChildren.map { childNodeHolder(it) }
    }
  }

  private fun childNodeHolder(childNode: PsiViewerPropertyNode): PsiViewerPropertyNodeHolder {
    return PsiViewerPropertyNodeHolder(childNode, nodeContext, depth + 1, scope)
  }
}

interface PsiViewerPropertyNodeAppender {
  companion object {
    val EP_NAME = ExtensionPointName<PsiViewerPropertyNodeAppender>("com.intellij.dev.psiViewer.propertyNodeAppender")
  }

  suspend fun appendChildren(nodeContext: PsiViewerPropertyNode.Context, parent: PsiViewerPropertyNode): List<PsiViewerPropertyNode>
}