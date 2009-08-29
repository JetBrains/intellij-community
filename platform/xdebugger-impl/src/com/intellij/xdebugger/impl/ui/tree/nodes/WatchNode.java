package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.frame.XValue;

/**
 * @author nik
 */
public class WatchNode extends XValueNodeImpl {
  private final String myExpression;

  public WatchNode(final XDebuggerTree tree, final WatchesRootNode parent, final XValue result, final String expression) {
    super(tree, parent, result);
    myExpression = expression;
  }

  public String getExpression() {
    return myExpression;
  }
}
