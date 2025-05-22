package com.intellij.dev.psiViewer.properties.tree.nodes.apiMethods

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.CancellationException

fun Class<*>.psiViewerApiMethods(instance: Any): List<PsiViewerApiMethod> {
  return PsiViewerApiMethod.Provider.EP_NAME.extensionList.flatMap { it.apiMethods(instance, this) }
}

fun interface PsiViewerApiMethodEvaluator {
  companion object {
    val default: PsiViewerApiMethodEvaluator = defaultPsiViewerApiMethodEvaluator()
  }

  suspend fun evaluate(method: PsiViewerApiMethod): Any?
}

private fun defaultPsiViewerApiMethodEvaluator() = object : PsiViewerApiMethodEvaluator {
  override suspend fun evaluate(method: PsiViewerApiMethod): Any? {
    return try {
      method.evaluator.invoke()
    }
    catch (ce : CancellationException) {
      throw ce
    }
    catch (e : Throwable) {
      if (e is ControlFlowException) {
        throw e
      }
      thisLogger().warn("Failed to evaluate method ${method.name}", e)
      null
    }
  }

}

class PsiViewerApiMethod(
  val name: String,
  val returnType: ReturnType,
  val evaluator: suspend () -> Any?
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

  override fun toString(): String {
    return "Method $name | $returnType"
  }
}