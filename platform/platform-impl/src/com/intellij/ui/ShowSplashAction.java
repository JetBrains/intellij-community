// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

import java.awt.event.*;
import java.util.Objects;

import static com.intellij.platform.ide.bootstrap.SplashManagerKt.loadSplashImage;

/**
 * @author Konstantin Bulenkov
 */
public final class ShowSplashAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Splash splash = new Splash(Objects.requireNonNull(loadSplashImage(ApplicationInfoImpl.getShadowInstance())));
    splash.setVisible(true);
    splash.toFront();

    SplashListener listener = new SplashListener(splash);
    splash.addFocusListener(listener);
    splash.addKeyListener(listener);
    splash.addMouseListener(listener);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static final class SplashListener implements KeyListener, MouseListener, FocusListener {
    private final Splash mySplash;

    private SplashListener(Splash splash) {
      mySplash = splash;
    }

    private void close() {
      if (mySplash.isVisible()) {
        mySplash.setVisible(false);
      }
    }

    @Override
    public void focusGained(FocusEvent e) {
    }

    @Override
    public void focusLost(FocusEvent e) {
      close();
    }

    @Override
    public void keyTyped(KeyEvent e) {
      close();
    }

    @Override
    public void keyPressed(KeyEvent e) {
      close();
    }

    @Override
    public void keyReleased(KeyEvent e) {
      close();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      close();
    }

    @Override
    public void mousePressed(MouseEvent e) {
      close();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      close();
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }
  }
}
