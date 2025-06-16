package com.intellij.dev.psiViewer.properties.tree

import com.intellij.dev.psiViewer.properties.tree.nodes.apiMethods.PsiViewerApiMethod
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.SimpleColoredComponent


interface PsiViewerPropertyNode {
  data class Context(
    val project: Project,
    val rootPsiFile: PsiFile?,
    val showEmptyNodes: Boolean,
    val apiMethodProviders: List<PsiViewerApiMethod.Provider>,
    val psiSelectorInMainTree: suspend (PsiElement) -> Runnable?,

    val currentDepth: Depth,
    val depthLimit: Depth,
  ) {
    data class Depth(
      val totalDepth: Int,
      val notPsiApiDepth: Int,
      val otherPsiFileApiDepth: Int,
    )

    fun incrementDepth(isNotPsiApiEntered: Boolean, isOtherPsiFileApiEntered: Boolean): Context? {
      val newTotalDepth = currentDepth.totalDepth + 1
      val newNotPsiDepth = if (isNotPsiApiEntered || currentDepth.notPsiApiDepth != 0) {
        currentDepth.notPsiApiDepth + 1
      } else {
        currentDepth.notPsiApiDepth
      }
      val newOtherPsiDepth = if (isOtherPsiFileApiEntered || currentDepth.otherPsiFileApiDepth != 0) {
        currentDepth.otherPsiFileApiDepth + 1
      } else {
        currentDepth.otherPsiFileApiDepth
      }

      if (newTotalDepth > depthLimit.totalDepth ||
          newNotPsiDepth > depthLimit.notPsiApiDepth ||
          newOtherPsiDepth > depthLimit.otherPsiFileApiDepth) {
        return null
      }
      val newCurrentDepth = Depth(newTotalDepth, newNotPsiDepth, newOtherPsiDepth)
      return copy(currentDepth = newCurrentDepth)
    }
  }

  interface Factory {
    companion object {
      private val EP_NAME = ExtensionPointName<Factory>("com.intellij.dev.psiViewer.propertyNodeFactory")

      fun findMatchingFactory(clazz: Class<*>): Factory? {
        return EP_NAME.extensionList.find { it.isMatchingType(clazz) }?.depthAware()
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

  val apiClass: Class<*>?
    get() = null

  val apiMethod: PsiViewerApiMethod?
    get() = null
}

fun PsiViewerPropertyNode.appendPresentation(otherPresentation: PsiViewerPropertyNode.Presentation): PsiViewerPropertyNode =
  addPresentation(otherPresentation, before = false)

fun PsiViewerPropertyNode.prependPresentation(otherPresentation: PsiViewerPropertyNode.Presentation): PsiViewerPropertyNode =
  addPresentation(otherPresentation, before = true)

fun PsiViewerPropertyNode.withWeight(weight: Int): PsiViewerPropertyNode {
  return object : PsiViewerPropertyNode by this {
    override val weight: Int = weight
  }
}

fun PsiViewerPropertyNode.withApiClass(apiClass: Class<*>): PsiViewerPropertyNode {
  return object : PsiViewerPropertyNode by this {
    override val apiClass: Class<*> = apiClass
  }
}

fun PsiViewerPropertyNode.withApiMethod(apiMethod: PsiViewerApiMethod): PsiViewerPropertyNode {
  return object : PsiViewerPropertyNode by this {
    override val apiMethod: PsiViewerApiMethod = apiMethod
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

private fun PsiViewerPropertyNode.Factory.depthAware(): PsiViewerPropertyNode.Factory {
  val original = this
  return object : PsiViewerPropertyNode.Factory by original {
    override suspend fun createNode(nodeContext: PsiViewerPropertyNode.Context, returnedValue: Any): PsiViewerPropertyNode? {
      val isNotPsiApiEntered = returnedValue !is PsiElement
      val isOtherPsiFileApiEntered = returnedValue is PsiElement && returnedValue.containingFileSafe()?.name != nodeContext.rootPsiFile?.name

      val newContext = nodeContext.incrementDepth(isNotPsiApiEntered, isOtherPsiFileApiEntered) ?: return null
      return original.createNode(newContext, returnedValue)
    }
  }
}

private suspend fun PsiElement.containingFileSafe(): PsiFile? {
  return readAction {
    return@readAction try {
      containingFile
    }
    catch (e : Throwable) {
      if (e is ControlFlowException) {
        throw e
      }
      thisLogger().warn(e)
      null
    }
  }
}