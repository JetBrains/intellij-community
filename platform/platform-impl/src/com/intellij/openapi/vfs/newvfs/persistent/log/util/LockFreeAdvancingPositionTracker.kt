// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.util

import com.intellij.openapi.vfs.newvfs.persistent.log.util.AdvancingPositionTracker.AdvanceToken
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicMarkableReference
import java.util.concurrent.atomic.AtomicReference

/**
 * Lock-free implementation of [CloseableAdvancingPositionTracker]. All operations are thread-safe (including [close])
 */
class LockFreeAdvancingPositionTracker(initialPosition: Long) : CloseableAdvancingPositionTracker {
  // mark designates whether the tracker is closed, position in contained node designates the first unit that is not yet allocated
  private val advanceNode: AtomicMarkableReference<Node>

  // all preceding to current readyNode nodes were marked as finished
  private val readyNode: AtomicReference<Node>

  init {
    val initNode = Node(initialPosition)
    advanceNode = AtomicMarkableReference<Node>(initNode, false)
    readyNode = AtomicReference<Node>(initNode)
  }

  override fun startAdvance(size: Long): AdvanceToken {
    while (true) {
      val closeMarkHolder = booleanArrayOf(false)
      val currentAdvance: Node = advanceNode.get(closeMarkHolder)
      if (closeMarkHolder[0]) {
        throw IllegalStateException("LockFreeAdvancingPositionTracker is already closed")
      }

      val nextAdvance = Node(currentAdvance.position + size)
      val advanceWitness = advanceNode.compareAndSet(currentAdvance, nextAdvance, false, false)
      if (!advanceWitness) continue

      currentAdvance.next.setRelease(nextAdvance)
      return currentAdvance
    }
  }

  override fun close() {
    while (true) {
      val closeMarkHolder = booleanArrayOf(false)
      val currentAdvance = advanceNode.get(closeMarkHolder)
      if (closeMarkHolder[0]) return // already closed

      val closeWitness = advanceNode.compareAndSet(currentAdvance, currentAdvance, false, true)
      if (closeWitness) return
    }
  }

  override fun finishAdvance(token: AdvanceToken) {
    require(token is Node) { "$token didn't come from LockFreeAdvancingPositionTracker.beginAdvance()" }
    if (token.finished.getAndSet(true)) {
      throw IllegalStateException("token was already marked as finished")
    }
    // 2 times is to speed up ready node in case it's lagging behind
    updateAndGetReadyPosition(2)
  }

  // JIT should be smart enough to inline and optimize this
  private fun updateAndGetReadyPosition(maxMoves: Int): Long {
    val initialReady: Node = readyNode.get()
    var currentReady: Node = initialReady
    var moveCount = 0
    while (maxMoves == UNLIMITED_MOVES || moveCount < maxMoves) {
      val finishedWitness = currentReady.finished.get()
      if (!finishedWitness) break
      // next can't be null, because it was set before currentReady was given to the caller and was marked finished
      currentReady = currentReady.next.acquire!!
      moveCount++
    }
    if (initialReady != currentReady) { // try to advance ready node
      readyNode.compareAndSet(initialReady, currentReady)
    }
    return currentReady.position
  }

  override fun getReadyPosition(): Long {
    return updateAndGetReadyPosition(UNLIMITED_MOVES)
  }

  override fun getCurrentAdvancePosition(): Long {
    val markHolder = booleanArrayOf(false)
    return advanceNode.get(markHolder).position
  }


  private companion object {
    const val UNLIMITED_MOVES = -1

    // TODO: VarHandles
    private class Node(override val position: Long) : AdvanceToken {
      val next: AtomicReference<Node?> = AtomicReference(null)
      val finished: AtomicBoolean = AtomicBoolean(false)
    }
  }
}