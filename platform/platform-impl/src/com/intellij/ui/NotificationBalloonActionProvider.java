// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.impl.NotificationCollector;
import com.intellij.notification.impl.NotificationsConfigurable;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.icons.CachedImageIcon;
import com.intellij.ui.icons.CustomIconUtilKt;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.util.ui.JBRectangle;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
public final class NotificationBalloonActionProvider implements BalloonImpl.ActionProvider {
  private final BalloonImpl myBalloon;
  private final BalloonLayoutData myLayoutData;
  private final Component myRepaintPanel;
  private final Notification myNotification;
  private BalloonImpl.ActionButton myMoreButton;
  private BalloonImpl.ActionButton myCloseButton;
  private List<BalloonImpl.ActionButton> myActions;

  private static final Rectangle CloseHoverBounds = new JBRectangle(5, 5, 12, 10);

  public NotificationBalloonActionProvider(@NotNull BalloonImpl balloon,
                                           @Nullable Component repaintPanel,
                                           @NotNull BalloonLayoutData layoutData,
                                           @NotNull Notification notification) {
    myLayoutData = layoutData;
    myBalloon = balloon;
    myRepaintPanel = repaintPanel;
    myNotification = notification;
  }

  @Override
  public @NotNull List<BalloonImpl.ActionButton> createActions() {
    myActions = new ArrayList<>();

    if (!myLayoutData.showSettingButton) {
      myMoreButton = null;
    }
    else {
      myMoreButton = myBalloon.new ActionButton(
        AllIcons.Actions.More, null,
        IdeBundle.message("tooltip.turn.notification.off"),
        event -> myBalloon.runWithSmartFadeoutPause(() -> {
          if (!myBalloon.isDisposed()) {
            showMorePopup();
          }
        })) {
        @Override
        protected void paintIcon(@NotNull Graphics g, @NotNull Icon icon) {
          if (ExperimentalUI.isNewUI()) {
            icon = paintHover(g, icon, myButton, CloseHoverBounds.x, CloseHoverBounds.y);
            icon.paintIcon(this, g, CloseHoverBounds.x, CloseHoverBounds.y);
          }
          else {
            super.paintIcon(g, icon);
          }
        }

        @Override
        public void repaint() {
          super.repaint();
          if (myRepaintPanel != null) {
            myRepaintPanel.repaint();
          }
        }
      };
      myActions.add(myMoreButton);

      if (myRepaintPanel != null) {
        myLayoutData.showActions = () -> {
          for (BalloonImpl.ActionButton action : myActions) {
            if (!action.isShowing() || !action.hasPaint()) {
              return Boolean.FALSE;
            }
          }
          return Boolean.TRUE;
        };
      }
    }

    myCloseButton = myBalloon.new ActionButton(
      AllIcons.Ide.Notification.Close, AllIcons.Ide.Notification.CloseHover,
      IdeBundle.message( "tooltip.close.notification", SystemInfo.isMac ? "⌥" : "Alt+"),
      event -> {
        final int modifiers = event.getModifiers();
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(() -> {
          if ((modifiers & InputEvent.ALT_MASK) != 0 && myLayoutData.closeAll != null) {
            myLayoutData.closeAll.run();
          }
          else {
            BalloonLayoutData.MergeInfo mergeInfo = myLayoutData.mergeData;
            if (mergeInfo != null && mergeInfo.linkIds != null) {
              for (BalloonLayoutData.ID id : mergeInfo.linkIds) {
                NotificationCollector.getInstance()
                  .logNotificationBalloonClosedByUser(myLayoutData.project, id.notificationId, id.notificationDisplayId,
                                                      myNotification.getGroupId());
              }
            }
            NotificationCollector.getInstance()
              .logNotificationBalloonClosedByUser(myLayoutData.project, myNotification.id, myNotification.getDisplayId(),
                                                  myNotification.getGroupId());
            myBalloon.hide(true);
          }
        });
      }) {
      @Override
      protected void paintIcon(@NotNull Graphics g, @NotNull Icon icon) {
        icon = paintHover(g, icon, myButton, CloseHoverBounds.x, CloseHoverBounds.y);
        icon.paintIcon(this, g, CloseHoverBounds.x, CloseHoverBounds.y);
      }
    };
    myActions.add(myCloseButton);

    return myActions;
  }

