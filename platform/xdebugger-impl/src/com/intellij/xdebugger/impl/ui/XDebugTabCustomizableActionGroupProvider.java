// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui;

import com.intellij.ide.ui.customization.CustomizableActionGroupProvider;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;

public class XDebugTabCustomizableActionGroupProvider extends CustomizableActionGroupProvider {
  @Override
  public void registerGroups(CustomizableActionGroupRegistrar registrar) {
    registrar.addCustomizableActionGroup(Registry.is("debugger.new.tool.window.layout", false)
                                         ? XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_3_GROUP
                                         : XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_GROUP,
                                         XDebuggerBundle.message("debug.tool.window.top.toolbar"));
    registrar.addCustomizableActionGroup(XDebuggerActions.TOOL_WINDOW_LEFT_TOOLBAR_GROUP, XDebuggerBundle.message("debug.tool.window.left.toolbar"));
    registrar.addCustomizableActionGroup(XDebuggerActions.WATCHES_TREE_TOOLBAR_GROUP, XDebuggerBundle.message("debug.watches.toolbar"));
  }
}
