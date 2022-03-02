// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("OrderUtil")

package org.jetbrains.plugins.groovy.lang.psi.controlFlow

import com.intellij.util.ArrayUtilRt
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.InstructionImpl
import java.util.*
import kotlin.math.abs

private val fakeRoot = InstructionImpl(null)

@JvmOverloads
fun reversedPostOrder(flow: Array<Instruction>, reachable: Boolean = false): IntArray = postOrder(flow, reachable).reversedArray()

fun postOrder(flow: Array<Instruction>, reachable: Boolean): IntArray {
  val n = flow.size
  if (n == 0) return ArrayUtilRt.EMPTY_INT_ARRAY

  val result = IntArray(n) { -1 }
  var resultIndex = 0

  val visited = BooleanArray(n)
  val stack: Deque<Pair<Instruction, Iterator<Instruction>>> = LinkedList()

  val rootIterator = if (reachable) listOf(flow[0]).iterator() else flow.iterator()
  stack.push(fakeRoot to rootIterator)

  while (!stack.isEmpty()) {
    val (instruction, iterator) = stack.peek()
    val undiscovered = iterator.firstOrNull { !visited[it.num()] }
    if (undiscovered != null) {
      visited[undiscovered.num()] = true          // discover successor
      val successors = undiscovered
        .allSuccessors()
        .sortedByDescending { abs(it.num() - undiscovered.num()) } // cycles should be processed as early as possible
      stack.push(undiscovered to successors.iterator())
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

  if (reachable) {
    assert(resultIndex <= n)
  }
  else {
    assert(resultIndex == n)
  }
  return if (resultIndex == n) result else result.take(resultIndex).toIntArray()
}

private inline fun <T> Iterator<T>.firstOrNull(predicate: (T) -> Boolean): T? {
  while (hasNext()) {
    val next = next()
    if (predicate(next)) return next
  }
  return null
}