package com.intellij.dev.psiViewer.properties.tree.nodes

import com.intellij.dev.psiViewer.properties.tree.PsiViewerPropertyNode

class PsiViewerPrimitiveNode(private val primitive: Any) : PsiViewerPropertyNode {
  class Factory : PsiViewerPropertyNode.Factory {
    override fun isMatchingType(clazz: Class<*>): Boolean {
      return clazz == String::class.java ||
             (clazz.isPrimitive && clazz != Void.TYPE) ||
             clazz.isEnum
    }

    override suspend fun createNode(nodeContext: PsiViewerPropertyNode.Context, returnedValue: Any): PsiViewerPropertyNode {
      return PsiViewerPrimitiveNode(returnedValue)
    }
  }

  override val presentation: PsiViewerPropertyNode.Presentation
    get() = PsiViewerPropertyNode.Presentation {
      @Suppress("HardCodedStringLiteral")
      it.append(primitive.toString())
    }

  override val children = PsiViewerPropertyNode.Children.NoChildren

  override val weight: Int
    get() {
      return when (primitive) {
        is Boolean -> 0
        is Number -> 1
        is Enum<*> -> 2
        is String -> 3
        else -> 4
      }
    }
}