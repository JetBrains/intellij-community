// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui;

import com.intellij.execution.ui.UIExperiment;
import com.intellij.ide.ui.customization.CustomizableActionGroupProvider;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;

final class XDebugTabCustomizableActionGroupProvider extends CustomizableActionGroupProvider {
  @Override
  public void registerGroups(CustomizableActionGroupRegistrar registrar) {
    registrar.addCustomizableActionGroup(XDebuggerActions.WATCHES_TREE_TOOLBAR_GROUP, XDebuggerBundle.message("debug.watches.toolbar"));
    if (UIExperiment.isNewDebuggerUIEnabled()) {
      registrar.addCustomizableActionGroup(XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_3_GROUP, XDebuggerBundle.message("debug.tool.window.header.toolbar"));
      registrar.addCustomizableActionGroup(XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_3_EXTRA_GROUP, XDebuggerBundle.message("debug.tool.window.more.toolbar"));
    }
    else {
      registrar.addCustomizableActionGroup(XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_GROUP, XDebuggerBundle.message("debug.tool.window.top.toolbar"));
      registrar.addCustomizableActionGroup(XDebuggerActions.TOOL_WINDOW_LEFT_TOOLBAR_GROUP, XDebuggerBundle.message("debug.tool.window.left.toolbar"));
    }
  }
}
