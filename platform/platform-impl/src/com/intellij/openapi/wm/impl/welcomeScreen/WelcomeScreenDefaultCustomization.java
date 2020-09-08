// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.wm.WelcomeScreenCustomization;
import com.intellij.ui.components.labels.ActionLink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenComponentFactory.createShowPopupAction;

public class WelcomeScreenDefaultCustomization implements WelcomeScreenCustomization {

  @Override
  public @Nullable Component createQuickAccessComponent(@NotNull Disposable parentDisposable) {
    AnAction action = createShowPopupAction(IdeActions.GROUP_WELCOME_SCREEN_HELP);
    return WelcomeScreenComponentFactory.wrapActionLink(new ActionLink(IdeBundle.message("action.help"), null, action));
  }
}
