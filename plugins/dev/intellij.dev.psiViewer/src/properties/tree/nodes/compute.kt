package com.intellij.dev.psiViewer.properties.tree.nodes

import com.intellij.dev.psiViewer.properties.tree.*
import com.intellij.dev.psiViewer.properties.tree.nodes.apiMethods.PsiViewerApiMethod
import com.intellij.dev.psiViewer.properties.tree.nodes.apiMethods.psiViewerApiMethods
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.ui.SimpleTextAttributes
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.lang.reflect.Modifier

private const val NOT_EMPTY_LIST_VALUE_WEIGHT = 100
private const val NULL_VALUE_WEIGHT = 150
private const val EMPTY_LIST_VALUE_WEIGHT = 200

suspend fun methodReturnValuePsiViewerNode(
  value: Any?,
  methodName: String,
  methodReturnType: PsiViewerApiMethod.ReturnType,
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

private fun nullValueFromMethodNode(methodName: String, methodReturnType: PsiViewerApiMethod.ReturnType): PsiViewerPropertyNode {
  return PsiViewerPropertyNodeImpl(methodNamePresentation(methodName), emptyList(), NULL_VALUE_WEIGHT)
    .appendPresentation(nullValuePresentation())
    .appendPresentation(methodReturnTypePresentation(methodReturnType))
}

private suspend fun computePsiViewerNodeByMethodCall(
  nodeContext: PsiViewerPropertyNode.Context,
  psiViewerApiMethod: PsiViewerApiMethod,
): PsiViewerPropertyNode? {
  nodeContext.project.waitForSmartMode()

  val returnType = psiViewerApiMethod.returnType.returnType
  val matchedNodeFactory = PsiViewerPropertyNode.Factory.findMatchingFactory(returnType)
  if (matchedNodeFactory != null) {
    return methodReturnValuePsiViewerNode(
      value = psiViewerApiMethod.invoke(),
      psiViewerApiMethod.name,
      psiViewerApiMethod.returnType,
      matchedNodeFactory,
      nodeContext
    )?.withApiMethod(psiViewerApiMethod)
  }

  val returnedCollectionType = psiViewerApiMethod.returnType.returnedCollectionType ?: return null
  val matchedNodeFactoryOfCollectionType = PsiViewerPropertyNode.Factory.findMatchingFactory(returnedCollectionType) ?: return null

  val returnedValue = psiViewerApiMethod.invoke() ?: return null
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
          matchedNodeFactoryOfCollectionType.createNode(nodeContext, value)?.prependPresentation(idxPresentation)
        }
      }
      .toList()
      .awaitAll()
      .filterNotNull()
  }

  val returnedListPresentation = PsiViewerPropertyNode.Presentation {
    val attributes = if (childrenNodes.isEmpty()) SimpleTextAttributes.GRAYED_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES
    @Suppress("HardCodedStringLiteral")
    it.append("List<${returnedCollectionType.name}>[${childrenNodes.size}]", attributes)
  }

  val weight = if (childrenNodes.isEmpty()) EMPTY_LIST_VALUE_WEIGHT else NOT_EMPTY_LIST_VALUE_WEIGHT

  val node = PsiViewerPropertyNodeImpl(methodNamePresentation(psiViewerApiMethod.name), childrenNodes, weight)
    .appendPresentation(returnedListPresentation)
    .appendPresentation(methodReturnTypePresentation(psiViewerApiMethod.returnType))
    .withApiMethod(psiViewerApiMethod)

  return if (childrenNodes.isNotEmpty() || nodeContext.showEmptyNodes) node else null
}

suspend fun computePsiViewerApiClassesNodes(
  apiClasses: List<Class<*>>,
  instance: Any,
  nodeContext: PsiViewerPropertyNode.Context,
): List<PsiViewerPropertyNode> {
  return coroutineScope {
    val apiClassesNodes = apiClasses
      .mapIndexed { idx, apiClass ->
        async {
          val apiMethods = apiClass.psiViewerApiMethods(nodeContext, instance)
          psiViewerPropertyNodeForApiClass(nodeContext, apiClass, apiMethods, weight = idx)
        }
      }
      .awaitAll()
      .filterNotNull()
      .toList()

    apiClassesNodes
  }
}

suspend fun psiViewerPropertyNodeForApiClass(
  nodeContext: PsiViewerPropertyNode.Context,
  apiClass: Class<*>,
  apiMethods: List<PsiViewerApiMethod>,
  weight: Int,
): PsiViewerPropertyNode? {
  val childrenNodesForApiClass = computePsiViewerPropertyNodesByCallingApiMethods(nodeContext, apiMethods)
    .map { it.withApiClass(apiClass) }
  if (childrenNodesForApiClass.isEmpty()) return null

  val presentation = PsiViewerPropertyNode.Presentation {
    it.icon = if (apiClass.isInterface) AllIcons.Nodes.Interface else AllIcons.Nodes.Class
    it.append(apiClass.canonicalName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
  }

  return PsiViewerPropertyNodeImpl(presentation, childrenNodesForApiClass, weight).withApiClass(apiClass)
}

suspend fun computePsiViewerPropertyNodesByCallingApiMethods(
  nodeContext: PsiViewerPropertyNode.Context,
  methods: List<PsiViewerApiMethod>,
): List<PsiViewerPropertyNode> {
  return coroutineScope {
    methods
      .map {
        async {
          computePsiViewerNodeByMethodCall(nodeContext, it)
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