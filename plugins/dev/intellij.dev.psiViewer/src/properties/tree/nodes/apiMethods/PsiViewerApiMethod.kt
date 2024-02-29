package com.intellij.dev.psiViewer.properties.tree.nodes.apiMethods

import com.intellij.openapi.extensions.ExtensionPointName

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
    return evaluator.invoke()
  }
}