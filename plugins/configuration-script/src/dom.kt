package com.intellij.configurationScript

import org.snakeyaml.engine.v2.nodes.*

internal fun findValueNodeByPath(namePath: String, rootNodes: List<NodeTuple>): List<NodeTuple>? {
  return findNodeByPath(namePath, rootNodes, MappingNodeTypeFilter)
}

internal fun findListNodeByPath(namePath: String, rootNodes: List<NodeTuple>): List<Node>? {
  return findNodeByPath(namePath, rootNodes, ListNodeTypeFilter)
}

private interface NodeTypeFilter<T : Any> {
  fun convertToResult(node: Node): List<T>?
}

private object MappingNodeTypeFilter : NodeTypeFilter<NodeTuple> {
  override fun convertToResult(node: Node): List<NodeTuple>? {
    return (node as? MappingNode)?.value
  }
}

private object ListNodeTypeFilter : NodeTypeFilter<Node> {
  override fun convertToResult(node: Node): List<Node>? {
    return (node as? SequenceNode)?.value
  }
}

private fun <T : Any> findNodeByPath(namePath: String, rootNodes: List<NodeTuple>, filter: NodeTypeFilter<T>): List<T>? {
  var nodes = rootNodes
  val iterator = namePath.splitToSequence('.').iterator()
  loop@
  do {
    val name = iterator.next()
    for (tuple in nodes) {
      val keyNode = tuple.keyNode
      if (keyNode is ScalarNode && keyNode.value == name) {
        if (iterator.hasNext()) {
          nodes = (tuple.valueNode as? MappingNode)?.value ?: continue
        }
        else {
          return filter.convertToResult(tuple.valueNode)
        }

        continue@loop
      }
    }
    return null
  } while (iterator.hasNext())

  return null
}

inline fun List<NodeTuple>.processStringKeys(processor: (String, Node) -> Unit) {
  for (tuple in this) {
    val keyNode = tuple.keyNode
    if (keyNode is ScalarNode) {
      processor(keyNode.value, tuple.valueNode)
    }
  }
}