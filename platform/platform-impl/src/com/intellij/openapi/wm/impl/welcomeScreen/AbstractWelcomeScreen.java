// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.Strings;
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

public abstract class AbstractWelcomeScreen extends JPanel implements WelcomeScreen {
  private static final @NonNls String ROOT_ID = "root";
  protected final JBSlidingPanel slidingPanel = new JBSlidingPanel();

  protected AbstractWelcomeScreen() {
    super(new BorderLayout());
    slidingPanel.add(ROOT_ID, this);
    ApplicationManager.getApplication().getMessageBus().connect(this)
      .subscribe(WelcomeScreenComponentListener.COMPONENT_CHANGED, new MyWelcomeScreenComponentListener(slidingPanel));
  }

  private static final class MyWelcomeScreenComponentListener implements WelcomeScreenComponentListener {
    JBSlidingPanel slidingPanel;

    MyWelcomeScreenComponentListener(JBSlidingPanel slidingPanel) {
      this.slidingPanel = slidingPanel;
    }

    @Override
    public void attachComponent(@NotNull Component componentToShow, @Nullable Runnable onDone) {
      String name = generateOrGetName(componentToShow, slidingPanel);
      slidingPanel.add(name, componentToShow);
      slidingPanel.getLayout().swipe(slidingPanel, name, JBCardLayout.SwipeDirection.FORWARD, onDone);
    }

    @Override
    public void detachComponent(@NotNull Component componentToDetach, @Nullable Runnable onDone) {
      slidingPanel.swipe(ROOT_ID, JBCardLayout.SwipeDirection.BACKWARD).doWhenDone(() -> {
        if (onDone != null) {
          onDone.run();
        }
        slidingPanel.getLayout().removeLayoutComponent(componentToDetach);
        setTitle(getApplicationTitle());
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
          IdeFocusManager.getGlobalInstance().requestFocus(slidingPanel, true);
        });
      });
    }

    private static String generateOrGetName(@NotNull Component show, @NotNull JBSlidingPanel slidingPanel) {
      String componentName = show.getName();
      if (!Strings.isEmptyOrSpaces(componentName)) {
        return componentName;
      }
      return UniqueNameGenerator.generateUniqueName("welcomeScreenSlidingPanel",
                                                    s -> slidingPanel.getLayout().findComponentById(s) == null);
    }
  }

  @Override
  public void dispose() {
  }

  @Override
  public JComponent getWelcomePanel() {
    return slidingPanel;
  }
}
