// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.content.tabs;

import com.intellij.ide.actions.PinActiveTabAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Toggleable;

/**
 * @deprecated use {@link PinActiveTabAction}
 */
@Deprecated
public class PinToolwindowTabAction extends PinActiveTabAction.TW implements Toggleable {
  public static final String ACTION_NAME = "PinToolwindowTab";

  public static AnAction getPinAction() {
    return ActionManager.getInstance().getAction(ACTION_NAME);
  }
}
