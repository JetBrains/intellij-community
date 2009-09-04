package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class WatchErrorNode extends MessageTreeNode implements WatchNode {
  private final String myExpression;

  public WatchErrorNode(XDebuggerTree tree, XDebuggerTreeNode parent, @NotNull String expression, @NotNull String errorMessage) {
    super(tree, parent, true);
    myExpression = expression;
    setIcon(XDebuggerUIConstants.ERROR_MESSAGE_ICON);
    myText.append(expression + " = ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
    myText.append(errorMessage, SimpleTextAttributes.ERROR_ATTRIBUTES);
  }

  @NotNull
  public String getExpression() {
    return myExpression;
  }
}
