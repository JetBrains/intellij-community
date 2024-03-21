package com.intellij.dev.psiViewer.properties.tree

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.ui.SimpleColoredComponent


interface PsiViewerPropertyNode {
  data class Context(
    val project: Project,
    val showEmptyNodes: Boolean,
    val psiSelectorInMainTree: suspend (PsiElement) -> Runnable?
  )

  interface Factory {
    companion object {
      private val EP_NAME = ExtensionPointName<Factory>("com.intellij.dev.psiViewer.propertyNodeFactory")

      fun findMatchingFactory(clazz: Class<*>): Factory? {
        return EP_NAME.extensionList.find { it.isMatchingType(clazz) }
      }
    }

    fun isMatchingType(clazz: Class<*>): Boolean

    suspend fun createNode(nodeContext: Context, returnedValue: Any): PsiViewerPropertyNode?
  }

  sealed interface Children {
    class Enumeration(val childrenList: List<PsiViewerPropertyNode>) : Children

    fun interface Async : Children {
      suspend fun computeChildren(): List<PsiViewerPropertyNode>
    }

    companion object {
      val NoChildren = Enumeration(emptyList())
    }
  }

  val children: Children

  fun interface Presentation {
    fun build(component: SimpleColoredComponent)
  }

  val presentation: Presentation

  val weight: Int
}

val PsiViewerPropertyNode.isLeaf: Boolean
  get() = children.let { it is PsiViewerPropertyNode.Children.Enumeration && it.childrenList.isEmpty() }

fun PsiViewerPropertyNode.appendPresentation(otherPresentation: PsiViewerPropertyNode.Presentation): PsiViewerPropertyNode =
  addPresentation(otherPresentation, before = false)

fun PsiViewerPropertyNode.prependPresentation(otherPresentation: PsiViewerPropertyNode.Presentation): PsiViewerPropertyNode =
  addPresentation(otherPresentation, before = true)

fun PsiViewerPropertyNode.withWeight(weight: Int): PsiViewerPropertyNode {
  return object : PsiViewerPropertyNode by this {
    override val weight: Int = weight
  }
}

private fun PsiViewerPropertyNode.addPresentation(otherPresentation: PsiViewerPropertyNode.Presentation, before: Boolean): PsiViewerPropertyNode {
  return object : PsiViewerPropertyNode by this {
    override val presentation = PsiViewerPropertyNode.Presentation {
      if (before) {
        otherPresentation.build(it)
        it.append(" ")
        this@addPresentation.presentation.build(it)
      } else {
        this@addPresentation.presentation.build(it)
        it.append(" ")
        otherPresentation.build(it)
      }
    }
  }
}