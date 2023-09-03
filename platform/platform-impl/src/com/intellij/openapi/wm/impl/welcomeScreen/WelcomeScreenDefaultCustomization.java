// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.wm.WelcomeScreenCustomization;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenComponentFactory.createShowPopupAction;

public final class WelcomeScreenDefaultCustomization implements WelcomeScreenCustomization {

  @Override
  public @Nullable List<AnAction> createQuickAccessActions(@NotNull Disposable parentDisposable) {
    AnAction result = createShowPopupAction(IdeActions.GROUP_WELCOME_SCREEN_OPTIONS);
    result.getTemplatePresentation().setText(ActionsBundle.groupText(IdeActions.GROUP_WELCOME_SCREEN_OPTIONS));
    result.getTemplatePresentation().setIcon(ExperimentalUI.isNewUI() ? AllIcons.General.Settings : AllIcons.General.GearHover);
    return Collections.singletonList(result);
  }
}
