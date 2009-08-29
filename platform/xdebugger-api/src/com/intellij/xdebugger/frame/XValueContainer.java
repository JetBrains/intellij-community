package com.intellij.xdebugger.frame;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * @author nik
 */
public abstract class XValueContainer {
  /**
   * Start computing children of the value. Call {@link XCompositeNode#addChildren(java.util.List, boolean)} to add child nodes
   * @param node node in the tree
   */
  public void computeChildren(@NotNull XCompositeNode node) {
    node.addChildren(Collections.<XValue>emptyList(), true);
  }
}
