// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.util

import com.intellij.openapi.vfs.newvfs.persistent.log.util.AdvancingPositionTracker.AdvanceToken
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class LockFreeAdvancingPositionTracker(initialPosition: Long) : AdvancingPositionTracker {
  private val advanceNode: AtomicReference<Node>

  // all preceding to current readyNode nodes were marked as finished
  private val readyNode: AtomicReference<Node>

  init {
    val initNode = Node(initialPosition, AtomicReference(null), AtomicBoolean(false))
    advanceNode = AtomicReference<Node>(initNode)
    readyNode = AtomicReference<Node>(initNode)
  }

  override fun beginAdvance(size: Long): AdvanceToken {
    while (true) {
      val result = advanceNode.get()
      val nextAdvance = Node(result.position + size,
                             AtomicReference(null),
                             AtomicBoolean(false))
      if (!result.next.compareAndSet(null, nextAdvance)) {
        while (true) {
          if (!tryMoveAdvance()) break
        }
        continue // try again
      }
      // try to move advanceNode
      advanceNode.compareAndSet(result, nextAdvance)
      return result
    }
  }

  override fun finishAdvance(token: AdvanceToken) {
    require(token is Node) { "$token didn't come from LockFreeAdvancingPositionTracker.beginAdvance()" }
    if (!token.finished.compareAndSet(false, true)) {
      throw IllegalStateException("token was already marked as finished")
    }
    if (tryMoveReady()) {
      tryMoveReady() // speed up ready node if it's lagging behind
    }
  }

  private fun tryMoveAdvance(): Boolean {
    val current = advanceNode.get()
    val next = current.next.get() ?: return false
    return advanceNode.compareAndSet(current, next)
  }

  private fun tryMoveReady(): Boolean {
    val current = readyNode.get()
    if (!current.finished.get()) return false
    val next = current.next.get() ?: return false
    return readyNode.compareAndSet(current, next)
  }

  override fun getReadyPosition(): Long {
    while (true) {
      if (!tryMoveReady()) break
    }
    return readyNode.get().position
  }

  override fun getCurrentAdvancePosition(): Long {
    return advanceNode.get().position
  }

  private class Node(override val position: Long,
                     val next: AtomicReference<Node?>,
                     val finished: AtomicBoolean)
    : AdvanceToken
}