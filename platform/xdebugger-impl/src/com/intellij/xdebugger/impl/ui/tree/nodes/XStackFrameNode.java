// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class XStackFrameNode extends XValueContainerNode<XStackFrame> {

  public XStackFrameNode(final @NotNull XDebuggerTree tree, final @NotNull XStackFrame xStackFrame) {
    super(tree, null, false, xStackFrame);
  }

}
