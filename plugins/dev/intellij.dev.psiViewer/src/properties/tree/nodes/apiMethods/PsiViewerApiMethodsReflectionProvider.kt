package com.intellij.dev.psiViewer.properties.tree.nodes.apiMethods

import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.checkCancelled
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType

class PsiViewerApiMethodsReflectionProvider : PsiViewerApiMethod.Provider {
  override fun apiMethods(instance: Any, clazz: Class<*>): List<PsiViewerApiMethod> {
    return clazz.declaredMethods
      .filter {
        Modifier.isPublic(it.modifiers) && it.parameterCount == 0 && it.name != "hashCode"
      }
      .map {
        fromReflectionMethod(instance, it)
      }
  }

  private fun fromReflectionMethod(instance: Any, method: Method): PsiViewerApiMethod {
    return PsiViewerApiMethod(method.name, method.returnType()) {
      checkCancelled()
      return@PsiViewerApiMethod readAction {
        method.invoke(instance)
      }
    }
  }

  private fun Method.returnType(): PsiViewerApiMethod.ReturnType {
    val noCollectionReturnTypeDescriptor = PsiViewerApiMethod.ReturnType(returnType, null)

    if (returnType.isArray) {
      return PsiViewerApiMethod.ReturnType(returnType, returnType.componentType)
    }

    val isCollectionReturnType = Collection::class.java.isAssignableFrom(returnType)
    if (!isCollectionReturnType) return noCollectionReturnTypeDescriptor

    val genericType = genericReturnType
    if (genericType !is ParameterizedType) return noCollectionReturnTypeDescriptor

    val actualTypeArguments = genericType.actualTypeArguments
    if (actualTypeArguments.size != 1) return noCollectionReturnTypeDescriptor

    val firstTypeArgument = actualTypeArguments.firstOrNull() ?: return noCollectionReturnTypeDescriptor
    val collectionType = (firstTypeArgument as? Class<*>) ?: (firstTypeArgument as? WildcardType)?.upperBounds?.firstOrNull() as? Class<*>
    return PsiViewerApiMethod.ReturnType(returnType, collectionType)
  }
}