  private static @NotNull Icon paintHover(@NotNull Graphics g, @NotNull Icon icon, @NotNull BaseButtonBehavior button, int x, int y) {
    if (ExperimentalUI.isNewUI()) {
      if (button.isHovered()) {
        Graphics2D g2 = (Graphics2D)g.create();
        try {
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

          float arc = DarculaUIUtil.BUTTON_ARC.getFloat();
          float gap = JBUIScale.scale(2);
          Shape shape =
            new RoundRectangle2D.Float(x - gap, y - gap, icon.getIconWidth() + 2 * gap, icon.getIconHeight() + 2 * gap, arc, arc);

          g2.setColor(JBUI.CurrentTheme.Notification.ICON_HOVER_BACKGROUND);
          g2.fill(shape);

          g2.setColor(JBUI.CurrentTheme.ActionButton.hoverBorder());
          g2.draw(shape);
        }
        finally {
          g2.dispose();
        }
      }
      if (ColorUtil.isDark(JBColor.namedColor("MainToolbar.background")) && JBColor.isBright() && icon instanceof CachedImageIcon) {
        icon = CustomIconUtilKt.loadIconCustomVersionOrScale((CachedImageIcon)icon, 16, true, true);
      }
    }
    return icon;
  }

  private void showMorePopup() {
    DefaultActionGroup group = new MyActionGroup();

    if (NotificationsConfigurationImpl.getInstanceImpl().isRegistered(myNotification.getGroupId())) {
      group.add(new DumbAwareAction(IdeBundle.message("notification.settings.action.text")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
          doShowSettings();
        }
      });
      group.addSeparator();
    }

    //noinspection DialogTitleCapitalization
    group.add(new DumbAwareAction(IdeBundle.message("notifications.toolwindow.dont.show.again.for.this.project")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent event) {
        handleDoNotAsk(true);
      }
    });
    //noinspection DialogTitleCapitalization
    group.add(new DumbAwareAction(IdeBundle.message("notifications.toolwindow.dont.show.again")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent event) {
        handleDoNotAsk(false);
      }
    });

    ListPopup popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(null, group, DataManager.getInstance().getDataContext(myMoreButton), true, null, -1);
    Disposer.register(myBalloon, popup);
    popup.showUnderneathOf(myMoreButton);
  }

  private void doShowSettings() {
    NotificationCollector.getInstance()
      .logNotificationSettingsClicked(myNotification.id, myNotification.getDisplayId(), myNotification.getGroupId());
    final NotificationsConfigurable configurable = new NotificationsConfigurable();
    ShowSettingsUtil.getInstance().editConfigurable(myLayoutData.project, configurable, () -> {
      Runnable runnable = configurable.enableSearch(myNotification.getGroupId());
      if (runnable != null) {
        runnable.run();
      }
    });
  }

  private void handleDoNotAsk(boolean forProject) {
    myNotification.setDoNotAskFor(forProject ? myLayoutData.project : null);
    myNotification.expire();
  }

  @Override
  public void layout(@NotNull Rectangle bounds) {
    Dimension closeSize = myCloseButton.getPreferredSize();
    Insets borderInsets = myBalloon.getShadowBorderInsets();
    int x = bounds.x + bounds.width - borderInsets.right - closeSize.width - myLayoutData.configuration.rightActionsOffset.width;
    int y = bounds.y + borderInsets.top + myLayoutData.configuration.rightActionsOffset.height;
    myCloseButton.setBounds(x - CloseHoverBounds.x, y - CloseHoverBounds.y,
                            closeSize.width + CloseHoverBounds.width, closeSize.height + CloseHoverBounds.height);

    if (myMoreButton != null) {
      Dimension size = myMoreButton.getPreferredSize();
      if (ExperimentalUI.isNewUI()) {
        myMoreButton.setBounds(x - size.width - myLayoutData.configuration.gearCloseSpace - CloseHoverBounds.x, y - CloseHoverBounds.y,
                               size.width + CloseHoverBounds.width, size.height + CloseHoverBounds.height);
      }
      else {
        myMoreButton.setBounds(x - size.width - myLayoutData.configuration.gearCloseSpace, y, size.width, size.height);
      }
    }
  }

  private static final class MyActionGroup extends DefaultActionGroup implements TooltipDescriptionProvider {
    private MyActionGroup() {
      setPopup(true);
    }
  }
}