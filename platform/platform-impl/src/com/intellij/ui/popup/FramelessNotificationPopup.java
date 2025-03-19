// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup;

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.ui.ListenerUtil;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.TimerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

final class FramelessNotificationPopup {
  private final JComponent myContent;
  public static final Dimension myPreferredContentSize = JBUI.size(300, 100);
  private final JBPopup myPopup;
  private int myTimerTick;
  private final Color background;
  private final boolean myUseDefaultPreferredSize;
  private static final int FADE_IN_TICKS = 60;
  private static final int SHOW_TIME_TICKS = FADE_IN_TICKS + 300;
  private static final int FADE_OUT_TICKS = SHOW_TIME_TICKS + 60;

  private final ActionListener myFadeTracker = new ActionListener() {
    @Override
    public void actionPerformed(ActionEvent e) {
      Window popupWindow = SwingUtilities.windowForComponent(myContent);
      if (popupWindow != null) {
        myTimerTick++;
        if (myTimerTick < FADE_IN_TICKS) {
          popupWindow.setLocation(popupWindow.getLocation().x,  popupWindow.getLocation().y - 2);
        }
        else if (myTimerTick > FADE_OUT_TICKS) {
          myPopup.cancel();
          myFadeInTimer.stop();
        }
        else if (myTimerTick > SHOW_TIME_TICKS) {
          popupWindow.setLocation(popupWindow.getLocation().x,  popupWindow.getLocation().y + 2);
        }
      }
    }
  };
  private final Timer myFadeInTimer;
  private final ActionListener myActionListener;

  FramelessNotificationPopup(final JComponent owner,
                             final JComponent content,
                             Color background,
                             boolean useDefaultPreferredSize,
                             final ActionListener listener) {
    this.background = background;
    myUseDefaultPreferredSize = useDefaultPreferredSize;
    myContent = new ContentComponent(content);

    myActionListener = listener;

    myFadeInTimer = TimerUtil.createNamedTimer("Frameless fade in", 10, myFadeTracker);
    myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(myContent, null)
      .setRequestFocus(false)
      .setResizable(false)
      .setMovable(true)
      .setLocateWithinScreenBounds(false)
      .setAlpha(0.2f)
      .addListener(new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          if (myFadeInTimer.isRunning()) {
            myFadeInTimer.stop();
          }
          myFadeInTimer.removeActionListener(myFadeTracker);
        }
      }).createPopup();
    final Point p = RelativePoint.getSouthEastOf(owner).getScreenPoint();
    Rectangle screen = ScreenUtil.getScreenRectangle(p.x, p.y);

    final Point initial = new Point(screen.x + screen.width - myContent.getPreferredSize().width - 50,
                                    screen.y + screen.height - 5);

    myPopup.showInScreenCoordinates(owner, initial);

    myFadeInTimer.setRepeats(true);
    myFadeInTimer.start();
  }

  public JBPopup getPopup() {
    return myPopup;
  }

  private final class ContentComponent extends JPanel {
    private final MouseAdapter myMouseListener;

    ContentComponent(JComponent content) {
      super(new BorderLayout());
      add(content, BorderLayout.CENTER);
      setBackground(background);

      myMouseListener = new MouseAdapter() {
        @Override
        public void mouseClicked(final MouseEvent e) {
          if (e.getButton() == MouseEvent.BUTTON3) {
            myPopup.cancel();
          } else if (UIUtil.isActionClick(e)) {
            if (myActionListener != null) {
              myActionListener.actionPerformed(new ActionEvent(FramelessNotificationPopup.this, ActionEvent.ACTION_PERFORMED, null));
            }
          }
        }

        @Override
        public void mouseEntered(MouseEvent e) {
          if (myFadeInTimer.isRunning()) {
            myFadeInTimer.stop();
          }
        }

        @Override
        public void mouseExited(MouseEvent e) {
          if (!myFadeInTimer.isRunning()) {
            myFadeInTimer.start();
          }
        }
      };
    }

    @Override
    public void addNotify() {
      super.addNotify();
      ListenerUtil.addMouseListener(this, myMouseListener);
    }

    @Override
    public void removeNotify() {
      super.removeNotify();
      ListenerUtil.removeMouseListener(this, myMouseListener);
    }

    @Override
    public Dimension getPreferredSize() {
      if (myUseDefaultPreferredSize) {
        return myPreferredContentSize;
      }
      return super.getPreferredSize();
    }
  }


}