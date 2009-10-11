/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.ui.popup;

import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.components.panels.NonOpaquePanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * @author max
 */
public class NotificationPopup {

  private Impl myImpl;

  public NotificationPopup(final JComponent owner, final JComponent content, Color backgroud) {
    this(owner, content, backgroud, true);
  }

  public NotificationPopup(final JComponent owner, final JComponent content, Color backgroud, boolean useDefaultPreferredSize) {
    this(owner, content, backgroud, useDefaultPreferredSize, null, false);
  }

  public NotificationPopup(final JComponent owner, final JComponent content, Color backgroud, final boolean useDefaultPreferredSize, ActionListener clickHandler, boolean closeOnClick) {
    final IdeFrameImpl frame = findFrame(owner);
    if (frame == null || !frame.isShowing()) {
      //todo kirillk
      if (clickHandler != null) {
        throw new UnsupportedOperationException("Click handler is not supported in frameless mode");
      }
      final FramelessNotificationPopup popup = new FramelessNotificationPopup(owner, content, backgroud, useDefaultPreferredSize);

      myImpl = new Impl() {
        public void addListener(JBPopupListener listener) {
          popup.getPopup().addListener(listener);
        }

        public void hide() {
          popup.getPopup().cancel();
        }
      };
    } else {
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
        .setFillColor(backgroud)
        .setShowCallout(false)
        .setClickHandler(clickHandler, closeOnClick)
        .createBalloon();

      frame.getBalloonLayout().add(balloon);

      myImpl = new Impl() {
        public void addListener(JBPopupListener listener) {
          balloon.addListener(listener);
        }

        public void hide() {
          balloon.hide();
        }
      };
    }
  }

  private IdeFrameImpl findFrame(JComponent owner) {
    final Window frame = SwingUtilities.getWindowAncestor(owner);
    if (frame instanceof IdeFrameImpl) {
      return (IdeFrameImpl)frame;
    }

    return null;
  }

  public JBPopup getPopup() {
    return null;
  }


  interface Impl {
    void addListener(JBPopupListener listener);
    void hide();
  }

  public void addListener(JBPopupListener listener) {
  }

  public void hide() {
  }
}
