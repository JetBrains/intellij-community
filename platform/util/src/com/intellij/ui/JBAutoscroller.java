/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.util.Key;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class JBAutoscroller implements ActionListener {
  private static final int SCROLL_UPDATE_INTERVAL = 15;
  private static final Key<ScrollDeltaProvider> SCROLL_HANDLER_KEY = Key.create("JBAutoScroller.AutoScrollHandler");
  private static final JBAutoscroller INSTANCE = new JBAutoscroller();

  private final Timer myTimer = new Timer(SCROLL_UPDATE_INTERVAL, this);
  private final DefaultScrollDeltaProvider myDefaultAutoScrollHandler = new DefaultScrollDeltaProvider();

  private SyntheticDragEvent myLatestDragEvent;
  private int myHorizontalScrollDelta;
  private int myVerticalScrollDelta;

  private JBAutoscroller() {
  }

  public static void installOn(@NotNull JComponent component) {
    installOn(component, null);
  }

  public static void installOn(@NotNull JComponent component, @Nullable ScrollDeltaProvider handler) {
    INSTANCE.doInstallOn(component, handler);
  }

  private void doInstallOn(@NotNull JComponent component, @Nullable ScrollDeltaProvider handler) {
    component.setAutoscrolls(false); // disable swing autoscroll

    if (handler != null) {
      component.putClientProperty(SCROLL_HANDLER_KEY, handler);
    }

    component.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        start();
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        stop();
      }
    });
    component.addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseDragged(MouseEvent e) {
        if (e instanceof SyntheticDragEvent) return;

        JComponent c = (JComponent)e.getComponent();
        ScrollDeltaProvider handler = (ScrollDeltaProvider)c.getClientProperty(SCROLL_HANDLER_KEY);
        handler = ObjectUtils.notNull(handler, myDefaultAutoScrollHandler);

        myVerticalScrollDelta = handler.getVerticalScrollDelta(e);
        myHorizontalScrollDelta = handler.getHorizontalScrollDelta(e);
        myLatestDragEvent = new SyntheticDragEvent(c, e.getID(), e.getWhen(), e.getModifiers(),
                                                   c.getX(), c.getY(), e.getXOnScreen(), e.getYOnScreen(),
                                                   e.getClickCount(), e.isPopupTrigger(), e.getButton());
      }
    });
  }

  private void start() {
    myVerticalScrollDelta = 0;
    myHorizontalScrollDelta = 0;
    myTimer.start();
  }

  private void stop() {
    myTimer.stop();
    myLatestDragEvent = null;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (myLatestDragEvent == null) return;

    JComponent component = (JComponent)myLatestDragEvent.getComponent();
    if (!component.isShowing()) {
      stop();
      return;
    }

    if (autoscroll()) {
      fireSyntheticDragEvent(e);
    }
  }

  private void fireSyntheticDragEvent(ActionEvent e) {
    Component component = myLatestDragEvent.getComponent();

    Point componentOnScreen = component.getLocationOnScreen();
    int xScreen = myLatestDragEvent.getXOnScreen();
    int yScreen = myLatestDragEvent.getYOnScreen();
    int x = xScreen - componentOnScreen.x;
    int y = yScreen - componentOnScreen.y;
    SyntheticDragEvent dragEvent = new SyntheticDragEvent(component,
                                                          myLatestDragEvent.getID(), e.getWhen(),
                                                          myLatestDragEvent.getModifiers(),
                                                          x, y, xScreen, yScreen,
                                                          myLatestDragEvent.getClickCount(),
                                                          myLatestDragEvent.isPopupTrigger(),
                                                          myLatestDragEvent.getButton());

    for (MouseMotionListener l : component.getMouseMotionListeners()) {
      l.mouseDragged(dragEvent);
    }
  }

  private boolean autoscroll() {
    JScrollPane scrollPane = UIUtil.getParentOfType(JScrollPane.class, myLatestDragEvent.getComponent());
    if (scrollPane == null) return false;

    boolean scrolled = scroll(scrollPane.getVerticalScrollBar(), myVerticalScrollDelta);
    scrolled |= scroll(scrollPane.getHorizontalScrollBar(), myHorizontalScrollDelta);
    return scrolled;
  }

  private static boolean scroll(@Nullable JScrollBar scrollBar, int delta) {
    if (scrollBar == null || delta == 0) return false;

    int oldValue = scrollBar.getValue();
    scrollBar.setValue(scrollBar.getValue() + delta);
    return oldValue != scrollBar.getValue();
  }

  public interface ScrollDeltaProvider {
    int getHorizontalScrollDelta(MouseEvent e);
    int getVerticalScrollDelta(MouseEvent e);
  }

  public static class DefaultScrollDeltaProvider implements ScrollDeltaProvider {
    @Override
    public int getVerticalScrollDelta(MouseEvent e) {
      Rectangle visibleRect = ((JComponent)e.getComponent()).getVisibleRect();
      return getScrollDelta(visibleRect.y, visibleRect.y + visibleRect.height - 1, e.getY());
    }

    @Override
    public int getHorizontalScrollDelta(MouseEvent e) {
      Rectangle visibleRect = ((JComponent)e.getComponent()).getVisibleRect();
      return getScrollDelta(visibleRect.x, visibleRect.x + visibleRect.width - 1, e.getX());
    }

    protected int getScrollDelta(int low, int high, int value) {
      return value - (value > high ? high : value < low ? low : value);
    }
  }

  private static class SyntheticDragEvent extends MouseEvent {
    public SyntheticDragEvent(Component source, int id, long when, int modifiers,
                              int x, int y, int xAbs, int yAbs,
                              int clickCount, boolean popupTrigger, int button) {
      super(source, id, when, modifiers, x, y, xAbs, yAbs, clickCount, popupTrigger, button);
    }
  }
}
