/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log.graph

import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.elements.GraphNode
import com.intellij.vcs.log.graph.parser.EdgeNodeCharConverter.*
import com.intellij.vcs.log.graph.api.elements.GraphEdge
import com.intellij.vcs.log.graph.parser.CommitParser

fun LinearGraph.asString(): String {
  val s = StringBuilder()
  for (nodeIndex in 0..nodesCount() - 1) {
    if (nodeIndex > 0)
      s.append("\n");
    val node = getGraphNode(nodeIndex)
    s.append(node.asString()).append(CommitParser.SEPARATOR)

    getAdjacentEdges(nodeIndex).map { it.asString() }.joinTo(s, separator = " ")
  }
  return s.toString();
}

fun GraphNode.asString(): String = "${getNodeIndex()}:${getNodeId()}_${toChar(getType())}"

fun Int?.asString() = if (this == null) "n" else toString()

fun GraphEdge.asString(): String = "${getUpNodeIndex().asString()}:${getDownNodeIndex().asString()}:${getAdditionInfo().asString()}_${toChar(getType())}"