// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.commander.CommanderBundle;
import com.intellij.ide.ui.customization.CustomizableActionGroupProvider;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.keymap.KeyMapBundle;

public class CommanderCustomizableActionGroupProvider extends CustomizableActionGroupProvider {
  @Override
  public void registerGroups(CustomizableActionGroupRegistrar registrar) {
    registrar.addCustomizableActionGroup(IdeActions.GROUP_COMMANDER_POPUP, CommanderBundle.message("commender.view.popup.menu.title"));
  }
}
