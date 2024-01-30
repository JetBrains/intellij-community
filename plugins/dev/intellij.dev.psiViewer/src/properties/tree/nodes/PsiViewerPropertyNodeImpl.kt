package com.intellij.dev.psiViewer.properties.tree.nodes

import com.intellij.dev.psiViewer.properties.tree.PsiViewerPropertyNode

class PsiViewerPropertyNodeImpl(
  override val presentation: PsiViewerPropertyNode.Presentation,
  private val childrenList: List<PsiViewerPropertyNode>,
  override val weight: Int
) : PsiViewerPropertyNode {
  override val children: PsiViewerPropertyNode.Children
    get() = PsiViewerPropertyNode.Children.Enumeration(childrenList)
}