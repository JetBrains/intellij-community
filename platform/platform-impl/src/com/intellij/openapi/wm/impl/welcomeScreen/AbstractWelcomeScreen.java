// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WelcomeScreen;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.components.JBSlidingPanel;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.wm.impl.welcomeScreen.ActionGroupPanelWrapper.setTitle;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenComponentFactory.getApplicationTitle;

public abstract class AbstractWelcomeScreen extends JPanel implements WelcomeScreen, DataProvider, WelcomeScreenComponentListener {
  @NonNls private static final String ROOT_ID = "root";
  protected final JBSlidingPanel mySlidingPanel = new JBSlidingPanel();

  protected AbstractWelcomeScreen() {
    super(new BorderLayout());
    mySlidingPanel.add(ROOT_ID, this);
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(COMPONENT_CHANGED, this);
  }

  @Override
  public void dispose() {
  }

  @Override
  public JComponent getWelcomePanel() {
    return mySlidingPanel;
  }

  @Override
  public void attachComponent(@NotNull Component componentToShow, @Nullable Runnable onDone) {
    String name = generateOrGetName(componentToShow);
    mySlidingPanel.add(name, componentToShow);
    mySlidingPanel.getLayout().swipe(mySlidingPanel, name, JBCardLayout.SwipeDirection.FORWARD, onDone);
  }

  private String generateOrGetName(@NotNull Component show) {
    String componentName = show.getName();
    if (!StringUtil.isEmptyOrSpaces(componentName)) return componentName;
    return UniqueNameGenerator
      .generateUniqueName("welcomeScreenSlidingPanel", s -> mySlidingPanel.getLayout().findComponentById(s) == null);
  }

  @Override
  public void detachComponent(@NotNull Component componentToDetach, @Nullable Runnable onDone) {
    mySlidingPanel.swipe(ROOT_ID, JBCardLayout.SwipeDirection.BACKWARD).doWhenDone(() -> {
      if (onDone != null) {
        onDone.run();
      }
      mySlidingPanel.getLayout().removeLayoutComponent(componentToDetach);
      setTitle(getApplicationTitle());
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(
        () -> IdeFocusManager.getGlobalInstance().requestFocus(mySlidingPanel, true));
    });
  }
}
