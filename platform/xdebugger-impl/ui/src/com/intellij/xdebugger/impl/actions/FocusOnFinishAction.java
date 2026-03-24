// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.xdebugger.impl.actions;

import com.intellij.execution.ui.actions.AbstractFocusOnAction;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class FocusOnFinishAction extends AbstractFocusOnAction {
  public FocusOnFinishAction() {
    super(XDebuggerUIConstants.LAYOUT_VIEW_FINISH_CONDITION);
  }
}
