package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;

/**
 * @author nik
 */
public class XStackFrameNode extends XValueContainerNode<XStackFrame> {
  public XStackFrameNode(final XDebuggerTree tree, final XStackFrame xStackFrame) {
    super(tree, null, xStackFrame);
    setLeaf(false);
  }
}
