// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.intellij.notification.impl.NotificationsManagerImpl.BORDER_COLOR;
import static com.intellij.notification.impl.NotificationsManagerImpl.FILL_COLOR;

@ApiStatus.Internal
public class WelcomeBalloonLayoutImpl extends BalloonLayoutImpl {
  public static final Topic<BalloonNotificationListener> BALLOON_NOTIFICATION_TOPIC =
    Topic.create("balloon notification changed", BalloonNotificationListener.class);
  protected static final String TYPE_KEY = "Type";

  protected @Nullable Component myLayoutBaseComponent;
  private BalloonImpl myPopupBalloon;
  private final BalloonPanel myBalloonPanel = new BalloonPanel();
  protected boolean myVisible;
  protected Runnable hideListener;

  public WelcomeBalloonLayoutImpl(@NotNull JRootPane parent, @NotNull Insets insets) {
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
  public void add(@NotNull Balloon newBalloon, @Nullable Object layoutData) {
    if (layoutData instanceof BalloonLayoutData
        && ((BalloonLayoutData)layoutData).welcomeScreen
        && newBalloon instanceof BalloonImpl) {
      addToPopup((BalloonImpl)newBalloon, (BalloonLayoutData)layoutData);
    }
    else {
      super.add(newBalloon, layoutData);
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
      myPopupBalloon.setShadowBorderProvider(new NotificationBalloonRoundShadowBorderProvider(FILL_COLOR, BORDER_COLOR));
      myPopupBalloon.setHideListener(() -> {
        if (hideListener != null) {
          hideListener.run();
        }
        myPopupBalloon.getComponent().setVisible(false);
      });
      myPopupBalloon.setActionProvider(new BalloonImpl.ActionProvider() {
        private BalloonImpl.ActionButton myAction;

        @Override
        public @NotNull List<BalloonImpl.ActionButton> createActions() {
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
        balloons.remove(balloon);
        myBalloonPanel.remove(balloon.getContent());
        updatePopup();
      }
    });
    balloons.add(balloon);

    updatePopup();
  }

  public void showPopup() {
    layoutPopup();
    if (myVisible) {
      myPopupBalloon.getComponent().setVisible(true);
    }
    else {
      myPopupBalloon.show(layeredPane);
      myVisible = true;
    }
  }

  @Override
  public void queueRelayout() {
    if (myVisible) {
      layoutPopup();
    }
  }

  public void setHideListener(Runnable hideListener) {
    this.hideListener = hideListener;
  }

  private void layoutPopup() {
    if (myLayoutBaseComponent == null || myPopupBalloon == null) {
      // if no component set - use default location on the LayeredPane
      if (myPopupBalloon != null) {
        myPopupBalloon.setBounds(null);
      }
      return;
    }

    Dimension layeredSize = Objects.requireNonNull(layeredPane).getSize();
    Dimension size = new Dimension(myPopupBalloon.getPreferredSize());
    Point location = SwingUtilities.convertPoint(myLayoutBaseComponent, 0, 0, layeredPane);
    int x = layeredSize.width - size.width;
    int fullHeight = location.y;

    if (x > location.x) {
      x = Math.max(location.x - 20, 0);
    }
    if (size.height > fullHeight) {
      size.height = fullHeight;
    }

    int locationX = x - JBUI.scale(10);
    int locationY = fullHeight - size.height;

    if (size.height < fullHeight) {
      locationY -= JBUI.scale(5);
    }

    myPopupBalloon.setBounds(new Rectangle(locationX, locationY, size.width, size.height));
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

  public @Nullable Component getLocationComponent() {
    return myLayoutBaseComponent;
  }

  public void setLocationComponent(@Nullable Component component) {
    myLayoutBaseComponent = component;
  }

  public interface BalloonNotificationListener {
    void notificationsChanged(List<NotificationType> types);
    default void newNotifications() {}
  }

  private static final class BalloonPanel extends NonOpaquePanel {
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

        g.setColor(JBColor.namedColor("Notification.WelcomeScreen.separatorColor", new JBColor(0xD0D0D0, 0x717375)));

        for (int i = 0; i < count; i++) {
          Dimension size = getComponent(i).getPreferredSize();
          y += size.height + VERTICAL_GAP;
          g.drawLine(JBUIScale.scale(16), y, x2, y);
        }
      }
    }
  }
}