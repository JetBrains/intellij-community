package com.intellij.dev.psiViewer.properties.tree

import com.intellij.dev.psiViewer.DevPsiViewerBundle
import com.intellij.dev.psiViewer.properties.tree.nodes.PsiViewerRootNode
import com.intellij.psi.PsiElement
import com.intellij.ui.SimpleTextAttributes
import kotlinx.coroutines.CoroutineScope

class PsiViewerPropertiesTreeViewModel(
  rootElement: PsiElement,
  private val rootElementString: String,
  val scope: CoroutineScope,
  nodeContext: PsiViewerPropertyNode.Context,
) {
  val rootNode = PsiViewerRootNode(
    nodeContext,
    PsiViewerPropertyNode.Presentation {
      @Suppress("HardCodedStringLiteral")
      it.append(rootElementString)
      it.append(" ")
      it.append(DevPsiViewerBundle.message("properties.tree.root.description"), SimpleTextAttributes.GRAYED_ATTRIBUTES)
    },
    rootElement
  )

  val root = PsiViewerPropertyNodeHolder(
    rootNode,
    nodeContext,
    depth = 0,
    scope
  )
}