// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.checkout.CheckoutActionGroup;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated superseded by {@link GetFromVersionControlAction}
 */
@Deprecated
public class GetFromVcsAction extends WelcomePopupAction {

  @Override
  protected void fillActions(DefaultActionGroup group) {
    group.addAll(new CheckoutActionGroup("WelcomeScreen.GetFromVcs").getActions());
  }

  @Override
  protected String getCaption() {
    return null;
  }

  @Override
  protected String getTextForEmpty() {
    return UIBundle.message("welcome.screen.get.from.vcs.action.no.vcs.plugins.with.check.out.action.installed.action.name");
  }

  @Override
  protected boolean isSilentlyChooseSingleOption() {
    return true;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean isEnabled = !Registry.is("vcs.use.new.clone.dialog") && CheckoutProvider.EXTENSION_POINT_NAME.hasAnyExtensions();
    e.getPresentation().setEnabledAndVisible(isEnabled);
  }
}
