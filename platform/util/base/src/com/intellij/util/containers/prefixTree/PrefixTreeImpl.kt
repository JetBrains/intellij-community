// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.prefixTree

import com.intellij.util.containers.FList
import com.intellij.util.containers.OptionalKt
import com.intellij.util.containers.OptionalKt.Companion.getOrDefault
import com.intellij.util.containers.OptionalKt.Companion.getOrNull
import com.intellij.util.containers.prefixTree.map.AbstractPrefixTreeMap
import org.jetbrains.annotations.ApiStatus
import java.util.AbstractMap.SimpleEntry

@ApiStatus.Internal
internal class PrefixTreeImpl<Key, Value> : AbstractPrefixTreeMap<List<Key>, Value>(), MutablePrefixTree<Key, Value> {

  override var size: Int = 0
    private set

  private var state: OptionalKt<Value> = OptionalKt.EMPTY

  private val children = LinkedHashMap<Key, PrefixTreeImpl<Key, Value>>()

  override val entries: Set<Map.Entry<List<Key>, Value>>
    get() = getTreeEntries()

  override fun containsKey(key: List<Key>): Boolean {
    return getValue(key).isPresent()
  }

  override fun get(key: List<Key>): Value? {
    return getValue(key).getOrNull()
  }

  override fun getOrDefault(key: List<Key>, defaultValue: Value): Value {
    return getValue(key).getOrDefault(defaultValue)
  }

  override fun put(key: List<Key>, value: Value): Value? {
    return setValue(0, key, value).getOrNull()
  }

  override fun remove(key: List<Key>): Value? {
    return removeValue(0, key).getOrNull()
  }

  override fun getAncestorEntries(key: List<Key>): Set<Map.Entry<List<Key>, Value>> {
    return getAncestorTreeEntries(key)
  }

  override fun getDescendantEntries(key: List<Key>): Set<Map.Entry<List<Key>, Value>> {
    return getDescendantTreeEntries(key)
  }

  override fun getRootEntries(): Set<Map.Entry<List<Key>, Value>> {
    return getRootTreeEntries()
  }

  private fun getValue(key: List<Key>): OptionalKt<Value> {
    return findNode(key)?.state ?: OptionalKt.EMPTY
  }

  private fun setValue(index: Int, key: List<Key>, value: Value): OptionalKt<Value> {
    require(index >= 0 && index <= key.size) { "Index $index out of bound [0, " + key.size + "]" }
    if (index == key.size) {
      val previousState = state
      state = OptionalKt.of(value)
      if (!previousState.isPresent()) {
        size += 1
      }
      return previousState
    }
    val childNode = children.getOrPut(key[index]) { PrefixTreeImpl() }
    val previousState = childNode.setValue(index + 1, key, value)
    if (!previousState.isPresent()) {
      size += 1
    }
    return previousState
  }

  private fun removeValue(index: Int, key: List<Key>): OptionalKt<Value> {
    require(index >= 0 && index <= key.size) { "Index $index out of bound [0, " + key.size + "]" }
    if (index == key.size) {
      val previousState = state
      state = OptionalKt.EMPTY
      if (previousState.isPresent()) {
        size -= 1
      }
      return previousState
    }
    val childNode = children[key[index]] ?: return OptionalKt.EMPTY
    val previousState = childNode.removeValue(index + 1, key)
    if (childNode.isEmpty()) {
      children.remove(key[index])
    }
    if (previousState.isPresent()) {
      size -= 1
    }
    return previousState
  }

  private fun getTreeEntries(): Set<TreeEntry<Key, Value>> {
    val result = LinkedHashSet<TreeEntry<Key, Value>>()
    traverseTree { node ->
      if (node.state.isPresent()) {
        result.add(TreeEntry(node.key, node.state.get()))
      }
      TraverseDecision.CONTINUE
    }
    return result
  }

  private fun getAncestorTreeEntries(key: List<Key>): Set<TreeEntry<Key, Value>> {
    val result = LinkedHashSet<TreeEntry<Key, Value>>()
    traverseNode(key) { node ->
      if (node.state.isPresent()) {
        result.add(TreeEntry(node.key, node.state.get()))
      }
    }
    return result
  }

  private fun getDescendantTreeEntries(key: List<Key>): Set<TreeEntry<Key, Value>> {
    val node = findNode(key) ?: return emptySet()
    return node.getTreeEntries()
      .mapTo(LinkedHashSet()) { TreeEntry(key + it.key, it.value) }
  }

  private fun getRootTreeEntries(): Set<TreeEntry<Key, Value>> {
    val result = LinkedHashSet<TreeEntry<Key, Value>>()
    traverseTree { node ->
      if (node.state.isPresent()) {
        result.add(TreeEntry(node.key, node.state.get()))
      }
      when (node.state.isPresent()) {
        true -> TraverseDecision.DO_NOT_GO_DEEPER
        else -> TraverseDecision.CONTINUE
      }
    }
    return result
  }

  private fun findNode(key: List<Key>): PrefixTreeImpl<Key, Value>? {
    var node = this
    for (keyElement in key) {
      node = node.children[keyElement] ?: return null
    }
    return node
  }

  private fun traverseNode(key: List<Key>, process: (TreeNode<Key, Value>) -> Unit) {
    var node = this
    process(TreeNode(emptyList(), node.state))
    for ((i, keyElement) in key.withIndex()) {
      node = node.children[keyElement] ?: return
      process(TreeNode(key.subList(0, i + 1), node.state))
    }
  }

  private fun traverseTree(process: (TreeNode<Key, Value>) -> TraverseDecision) {
    val queue = ArrayDeque<Pair<FList<Key>, PrefixTreeImpl<Key, Value>>>()
    queue.addLast(FList.emptyList<Key>() to this)
    while (queue.isNotEmpty()) {
      val (key, node) = queue.removeFirst()
      when (process(TreeNode(key.asReversed(), node.state))) {
        TraverseDecision.STOP -> break
        TraverseDecision.DO_NOT_GO_DEEPER -> continue
        TraverseDecision.CONTINUE -> {
          for ((keyElement, child) in node.children) {
            queue.add(key.prepend(keyElement) to child)
          }
        }
      }
    }
  }

  private enum class TraverseDecision { CONTINUE, STOP, DO_NOT_GO_DEEPER }

  private class TreeNode<Key, Value>(val key: List<Key>, val state: OptionalKt<Value>)

  private class TreeEntry<Key, Value>(key: List<Key>, value: Value) : SimpleEntry<List<Key>, Value>(key, value)
}