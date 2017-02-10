/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.psi.dataFlow

import java.util.*

internal class WorkList(order: IntArray) {

  private val mySize: Int = order.size
  /**
   * Mapping: index -> instruction number
   */
  private val myOrder: IntArray = order
  /**
   * Mapping: instruction number -> index in [myOrder] array
   */
  private val myInstructionToOrder: IntArray = IntArray(mySize)
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
