// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.prefix.map

import com.intellij.util.containers.OptionalKt
import org.jetbrains.annotations.ApiStatus
import java.util.LinkedHashMap

@ApiStatus.Internal
internal class PrefixTreeNode<Key, Value> {

  private var size: Int = 0

  private var state: OptionalKt<Value> = OptionalKt.EMPTY

  private val children = LinkedHashMap<Key, PrefixTreeNode<Key, Value>>()

  fun isLeaf(): Boolean {
    return children.isEmpty()
  }

  fun isEmpty(): Boolean {
    return isLeaf() && state.isNotPresent()
  }

  fun getSize(): Int {
    return size
  }

  fun getValue(key: List<Key>): OptionalKt<Value> {
    return findNode(key)?.state ?: OptionalKt.EMPTY
  }

  fun setValue(key: List<Key>, value: Value): OptionalKt<Value> {
    return setValue(0, key, value)
  }

  fun removeValue(key: List<Key>): OptionalKt<Value> {
    return removeValue(0, key)
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
    val childNode = children.getOrPut(key[index]) { PrefixTreeNode() }
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

  fun containsKey(key: List<Key>): Boolean {
    val node = findNode(key) ?: return false
    return node.state.isPresent()
  }

  fun getValues(): List<Value> {
    val result = ArrayList<Value>()
    traverseTree { state ->
      if (state.isPresent()) {
        result.add(state.get())
      }
      TraverseDecision.CONTINUE
    }
    return result
  }

  fun getAncestorValues(key: List<Key>): List<Value> {
    val result = ArrayList<Value>()
    traverseNode(key) { state ->
      if (state.isPresent()) {
        result.add(state.get())
      }
    }
    return result
  }

  fun getDescendantValues(key: List<Key>): List<Value> {
    val node = findNode(key) ?: return emptyList()
    val result = ArrayList<Value>()
    for (value in node.getValues()) {
      result.add(value)
    }
    return result
  }

  fun getRootValues(): List<Value> {
    val result = ArrayList<Value>()
    traverseTree { state ->
      if (state.isPresent()) {
        result.add(state.get())
      }
      when (state.isPresent()) {
        true -> TraverseDecision.DO_NOT_GO_DEEPER
        else -> TraverseDecision.CONTINUE
      }
    }
    return result
  }

  private fun findNode(key: List<Key>): PrefixTreeNode<Key, Value>? {
    return traverseNode(key) {}
  }

  private fun traverseNode(key: List<Key>, process: (OptionalKt<Value>) -> Unit): PrefixTreeNode<Key, Value>? {
    var node = this
    process(node.state)
    for (keyElement in key) {
      node = node.children[keyElement] ?: return null
      process(node.state)
    }
    return node
  }

  private fun traverseTree(process: (OptionalKt<Value>) -> TraverseDecision) {
    val queue = ArrayDeque<PrefixTreeNode<Key, Value>>()
    queue.addLast(this)
    while (queue.isNotEmpty()) {
      val node = queue.removeFirst()
      when (process(node.state)) {
        TraverseDecision.STOP -> break
        TraverseDecision.DO_NOT_GO_DEEPER -> continue
        TraverseDecision.CONTINUE -> {
          for (child in node.children.values) {
            queue.add(child)
          }
        }
      }
    }
  }

  private enum class TraverseDecision { CONTINUE, STOP, DO_NOT_GO_DEEPER }
}