// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.wm.WelcomeScreenCustomization;
import com.intellij.ui.components.labels.ActionLink;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenComponentFactory.createShowPopupAction;

public class WelcomeScreenDefaultCustomization implements WelcomeScreenCustomization {

  @Override
  public @Nullable Component createQuickAccessComponent(@NotNull Disposable parentDisposable) {
    AnAction action = createShowPopupAction(IdeActions.GROUP_WELCOME_SCREEN_OPTIONS);
    ActionLink link = new ActionLink("", AllIcons.General.GearHover, action);
    link.setToolTipText(ActionsBundle.groupText(IdeActions.GROUP_WELCOME_SCREEN_OPTIONS));
    link.setBorder(JBUI.Borders.emptyBottom(4));
    return WelcomeScreenComponentFactory.wrapActionLink(link);
  }
}
