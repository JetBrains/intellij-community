// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow

import java.util.*

internal class WorkList(flowSize: Int, order: IntArray) {

  private val mySize: Int = order.size
  /**
   * Mapping: index -> instruction number
   */
  private val myOrder: IntArray = order
  /**
   * Mapping: instruction number -> index in [myOrder] array
   */
  private val myInstructionToOrder: IntArray = IntArray(flowSize)
  /**
   * Mapping: index -> whether instruction number needs to be processed
   *
   * Indexes match [myOrder] indexes
   */
  private val mySet: BitSet = BitSet()

  /**
   * Index of the first instruction, for which we can be sure that all instructions before are 0 in [mySet]
   */
  private var firstUnmarkedIndex = 0

  init {
    order.forEachIndexed { index, instruction ->
      myInstructionToOrder[instruction] = index
    }
    mySet.set(0, mySize)
  }

  val isEmpty: Boolean get() = mySet.isEmpty

  /**
   * Instruction number to be processed next
   */
  fun next(): Int {
    /**
     * Index of instruction in [myOrder]
     */
    val next = mySet.nextSetBit(firstUnmarkedIndex)
    assert(next >= 0 && next < mySize)
    mySet.clear(next)
    firstUnmarkedIndex = next
    return myOrder[next]
  }

  /**
   * Marks instruction to be processed
   */
  fun offer(instructionIndex: Int) {
    /**
     * Index of instruction in [myOrder]
     */
    val orderIndex = myInstructionToOrder[instructionIndex]
    firstUnmarkedIndex = minOf(firstUnmarkedIndex, orderIndex)
    mySet.set(orderIndex, true)
  }
}
