package com.intellij.dev.psiViewer.properties.tree.nodes.apiMethods

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.CancellationException

fun Class<*>.psiViewerApiMethods(instance: Any): List<PsiViewerApiMethod> {
  return PsiViewerApiMethod.Provider.EP_NAME.extensionList.flatMap { it.apiMethods(instance, this) }
}

class PsiViewerApiMethod(
  val name: String,
  val returnType: ReturnType,
  private val evaluator: suspend () -> Any?
) {
  class ReturnType(
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
}