package com.intellij.dev.psiViewer.properties.tree.nodes

import com.intellij.dev.psiViewer.properties.tree.PsiViewerPropertyNode
import com.intellij.dev.psiViewer.properties.tree.appendPresentation
import com.intellij.dev.psiViewer.properties.tree.prependPresentation
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.checkCancelled
import com.intellij.ui.SimpleTextAttributes
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType

private const val NOT_EMPTY_LIST_VALUE_WEIGHT = 100
private const val NULL_VALUE_WEIGHT = 150
private const val EMPTY_LIST_VALUE_WEIGHT = 200

suspend fun methodReturnValuePsiViewerNode(
  value: Any?,
  methodName: String,
  methodReturnType: Class<*>,
  factory: PsiViewerPropertyNode.Factory,
  context: PsiViewerPropertyNode.Context,
): PsiViewerPropertyNode? {
  if (value == null) {
    if (!context.showEmptyNodes) return null

    return nullValueFromMethodNode(methodName, methodReturnType)
  }
  return factory.createNode(context, value)
    ?.appendPresentation(methodReturnTypePresentation(methodReturnType))
    ?.prependPresentation(methodNamePresentation(methodName))
}

private fun nullValueFromMethodNode(methodName: String, methodReturnType: Class<*>): PsiViewerPropertyNode {
  return PsiViewerPropertyNodeImpl(methodNamePresentation(methodName), emptyList(), NULL_VALUE_WEIGHT)
    .appendPresentation(nullValuePresentation())
    .appendPresentation(methodReturnTypePresentation(methodReturnType))
}

suspend fun computePsiViewerNodeByMethodCall(
  nodeContext: PsiViewerPropertyNode.Context,
  instance: Any?,
  method: Method,
  arguments: List<Any>
): PsiViewerPropertyNode? {
  suspend fun invokeMethod(): Any? {
    checkCancelled()
    return readAction {
      method.invoke(instance, *arguments.toTypedArray())
    }
  }

  val returnType = method.returnType
  val matchedNodeFactory = PsiViewerPropertyNode.Factory.findMatchingFactory(returnType)
  if (matchedNodeFactory != null) {
    return methodReturnValuePsiViewerNode(
      value = invokeMethod(),
      method.name,
      method.returnType,
      matchedNodeFactory,
      nodeContext
    )
  }

  val typeOfReturnedCollection = getTypeOfReturnedCollection(method) ?: return null

  val matchedNodeFactoryOfListType = PsiViewerPropertyNode.Factory.findMatchingFactory(typeOfReturnedCollection) ?: return null

  val returnedValue = invokeMethod() ?: return null
  val returnedList = (returnedValue as? Array<*>)?.toList() ?: (returnedValue as? Collection<*>) ?: return null

  val childrenNodes = coroutineScope {
    returnedList
      .asSequence()
      .filterNotNull()
      .mapIndexed { idx, value ->
        async {
          val idxPresentation = PsiViewerPropertyNode.Presentation {
            it.append("[$idx] =", SimpleTextAttributes.GRAYED_ATTRIBUTES)
          }
          matchedNodeFactoryOfListType.createNode(nodeContext, value)?.prependPresentation(idxPresentation)
        }
      }
      .toList()
      .awaitAll()
      .filterNotNull()
  }

  val returnedListPresentation = PsiViewerPropertyNode.Presentation {
    val attributes = if (childrenNodes.isEmpty()) SimpleTextAttributes.GRAYED_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES
    @Suppress("HardCodedStringLiteral")
    it.append("List[${childrenNodes.size}]", attributes)
  }

  val weight = if (childrenNodes.isEmpty()) EMPTY_LIST_VALUE_WEIGHT else NOT_EMPTY_LIST_VALUE_WEIGHT

  val node = PsiViewerPropertyNodeImpl(methodNamePresentation(method.name), childrenNodes, weight)
    .appendPresentation(returnedListPresentation)
    .appendPresentation(methodReturnTypePresentation(method.returnType))

  return if (childrenNodes.isNotEmpty() || nodeContext.showEmptyNodes) node else null
}

