/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.*;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.Consumer;
import com.intellij.util.ParameterizedRunnable;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.notification.impl.NotificationsManagerImpl.BORDER_COLOR;
import static com.intellij.notification.impl.NotificationsManagerImpl.FILL_COLOR;

/**
 * @author Alexander Lobas
 */
public class WelcomeBalloonLayoutImpl extends BalloonLayoutImpl {
  private static final String TYPE_KEY = "Type";

  private final ParameterizedRunnable<List<NotificationType>> myListener;
  private final Computable<Point> myButtonLocation;
  private BalloonImpl myPopupBalloon;
  private final BalloonPanel myBalloonPanel = new BalloonPanel();
  private boolean myVisible;

  public WelcomeBalloonLayoutImpl(@NotNull JRootPane parent,
                                  @NotNull Insets insets,
                                  @NotNull ParameterizedRunnable<List<NotificationType>> listener,
                                  @NotNull Computable<Point> buttonLocation) {
    super(parent, insets);
    myListener = listener;
    myButtonLocation = buttonLocation;
  }

  @Override
  public void dispose() {
    super.dispose();
    if (myPopupBalloon != null) {
      Disposer.dispose(myPopupBalloon);
      myPopupBalloon = null;
    }
  }

  @Override
  public void add(@NotNull Balloon balloon, @Nullable Object layoutData) {
    if (layoutData instanceof BalloonLayoutData && ((BalloonLayoutData)layoutData).welcomeScreen) {
      addToPopup((BalloonImpl)balloon, (BalloonLayoutData)layoutData);
    }
    else {
      super.add(balloon, layoutData);
    }
  }

  private void addToPopup(@NotNull BalloonImpl balloon, @NotNull BalloonLayoutData layoutData) {
    layoutData.doLayout = this::layoutPopup;
    layoutData.configuration = layoutData.configuration.replace(JBUI.scale(myPopupBalloon == null ? 7 : 5), JBUI.scale(12));

    if (myPopupBalloon == null) {
      final JScrollPane pane = NotificationsManagerImpl.createBalloonScrollPane(myBalloonPanel, true);
      pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

      pane.getVerticalScrollBar().addComponentListener(new ComponentAdapter() {
        @Override
        public void componentShown(ComponentEvent e) {
          pane.setBorder(IdeBorderFactory.createEmptyBorder(SystemInfo.isMac ? 2 : 1, 0, 1, 1));
        }

        @Override
        public void componentHidden(ComponentEvent e) {
          pane.setBorder(IdeBorderFactory.createEmptyBorder());
        }
      });

      myPopupBalloon =
        new BalloonImpl(pane, BORDER_COLOR, new Insets(0, 0, 0, 0), FILL_COLOR, true, false, false, true, false, true, 0, false, false,
                        null, false, 0, 0, 0, 0, false, null, null, false, false, false, null, false);
      myPopupBalloon.setAnimationEnabled(false);
      myPopupBalloon.setShadowBorderProvider(
        new NotificationBalloonShadowBorderProvider(FILL_COLOR, BORDER_COLOR));
      myPopupBalloon.setHideListener(() -> myPopupBalloon.getComponent().setVisible(false));
      myPopupBalloon.setActionProvider(new BalloonImpl.ActionProvider() {
        private BalloonImpl.ActionButton myAction;

        @NotNull
        @Override
        public List<BalloonImpl.ActionButton> createActions() {
          myAction = myPopupBalloon.new ActionButton(AllIcons.Ide.Notification.Close, null, null, Consumer.EMPTY_CONSUMER);
          return Collections.singletonList(myAction);
        }

        @Override
        public void layout(@NotNull Rectangle bounds) {
          myAction.setBounds(0, 0, 0, 0);
        }
      });
    }

    myBalloonPanel.add(balloon.getContent());
    balloon.getContent().putClientProperty(TYPE_KEY, layoutData.type);
    Disposer.register(balloon, new Disposable() {
      @Override
      public void dispose() {
        myBalloons.remove(balloon);
        myBalloonPanel.remove(balloon.getContent());
        updatePopup();
      }
    });
    myBalloons.add(balloon);

    updatePopup();
  }

  public void showPopup() {
    layoutPopup();
    if (myVisible) {
      myPopupBalloon.getComponent().setVisible(true);
    }
    else {
      myPopupBalloon.show(myLayeredPane);
      myVisible = true;
    }
  }

  @Override
  public void queueRelayout() {
    if (myVisible) {
      layoutPopup();
    }
  }

  private void layoutPopup() {
    Dimension layeredSize = myLayeredPane.getSize();
    Dimension size = new Dimension(myPopupBalloon.getPreferredSize());
    Point location = myButtonLocation.compute();
    int x = layeredSize.width - size.width - 5;
    int fullHeight = location.y;

    if (x > location.x) {
      x = location.x - 20;
    }
    if (size.height > fullHeight) {
      size.height = fullHeight;
    }

    myPopupBalloon.setBounds(new Rectangle(x, fullHeight - size.height, size.width, size.height));
  }

  private void updatePopup() {
    int count = myBalloonPanel.getComponentCount();
    List<NotificationType> types = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      types.add((NotificationType)((JComponent)myBalloonPanel.getComponent(i)).getClientProperty(TYPE_KEY));
    }
    myListener.run(types);

    if (myVisible) {
      if (count == 0) {
        myPopupBalloon.getComponent().setVisible(false);
      }
      else {
        layoutPopup();
      }
    }
  }

  private static class BalloonPanel extends NonOpaquePanel {
    public BalloonPanel() {
      super(new AbstractLayoutManager() {
        @Override
        public Dimension preferredLayoutSize(Container parent) {
          int count = parent.getComponentCount();
          int width = 0;
          int height = 0;
          for (int i = 0; i < count; i++) {
            Dimension size = parent.getComponent(i).getPreferredSize();
            width = Math.max(width, size.width);
            height += size.height;
          }
          height += count - 1;
          return new Dimension(width + JBUI.scale(32), height);
        }

        @Override
        public void layoutContainer(Container parent) {
          int count = parent.getComponentCount();
          int width = parent.getWidth() - JBUI.scale(32);
          int height = parent.getHeight();
          if (count == 1) {
            parent.getComponent(0).setBounds(JBUI.scale(16), 0, width, height);
          }
          else {
            int y = 0;
            for (int i = 0; i < count; i++) {
              Component component = parent.getComponent(i);
              Dimension size = component.getPreferredSize();
              component.setBounds(JBUI.scale(16), y, width, size.height);
              y += size.height + JBUI.scale(2);
            }
          }
        }
      });
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);

      int count = getComponentCount() - 1;
      if (count > 0) {
        int x2 = getWidth() - JBUI.scale(16);
        int y = 0;

        g.setColor(new JBColor(0xD0D0D0, 0x717375));

        for (int i = 0; i < count; i++) {
          Dimension size = getComponent(i).getPreferredSize();
          y += size.height + 1;
          g.drawLine(JBUI.scale(16), y, x2, y);
        }
      }
    }
  }
}