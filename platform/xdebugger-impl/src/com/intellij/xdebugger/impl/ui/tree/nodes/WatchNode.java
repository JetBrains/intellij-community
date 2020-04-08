// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.xdebugger.Obsolescent;
import com.intellij.xdebugger.XExpression;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreeNode;

public interface WatchNode extends TreeNode, Obsolescent {
  @NotNull
  XExpression getExpression();

  void setObsolete();
}