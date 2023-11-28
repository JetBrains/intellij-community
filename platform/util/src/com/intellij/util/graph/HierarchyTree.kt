/*
 * Copyright 2000-2023 JetBrains s.r.o.
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
package com.intellij.util.graph

/**
 * A Hierarchy Tree interface. Allows performing hierarchical operations such as linking child nodes to a parent, ungrouping nodes, etc.
 * Each node of the hierarchy has a parent.
 *
 * @param N Represents any Hierarchic Node.
 * @param GN Represents the Group Node.
 */
interface HierarchyTree<N : Any, GN : N> {

  /**
   * Retrieves all hierarchy nodes from the hierarchy tree.
   *
   * @return The set containing all the hierarchy nodes in the hierarchy tree.
   */
  fun getAllHierarchyNodes(): Set<N>

  /**
   * Retrieves the parent node of the given node.
   *
   * @param node The node whose parent is to be retrieved.
   * @return The parent node of the given node, or null if the node has no parent.
   */
  fun getParent(node: N): GN?

  /**
   * Returns the children nodes of the given group node.
   *
   * @param group The group node for which to retrieve the children.
   * @param isRecursively Specifies whether to retrieve the children recursively.
   *
   * @return The set of children nodes of the given group node.
   */
  fun getChildren(group: GN, isRecursively: Boolean = false): Set<N>

  /**
   * Connects a child node to a parent group node in the hierarchy tree.
   *
   * @param child The child node to be connected.
   * @param parent The parent group node to which the child node will be connected.
   */
  fun connect(child: N, parent: GN)

  /**
   * Connects each child node in the given collection to the specified parent node.
   *
   * @param children The collection of child nodes to be connected to the parent node.
   * @param parent The parent node to which the child nodes will be connected.
   */
  fun connect(children: Collection<N>, parent: GN)

  /**
   * Ungroups the nodes from the given group node.
   *
   * @param group The group node from which to ungroup the nodes. Must be a valid group node in the hierarchy tree.
   */
  fun ungroupNodes(group: GN)

  /**
   * Ungroups the nodes from their parent group.
   *
   * @param nodes The collection of nodes to ungroup.
   */
  fun ungroupNodes(nodes: Collection<N>)

  /**
   * Ungroups all nodes in the hierarchy by removing their parent-child relationships with any group node.
   * This method disassociates all nodes from their parent group nodes, effectively flattening the hierarchy.
   * Once ungrouped, the nodes will be independent and not part of any group.
   */
  fun ungroupAllNodes()

  /**
   * Removes the specified group node and all its child nodes from the hierarchy tree.
   *
   * @param group The group node to be removed.
   */
  fun removeGroupWithItsNodes(group: GN)
}