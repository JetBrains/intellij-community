/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.debugger

import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueGroup
import org.jetbrains.debugger.values.ObjectValue
import org.jetbrains.debugger.values.ValueType
import java.util.*

internal fun lazyVariablesGroup(variables: ObjectValue, start: Int, end: Int, context: VariableContext) = LazyVariablesGroup(variables, start, end, context)

class LazyVariablesGroup(private val value: ObjectValue, private val startInclusive: Int, private val endInclusive: Int, private val context: VariableContext, private val componentType: ValueType? = null, private val sparse: Boolean = true) : XValueGroup(String.format("[%,d \u2026 %,d]", startInclusive, endInclusive)) {
  override fun computeChildren(node: XCompositeNode) {
    node.setAlreadySorted(true)

    val bucketThreshold = XCompositeNode.MAX_CHILDREN_TO_SHOW
    if (!sparse && endInclusive - startInclusive > bucketThreshold) {
      node.addChildren(XValueChildrenList.topGroups(computeNotSparseGroups(value, context, startInclusive, endInclusive + 1, bucketThreshold)), true)
      return
    }

    value.getIndexedProperties(startInclusive, endInclusive + 1, bucketThreshold, object : VariableView.ObsolescentIndexedVariablesConsumer(node) {
      override fun consumeRanges(ranges: IntArray?) {
        if (ranges == null) {
          val groupList = XValueChildrenList()
          addGroups(value, ::lazyVariablesGroup, groupList, startInclusive, endInclusive, XCompositeNode.MAX_CHILDREN_TO_SHOW, context)
          node.addChildren(groupList, true)
        }
        else {
          addRanges(value, ranges, node, context, true)
        }
      }

      override fun consumeVariables(variables: List<Variable>) {
        node.addChildren(createVariablesList(variables, context, null), true)
      }
    }, componentType)
  }
}

fun computeNotSparseGroups(value: ObjectValue, context: VariableContext, _fromInclusive: Int, toExclusive: Int, bucketThreshold: Int): List<XValueGroup> {
  var fromInclusive = _fromInclusive
  val size = toExclusive - fromInclusive
  val bucketSize = Math.pow(bucketThreshold.toDouble(), Math.ceil(Math.log(size.toDouble()) / Math.log(bucketThreshold.toDouble())) - 1).toInt()
  val groupList = ArrayList<XValueGroup>(Math.ceil((size / bucketSize).toDouble()).toInt())
  while (fromInclusive < toExclusive) {
    groupList.add(LazyVariablesGroup(value, fromInclusive, fromInclusive + (Math.min(bucketSize, toExclusive - fromInclusive) - 1), context, ValueType.NUMBER, false))
    fromInclusive += bucketSize
  }
  return groupList
}

fun addRanges(value: ObjectValue, ranges: IntArray, node: XCompositeNode, context: VariableContext, isLast: Boolean) {
  val groupList = XValueChildrenList(ranges.size / 2)
  var i = 0
  val n = ranges.size
  while (i < n) {
    groupList.addTopGroup(LazyVariablesGroup(value, ranges[i], ranges[i + 1], context))
    i += 2
  }
  node.addChildren(groupList, isLast)
}

internal fun <T> addGroups(data: T,
                  groupFactory: (data: T, start: Int, end: Int, context: VariableContext) -> XValueGroup,
                  groupList: XValueChildrenList,
                  _from: Int,
                  limit: Int,
                  bucketSize: Int,
                  context: VariableContext) {
  var from = _from
  var to = Math.min(bucketSize, limit)
  var done = false
  do {
    val groupFrom = from
    var groupTo = to

    from += bucketSize
    to = from + Math.min(bucketSize, limit - from)

    // don't create group for only one member
    if (to - from == 1) {
      groupTo++
      done = true
    }
    groupList.addTopGroup(groupFactory(data, groupFrom, groupTo, context))
    if (from >= limit) {
      break
    }
  }
  while (!done)
}