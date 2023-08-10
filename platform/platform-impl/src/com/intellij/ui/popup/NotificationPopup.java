// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ui.popup;

import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.BalloonLayout;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public final class NotificationPopup {
  public NotificationPopup(final JComponent owner, final JComponent content, Color background) {
    this(owner, content, background, true);
  }

  private NotificationPopup(final JComponent owner, final JComponent content, Color background, boolean useDefaultPreferredSize) {
    this(owner, content, background, useDefaultPreferredSize, null, false);
  }

  private NotificationPopup(final JComponent owner,
                            final JComponent content,
                            Color background,
                            final boolean useDefaultPreferredSize,
                            ActionListener clickHandler,
                            boolean closeOnClick) {
    final IdeFrame frame = findFrame(owner);
    if (frame == null || !((Window)frame).isShowing() || frame.getBalloonLayout() == null) {
      new FramelessNotificationPopup(owner, content, background, useDefaultPreferredSize, clickHandler);
    }
    else {
      final Wrapper wrapper = new NonOpaquePanel(content) {
        @Override
        public Dimension getPreferredSize() {
          final Dimension size = super.getPreferredSize();
          if (useDefaultPreferredSize) {
            if (size.width > 400 || size.height > 200) {
              size.width = 400;
              size.height = 200;
            }
          }
          return size;
        }
      };

      final Balloon balloon = JBPopupFactory.getInstance().createBalloonBuilder(wrapper)
        .setFadeoutTime(5000)
        .setHideOnClickOutside(false)
        .setHideOnFrameResize(false)
        .setHideOnKeyOutside(false)
        .setCloseButtonEnabled(true)
        .setFillColor(background)
        .setShowCallout(false)
        .setClickHandler(clickHandler, closeOnClick)
        .createBalloon();

      BalloonLayout layout = frame.getBalloonLayout();
      assert layout != null;

      layout.add(balloon);
    }
  }

  private static IdeFrame findFrame(JComponent owner) {
    final Window frame = SwingUtilities.getWindowAncestor(owner);
    if (frame instanceof IdeFrame) {
      return (IdeFrame)frame;
    }

    return null;
  }

  public JBPopup getPopup() {
    return null;
  }
}
