/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.xdebugger.impl.frame;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public interface XWatchesView {
  DataKey<XWatchesView> DATA_KEY = DataKey.create("XDEBUGGER_WATCHES_VIEW");

  void addWatchExpression(@NotNull XExpression expression, int index, boolean navigateToWatchNode);

  void removeWatches(List<? extends XDebuggerTreeNode> nodes);

  void removeAllWatches();
}
