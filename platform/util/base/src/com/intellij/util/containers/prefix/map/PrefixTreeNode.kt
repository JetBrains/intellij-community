// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.prefix.map

import com.intellij.util.containers.FList
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

  fun findValue(key: FList<Key>): OptionalKt<Value> {
    return findNode(key)?.state ?: OptionalKt.EMPTY
  }

  fun setValue(key: FList<Key>, value: Value): OptionalKt<Value> {
    if (key.isEmpty()) {
      val previousState = state
      state = OptionalKt.of(value)
      if (!previousState.isPresent()) {
        size += 1
      }
      return previousState
    }
    val childNode = children.getOrPut(key.head) { PrefixTreeNode() }
    val previousState = childNode.setValue(key.tail, value)
    if (!previousState.isPresent()) {
      size += 1
    }
    return previousState
  }

  fun removeValue(key: FList<Key>): OptionalKt<Value> {
    if (key.isEmpty()) {
      val previousState = state
      state = OptionalKt.EMPTY
      if (previousState.isPresent()) {
        size -= 1
      }
      return previousState
    }
    val childNode = children[key.head] ?: return OptionalKt.EMPTY
    val previousState = childNode.removeValue(key.tail)
    if (childNode.isEmpty()) {
      children.remove(key.head)
    }
    if (previousState.isPresent()) {
      size -= 1
    }
    return previousState
  }

  fun containsKey(key: FList<Key>): Boolean {
    if (key.isEmpty()) {
      return state.isPresent()
    }
    val child = children[key.head] ?: return false
    return child.containsKey(key.tail)
  }

  fun findNode(key: FList<Key>): PrefixTreeNode<Key, Value>? {
    if (key.isEmpty()) {
      return this
    }
    val child = children[key.head] ?: return null
    return child.findNode(key.tail)
  }

  fun getEntrySequence(): Sequence<Pair<FList<Key>, Value>> {
    return sequence {
      state.ifPresent {
        yield(FList.emptyList<Key>() to it)
      }
      for ((keyPrefix, child) in children) {
        for ((key, value) in child.getEntrySequence()) {
          yield(key.prepend(keyPrefix) to value)
        }
      }
    }
  }

  fun getAncestorEntrySequence(key: FList<Key>): Sequence<Pair<FList<Key>, Value>> {
    return sequence {
      state.ifPresent {
        yield(FList.emptyList<Key>() to it)
      }
      if (key.isEmpty()) {
        return@sequence
      }
      val childNode = children[key.head] ?: return@sequence
      for ((keyPostfix, value) in childNode.getAncestorEntrySequence(key.tail)) {
        yield(keyPostfix.prepend(key.head) to value)
      }
    }
  }

  fun getDescendantEntrySequence(key: FList<Key>): Sequence<Pair<List<Key>, Value>> {
    val node = findNode(key) ?: return emptySequence()
    return sequence {
      for ((keyPostfix, value) in node.getEntrySequence()) {
        yield(key + keyPostfix to value)
      }
    }
  }

  fun getRootEntrySequence(): Sequence<Pair<FList<Key>, Value>> {
    state.ifPresent {
      return sequenceOf(FList.emptyList<Key>() to it)
    }
    return sequence {
      for ((key, childNode) in children) {
        for ((keyPostfix, value) in childNode.getRootEntrySequence()) {
          yield(keyPostfix.prepend(key) to value)
        }
      }
    }
  }
}