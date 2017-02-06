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

import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction
import java.util.*

internal class WorkList(size: Int) {

  private val myQueue: Queue<Instruction> = LinkedList<Instruction>()
  private val myVisited: BooleanArray = BooleanArray(size)

  fun remove(): Instruction = myQueue.remove()

  val isEmpty: Boolean get() = myQueue.isEmpty()

  /**
   * Adds element to the queue and marks it as visited
   */
  fun offerUnconditionally(instruction: Instruction) {
    myQueue.add(instruction)
    myVisited[instruction.num()] = true
  }

  /**
   * Adds element to the queue and marks it as visited if it wasn't visited before
   * @return `true` if element was added to the queue
   */
  fun offer(instruction: Instruction): Boolean {
    if (myVisited[instruction.num()]) return false
    offerUnconditionally(instruction)
    return true
  }
}
