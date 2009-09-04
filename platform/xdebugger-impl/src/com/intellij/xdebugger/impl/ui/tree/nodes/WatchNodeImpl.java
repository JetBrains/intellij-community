package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.frame.XValue;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class WatchNodeImpl extends XValueNodeImpl implements WatchNode {
  private final String myExpression;

  public WatchNodeImpl(final @NotNull XDebuggerTree tree, final @NotNull WatchesRootNode parent, final @NotNull XValue result,
                       final @NotNull String expression) {
    super(tree, parent, result);
    myExpression = expression;
  }

  @NotNull
  public String getExpression() {
    return myExpression;
  }
}
