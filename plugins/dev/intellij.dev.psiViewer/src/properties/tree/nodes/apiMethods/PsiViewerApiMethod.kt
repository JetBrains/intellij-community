package com.intellij.dev.psiViewer.properties.tree.nodes.apiMethods

import com.intellij.dev.psiViewer.properties.tree.PsiViewerPropertyNode
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.CancellationException

fun Class<*>.psiViewerApiMethods(nodeContext: PsiViewerPropertyNode.Context, instance: Any): List<PsiViewerApiMethod> {
  return nodeContext.apiMethodProviders.flatMap { it.apiMethods(instance, this) }
}

class PsiViewerApiMethod(
  val name: String,
  val returnType: ReturnType,
  private val evaluator: suspend () -> Any?
) {
  data class ReturnType(
    val returnType: Class<*>,
    val returnedCollectionType: Class<*>?,
  )

  interface Provider {
    companion object {
      val EP_NAME = ExtensionPointName<Provider>("com.intellij.dev.psiViewer.apiMethodsProvider")
    }

    fun apiMethods(instance: Any, clazz: Class<*>): List<PsiViewerApiMethod>
  }

  suspend fun invoke(): Any? {
    return try {
      evaluator.invoke()
    }
    catch (ce : CancellationException) {
      throw ce
    }
    catch (e : Throwable) {
      if (e is ControlFlowException) {
        throw e
      }
      thisLogger().warn("Failed to evaluate method $name", e)
      null
    }
  }

  override fun toString(): String {
    return "Method $name | $returnType"
  }
}