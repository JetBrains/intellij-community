package com.intellij.dev.psiViewer.properties.tree.nodes

import com.intellij.dev.psiViewer.properties.tree.PsiViewerPropertyNode
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiElement
import com.intellij.ui.SimpleTextAttributes

private const val PSI_ELEMENT_NODE_WEIGHT = 50

class PsiViewerPsiElementNode(
  private val nodeContext: PsiViewerPropertyNode.Context,
  override val presentation: PsiViewerPropertyNode.Presentation,
  val psiElement: PsiElement
) : PsiViewerPropertyNode {
  class Factory : PsiViewerPropertyNode.Factory {
    override fun isMatchingType(clazz: Class<*>): Boolean {
      return PsiElement::class.java.isAssignableFrom(clazz)
    }

    override suspend fun createNode(nodeContext: PsiViewerPropertyNode.Context, returnedValue: Any): PsiViewerPropertyNode? {
      val psiElement = returnedValue as? PsiElement ?: return null
      @Suppress("HardCodedStringLiteral") val psiElementString = readAction {
        psiElement.toString()
      }
      val psiElementSelectorInMainTree = nodeContext.psiSelectorInMainTree(psiElement)
      val presentation = PsiViewerPropertyNode.Presentation {
        if (psiElementSelectorInMainTree != null) {
          it.append(psiElementString, SimpleTextAttributes.LINK_ATTRIBUTES, psiElementSelectorInMainTree)
        } else {
          it.append(psiElementString)
        }
      }
      return PsiViewerPsiElementNode(nodeContext, presentation, psiElement)
    }
  }

  override val children: PsiViewerPropertyNode.Children.Async = asRootNode().children

  override val weight: Int = PSI_ELEMENT_NODE_WEIGHT

  private fun asRootNode(): PsiViewerRootNode {
    return PsiViewerRootNode(
      nodeContext,
      PsiViewerPropertyNode.Presentation {  },
      psiElement
    )
  }
}