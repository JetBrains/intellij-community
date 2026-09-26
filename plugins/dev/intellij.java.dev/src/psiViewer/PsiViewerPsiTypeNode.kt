// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.dev.psiViewer

import com.intellij.dev.psiViewer.properties.tree.PsiViewerPropertyNode
import com.intellij.dev.psiViewer.properties.tree.nodes.computePsiViewerApiClassesNodes
import com.intellij.dev.psiViewer.properties.tree.nodes.psiViewerApiClassesExtending
import com.intellij.dev.psiViewer.properties.tree.nodes.psiViewerPsiTypeAttributes
import com.intellij.psi.PsiType

private class PsiViewerPsiTypeNode(
  private val psiType: PsiType,
  private val nodeContext: PsiViewerPropertyNode.Context
) : PsiViewerPropertyNode {
  class Factory : PsiViewerPropertyNode.Factory {
    override fun isMatchingType(clazz: Class<*>): Boolean = PsiType::class.java.isAssignableFrom(clazz)

    override suspend fun createNode(nodeContext: PsiViewerPropertyNode.Context, returnedValue: Any): PsiViewerPropertyNode? {
      val psiType = returnedValue as? PsiType ?: return null
      return PsiViewerPsiTypeNode(psiType, nodeContext)
    }
  }

  override val presentation = PsiViewerPropertyNode.Presentation {
    @Suppress("HardCodedStringLiteral")
    it.append(psiType.toString(), psiViewerPsiTypeAttributes())
  }

  override val children = PsiViewerPropertyNode.Children.Async {
    val psiTypeApiClasses = psiType::class.java.psiViewerApiClassesExtending(PsiType::class.java)
    computePsiViewerApiClassesNodes(psiTypeApiClasses, psiType, nodeContext)
  }

  override val weight: Int = 25
}