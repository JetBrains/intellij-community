// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    val next = mySet.nextSetBit(0)
    assert(next >= 0 && next < mySize)
    mySet.clear(next)
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
    mySet.set(orderIndex, true)
  }
}
