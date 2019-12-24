package com.intellij.workspace.ide

// TODO: A very good candidate to make it incrementally
/*
class EntityTrie(val entityStore: EntityStorage, val mapper: (Aspect) -> VirtualFileUrl?) {
  private val root = Node(0, mutableMapOf(), mutableSetOf())
  private val lock: Lock = ReentrantLock()

  init {
    for (node in entityStore.all) {
      for (aspect in node.aspects.values) {
        mapper(aspect)?.let { filePath ->
          val trieNode = lookupNode(filePathSegments(filePath))
          trieNode.nodes.add(node)
        }
      }
    }
  }

  private fun lookupNode(path: List<Int>): Node {
    var current = root
    for (segmentNameId in path) {
      current = current.children.computeIfAbsent(
        segmentNameId) { nameId -> Node(nameId, mutableMapOf(), mutableSetOf()) }
    }

    return current
  }

  private fun filePathSegments(path: VirtualFileUrl): List<Int> {
    val result = mutableListOf<Int>()

    var current: VirtualFileUrl? = path
    while (current != null) {
      result.add(current.segmentNameId)
      current = current.parent
    }

    result.reverse()

    return result
  }

  public fun lookup(path: VirtualFileUrl): List<List<ProjectModelNode>> = lock.withLock {
    val result = mutableListOf<List<ProjectModelNode>>()

    var current = root
    for (segment in filePathSegments(path)) {
      current = current.children[segment] ?: break
      if (current.nodes.isNotEmpty()) {
        result.add(current.nodes.toList())
      }
    }

    result.reverse()

    return result
  }

  private class Node(val segmentNameId: Int, val children: MutableMap<Int, Node>, val nodes: MutableSet<ProjectModelNode>)
}*/
