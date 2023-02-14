// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui.tree.nodes;

import org.jetbrains.annotations.NotNull;

public class HeadlessValueEvaluationCallback extends HeadlessValueEvaluationCallbackBase {
  private final XValueNodeImpl myNode;

  public HeadlessValueEvaluationCallback(@NotNull XValueNodeImpl node) {
    super(node.myTree.getProject());
    myNode = node;
  }
  
  public XValueNodeImpl getNode() {
    return myNode;
  }

  @Override
  public boolean isObsolete() {
    return myNode.isObsolete() || super.isObsolete();
  }
}