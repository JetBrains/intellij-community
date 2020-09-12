// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.inline;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface XInlineWatchesView {
  DataKey<XInlineWatchesView> DATA_KEY = DataKey.create("XDEBUGGER_INLINE_WATCHES_VIEW");

  void addInlineWatchExpression(@NotNull XExpression expression, int index, XSourcePosition position, boolean navigateToWatchNode);

  void removeInlineWatches(List<InlineWatch> nodes);

  void removeAllInlineWatches();
}
