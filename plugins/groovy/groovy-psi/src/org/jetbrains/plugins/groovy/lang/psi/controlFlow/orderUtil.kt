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
@file:JvmName("OrderUtil")

package org.jetbrains.plugins.groovy.lang.psi.controlFlow

import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.InstructionImpl
import java.util.*

private val fakeRoot = InstructionImpl(null)

fun reversedPostOrder(flow: Array<Instruction>): IntArray = postOrder(flow).reversedArray()

fun postOrder(flow: Array<Instruction>): IntArray {
  val N = flow.size
  val result = IntArray(N)
  var resultIndex = 0

  val visited = BooleanArray(N)
  val stack: Deque<Pair<Instruction, Iterator<Instruction>>> = LinkedList()
  stack.push(fakeRoot to flow.iterator())

  while (!stack.isEmpty()) {
    val (instruction, iterator) = stack.peek()
    val undiscovered = iterator.firstOrNull { !visited[it.num()] }
    if (undiscovered != null) {
      visited[undiscovered.num()] = true          // discover successor
      stack.push(undiscovered to undiscovered.allSuccessors().iterator())
    }
    else {
      stack.pop()
      if (instruction === fakeRoot) {
        assert(stack.isEmpty())
      }
      else {
        result[resultIndex++] = instruction.num() // mark black if all successors are discovered
      }
    }
  }

  assert(resultIndex == N)
  return result
}

private inline fun <T> Iterator<T>.firstOrNull(predicate: (T) -> Boolean): T? {
  while (hasNext()) {
    val next = next()
    if (predicate(next)) return next
  }
  return null
}