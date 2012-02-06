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

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.ui.ListenerUtil;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author max
 */
public class FramelessNotificationPopup {
  private JComponent myContent;
  public static final Dimension myPreferredContentSize = new Dimension(300, 100);
  private JBPopup myPopup;
  private int myTimerTick;
  private Color myBackgroud;
  private final boolean myUseDefaultPreferredSize;
  private final static int FADE_IN_TICKS = 60;
  private final static int SHOW_TIME_TICKS = FADE_IN_TICKS + 300;
  private final static int FADE_OUT_TICKS = SHOW_TIME_TICKS + 60;

  private final ActionListener myFadeTracker = new ActionListener() {
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
  private ActionListener myActionListener;

  public FramelessNotificationPopup(final JComponent owner, final JComponent content, Color backgroud) {
    this(owner, content, backgroud, true, null);
  }

  public FramelessNotificationPopup(final JComponent owner, final JComponent content, Color backgroud, boolean useDefaultPreferredSize, final ActionListener listener) {
    myBackgroud = backgroud;
    myUseDefaultPreferredSize = useDefaultPreferredSize;
    myContent = new ContentComponent(content);

    myActionListener = listener;

    myFadeInTimer = UIUtil.createNamedTimer("Frameless fade in",10, myFadeTracker);
    myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(myContent, null)
      .setRequestFocus(false)
      .setResizable(false)
      .setMovable(true)
      .setLocateWithinScreenBounds(false)
      .setAlpha(0.2f).addListener(new JBPopupAdapter() {
      public void onClosed(LightweightWindowEvent event) {
        if (myFadeInTimer.isRunning()) {
          myFadeInTimer.stop();
        }
        myFadeInTimer.removeActionListener(myFadeTracker);
      }
    })
      .createPopup();
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

  private class ContentComponent extends JPanel {
    private final MouseAdapter myMouseListener;

    public ContentComponent(JComponent content) {
      super(new BorderLayout());
      add(content, BorderLayout.CENTER);
      setBackground(myBackgroud);

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

    public Dimension getPreferredSize() {
      if (myUseDefaultPreferredSize) {
        return myPreferredContentSize;
      }
      return super.getPreferredSize();
    }
  }


}