private fun getTypeOfReturnedCollection(method: Method): Class<*>? {
  val returnType = method.returnType
  if (returnType.isArray) {
    return returnType.componentType
  }

  val isCollectionReturnType = Collection::class.java.isAssignableFrom(returnType)
  if (!isCollectionReturnType) return null

  val genericType = method.genericReturnType
  if (genericType !is ParameterizedType) return null

  val actualTypeArguments = genericType.actualTypeArguments
  if (actualTypeArguments.size != 1) return null

  val firstTypeArgument = actualTypeArguments.firstOrNull() ?: return null
  return (firstTypeArgument as? Class<*>) ?: (firstTypeArgument as? WildcardType)?.upperBounds?.firstOrNull() as? Class<*>
}

suspend fun computePsiViewerApiClassesNodes(
  apiClasses: List<Class<*>>,
  instance: Any,
  nodeContext: PsiViewerPropertyNode.Context,
): List<PsiViewerPropertyNodeImpl> {
  return coroutineScope {
    val apiClassesNodes = apiClasses
      .mapIndexed { idx, apiClass ->
        async {
          val childrenNodesForApiClass = computePsiViewerPropertyNodesByCallingInstanceMethods(
            nodeContext,
            instance,
            apiClass.declaredApiMethods
          )
          if (childrenNodesForApiClass.isEmpty()) return@async null

          val presentation = PsiViewerPropertyNode.Presentation {
            it.icon = if (apiClass.isInterface) AllIcons.Nodes.Interface else AllIcons.Nodes.Class
            it.append(apiClass.canonicalName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
          }

          PsiViewerPropertyNodeImpl(presentation, childrenNodesForApiClass, weight = idx)
        }
      }
      .awaitAll()
      .filterNotNull()
      .toList()

    apiClassesNodes
  }
}

private suspend fun computePsiViewerPropertyNodesByCallingInstanceMethods(
  nodeContext: PsiViewerPropertyNode.Context,
  instance: Any,
  methods: List<Method>
): List<PsiViewerPropertyNode> {
  return coroutineScope {
    methods
      .map {
        async {
          computePsiViewerNodeByMethodCall(nodeContext, instance, it, emptyList())
        }
      }
      .awaitAll()
      .filterNotNull()
  }
}

fun Class<*>.psiViewerApiClassesExtending(base: Class<*>): List<Class<*>> {
  if (!base.isAssignableFrom(this)) {
    return emptyList()
  }
  val apiClasses = mutableSetOf<Class<*>>()
  if (this.isApiClass) {
    apiClasses.add(this)
  }

  val allClassesStack = ArrayDeque<Class<*>>()
  allClassesStack.addLast(this)

  while (allClassesStack.isNotEmpty()) {
    val clazz = allClassesStack.removeLast()
    val clazzParents = (listOfNotNull(clazz.superclass) + clazz.interfaces.toList())

    for (parentClazz in clazzParents.filter { base.isAssignableFrom(it) }) {
      if (parentClazz.isApiClass) {
        apiClasses.add(parentClazz)
      }
      allClassesStack.addLast(parentClazz)
    }
  }
  return apiClasses
    .toMutableList()
    .apply {
      // always put base in the end
      remove(base)
      add(base)
    }
}

private val Class<*>.isApiClass: Boolean
  get() {
    val canonicalName = canonicalName ?: return false
    return when {
      !Modifier.isPublic(modifiers) -> false
      canonicalName.contains("impl", ignoreCase = true) -> false
      canonicalName.contains("stub", ignoreCase = true) -> false
      else -> true
    }
  }
private val Class<*>.declaredApiMethods: List<Method>
  get() {
    return declaredMethods
      .filter {
        Modifier.isPublic(it.modifiers) && it.parameterCount == 0 && it.name != "hashCode"
      }
  }