// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers

/**
 * Describes the result of processing a node of a tree using Depth-first search in pre-order.
 */
enum class TreeNodeProcessingResult {
  /**
   * Continue processing children of the current node and its siblings.  
   */                                  
  CONTINUE,

  /**
   * Skip processing of children of the current node and continue with the next sibling. 
   */
  SKIP_CHILDREN,

  /**
   * Skip processing of children and siblings of the current node, and continue with the next sibling of its parent node.
   */
  SKIP_TO_PARENT,

  /**
   * Stop processing.
   */
  STOP
}