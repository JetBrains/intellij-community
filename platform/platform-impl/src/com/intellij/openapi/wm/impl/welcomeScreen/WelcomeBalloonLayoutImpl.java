// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.*;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.EmptyConsumer;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.JBInsets;
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

public class WelcomeBalloonLayoutImpl extends BalloonLayoutImpl {

  public static final Topic<BalloonNotificationListener> BALLOON_NOTIFICATION_TOPIC =
    Topic.create("balloon notification changed", BalloonNotificationListener.class);
  private static final String TYPE_KEY = "Type";

  private @Nullable Component myLayoutBaseComponent;
  private BalloonImpl myPopupBalloon;
  private final BalloonPanel myBalloonPanel = new BalloonPanel();
  private boolean myVisible;

  public WelcomeBalloonLayoutImpl(@NotNull JRootPane parent,
                                  @NotNull Insets insets) {
    super(parent, insets);
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
    if (layoutData instanceof BalloonLayoutData
        && ((BalloonLayoutData)layoutData).welcomeScreen
        && balloon instanceof BalloonImpl) {
      addToPopup((BalloonImpl)balloon, (BalloonLayoutData)layoutData);
    }
    else {
      super.add(balloon, layoutData);
    }
  }

  private void addToPopup(@NotNull BalloonImpl balloon, @NotNull BalloonLayoutData layoutData) {
    layoutData.doLayout = this::layoutPopup;
    int i = myPopupBalloon == null ? 7 : 5;
    layoutData.configuration = layoutData.configuration.replace(JBUIScale.scale(i), JBUIScale.scale(12));

    if (myPopupBalloon == null) {
      final JScrollPane pane = NotificationsManagerImpl.createBalloonScrollPane(myBalloonPanel, true);
      pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

      pane.getVerticalScrollBar().addComponentListener(new ComponentAdapter() {
        @Override
        public void componentShown(ComponentEvent e) {
          int top = SystemInfo.isMac ? 2 : 1;
          pane.setBorder(JBUI.Borders.empty(top, 0, 1, 1));
        }

        @Override
        public void componentHidden(ComponentEvent e) {
          pane.setBorder(JBUI.Borders.empty());
        }
      });

      myPopupBalloon =
        new BalloonImpl(pane, BORDER_COLOR, JBInsets.emptyInsets(), FILL_COLOR, true, false, false, true, false, true, 0, false, false,
                        null, false, 0, 0, 0, 0, false, null, null, false, false, true, null, false, null, -1);
      myPopupBalloon.setAnimationEnabled(false);
      myPopupBalloon.setShadowBorderProvider(
        new NotificationBalloonShadowBorderProvider(FILL_COLOR, BORDER_COLOR));
      myPopupBalloon.setHideListener(() -> myPopupBalloon.getComponent().setVisible(false));
      myPopupBalloon.setActionProvider(new BalloonImpl.ActionProvider() {
        private BalloonImpl.ActionButton myAction;

        @NotNull
        @Override
        public List<BalloonImpl.ActionButton> createActions() {
          myAction = myPopupBalloon.new ActionButton(AllIcons.Ide.Notification.Close, null, null, EmptyConsumer.getInstance());
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
    if (myLayoutBaseComponent == null) {
      // if no component set - use default location on the LayeredPane
      myPopupBalloon.setBounds(null);
      return;
    }

    Dimension layeredSize = myLayeredPane.getSize();
    Dimension size = new Dimension(myPopupBalloon.getPreferredSize());
    Point point = SwingUtilities.convertPoint(myLayoutBaseComponent, 0, 0, myLayeredPane);
    Point location = new Point(point.x, point.y + 5);
    int x = layeredSize.width - size.width - 5;
    int fullHeight = location.y;

    if (x > location.x) {
      x = Math.max(location.x - 20, 0);
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

    ApplicationManager.getApplication().getMessageBus().syncPublisher(BALLOON_NOTIFICATION_TOPIC).notificationsChanged(types);

    if (myVisible) {
      if (count == 0) {
        myPopupBalloon.getComponent().setVisible(false);
      }
      else {
        layoutPopup();
      }
    }
  }

  @Nullable
  public Component getLocationComponent() {
    return myLayoutBaseComponent;
  }

  public void setLocationComponent(@Nullable Component component) {
    myLayoutBaseComponent = component;
  }

  public interface BalloonNotificationListener {
    void notificationsChanged(List<NotificationType> types);
  }

  private static class BalloonPanel extends NonOpaquePanel {
    private static final int VERTICAL_GAP = JBUIScale.scale(2);

    BalloonPanel() {
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
          height += VERTICAL_GAP * (count - 1);
          return new Dimension(Math.max(width + JBUIScale.scale(32), BalloonLayoutConfiguration.MaxWidth()), height);
        }

        @Override
        public void layoutContainer(Container parent) {
          int count = parent.getComponentCount();
          int width = parent.getWidth() - JBUIScale.scale(32);
          int height = parent.getHeight();
          if (count == 1) {
            parent.getComponent(0).setBounds(JBUIScale.scale(16), 0, width, height);
          }
          else {
            int y = 0;
            for (int i = 0; i < count; i++) {
              Component component = parent.getComponent(i);
              Dimension size = component.getPreferredSize();
              component.setBounds(JBUIScale.scale(16), y, width, size.height);
              y += size.height + VERTICAL_GAP;
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
        int x2 = getWidth() - JBUIScale.scale(16);
        int y = 0;

        g.setColor(new JBColor(0xD0D0D0, 0x717375));

        for (int i = 0; i < count; i++) {
          Dimension size = getComponent(i).getPreferredSize();
          y += size.height + VERTICAL_GAP;
          g.drawLine(JBUIScale.scale(16), y, x2, y);
        }
      }
    }
  }
}