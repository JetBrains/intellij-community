// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.dev.kotlin.internal

import com.intellij.dev.psiViewer.properties.tree.PsiViewerPropertyNode
import com.intellij.dev.psiViewer.properties.tree.nodes.computePsiViewerApiClassesNodes
import org.jetbrains.kotlin.types.KotlinType

internal class PsiViewerKotlinTypeNode(
  private val kotlinType: KotlinType,
  private val nodeContext: PsiViewerPropertyNode.Context
) : PsiViewerPropertyNode {
  class Factory : PsiViewerPropertyNode.Factory {
    override fun isMatchingType(clazz: Class<*>): Boolean = KotlinType::class.java.isAssignableFrom(clazz)

    override suspend fun createNode(nodeContext: PsiViewerPropertyNode.Context, returnedValue: Any): PsiViewerPropertyNode? {
      val kotlinType = returnedValue as? KotlinType ?: return null
      return PsiViewerKotlinTypeNode(kotlinType, nodeContext)
    }
  }

  override val presentation = PsiViewerPropertyNode.Presentation {
    it.append(kotlinType.toString())
  }

  override val children = PsiViewerPropertyNode.Children.Async {
    computePsiViewerApiClassesNodes(listOf(KotlinType::class.java), kotlinType, nodeContext)
  }

  override val weight: Int
    get() = 20
}










