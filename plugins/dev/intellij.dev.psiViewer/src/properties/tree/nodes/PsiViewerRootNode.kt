package com.intellij.dev.psiViewer.properties.tree.nodes

import com.intellij.dev.psiViewer.properties.tree.PsiViewerPropertyNode
import com.intellij.dev.psiViewer.properties.tree.nodes.apiMethods.PsiViewerApiMethod
import com.intellij.dev.psiViewer.properties.tree.withWeight
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class PsiViewerRootNode(
  private val nodeContext: PsiViewerPropertyNode.Context,
  override val presentation: PsiViewerPropertyNode.Presentation,
  val element: PsiElement,
  override val weight: Int = -1000,
) : PsiViewerPropertyNode {

  override val children = PsiViewerPropertyNode.Children.Async {
    coroutineScope {
      val psiReferenceResolveNode = async {
        psiReferenceResolveNode()
      }
      val psiElementApiClasses = element::class.java.psiViewerApiClassesExtending(PsiElement::class.java)
        .filter { "AST" !in it.name }
      val apiClassesNodes = computePsiViewerApiClassesNodes(psiElementApiClasses, element, nodeContext)

      return@coroutineScope listOfNotNull(psiReferenceResolveNode.await()) + apiClassesNodes
    }
  }

  private suspend fun psiReferenceResolveNode(): PsiViewerPropertyNode? {
    val resolved: PsiElement? = readAction {
      val result = element.reference?.resolve()
      if (result == element) return@readAction null
      return@readAction result
    }
    val node = methodReturnValuePsiViewerNode(
      value = resolved,
      methodName = "getReference()?.resolve",
      methodReturnType = PsiViewerApiMethod.ReturnType(PsiElement::class.java, returnedCollectionType = null),
      factory = PsiViewerPsiElementNode.Factory(),
      nodeContext
    ) ?: return null
    val weight = if (resolved != null) -100 else node.weight
    return node.withWeight(weight)
  }
}
