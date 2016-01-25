/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.notification.impl.NotificationsConfigurable;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.util.Computable;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class NotificationBalloonActionProvider implements BalloonImpl.ActionProvider {
  private final BalloonImpl myBalloon;
  private final BalloonLayoutData myLayoutData;
  private final String myDisplayGroupId;
  private final Component myRepaintPanel;
  private final BalloonImpl.ActionButton mySettingButton;
  private final BalloonImpl.ActionButton myCloseButton;
  private final List<BalloonImpl.ActionButton> myActions = new ArrayList<BalloonImpl.ActionButton>();

  public NotificationBalloonActionProvider(@NotNull BalloonImpl balloon,
                                           @Nullable Component repaintPanel,
                                           @NotNull BalloonLayoutData layoutData,
                                           @Nullable String displayGroupId) {
    myLayoutData = layoutData;
    myDisplayGroupId = displayGroupId;
    myBalloon = balloon;
    myRepaintPanel = repaintPanel;

    if (myDisplayGroupId == null || !NotificationsConfigurationImpl.getInstanceImpl().isRegistered(myDisplayGroupId)) {
      mySettingButton = null;
    }
    else {
      mySettingButton = myBalloon.new ActionButton(
        icon(AllIcons.Ide.Notification.Gear), icon(AllIcons.Ide.Notification.GearHover),
        new Consumer<MouseEvent>() {
          @Override
          public void consume(MouseEvent event) {
            final NotificationsConfigurable configurable = new NotificationsConfigurable();
            ShowSettingsUtil.getInstance().editConfigurable(myLayoutData.project, configurable, new Runnable() {
              @Override
              public void run() {
                //noinspection ConstantConditions
                configurable.enableSearch(myDisplayGroupId).run();
              }
            });
          }
        }) {
        @Override
        public void repaint() {
          super.repaint();
          if (myRepaintPanel != null) {
            myRepaintPanel.repaint();
          }
        }
      };
      myActions.add(mySettingButton);

      if (repaintPanel != null) {
        layoutData.showActions = new Computable<Boolean>() {
          @Override
          public Boolean compute() {
            for (BalloonImpl.ActionButton action : myActions) {
              if (!action.isShowing() || !action.hasPaint()) {
                return Boolean.FALSE;
              }
            }
            return Boolean.TRUE;
          }
        };
      }
    }

    myCloseButton = myBalloon.new ActionButton(
      icon(AllIcons.Ide.Notification.Close), icon(AllIcons.Ide.Notification.CloseHover),
      new Consumer<MouseEvent>() {
        @Override
        public void consume(MouseEvent event) {
          final int modifiers = event.getModifiers();
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              if ((modifiers & InputEvent.ALT_MASK) != 0) {
                myLayoutData.closeAll.run();
              }
              else {
                myBalloon.hide();
              }
            }
          });
        }
      });
    myActions.add(myCloseButton);
  }

  @NotNull
  @Override
  public List<BalloonImpl.ActionButton> getActions() {
    return myActions;
  }

  @Override
  public void layout(@NotNull Rectangle bounds) {
    Dimension closeSize = myCloseButton.getPreferredSize();
    Insets borderInsets = myBalloon.getShadowBorderInsets();
    int x = bounds.x + bounds.width - closeSize.width - 4 - borderInsets.right;
    int y = bounds.y + 2 + borderInsets.top;
    myCloseButton.setBounds(x, y, closeSize.width, closeSize.height);

    if (mySettingButton != null) {
      Dimension size = mySettingButton.getPreferredSize();
      mySettingButton.setBounds(x - size.width - 12, y, size.width, size.height);
    }
  }

  public static int getCloseOffset() {
    return AllIcons.Ide.Notification.Close.getIconWidth() + 6;
  }

  public static int getAllActionsOffset() {
    return 50;
  }

  @NotNull
  public static Icon icon(@NotNull final Icon icon) {
    //noinspection ConstantConditionalExpression
    return true ? icon : new Icon() {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        icon.paintIcon(c, g, x, y);
        g.setColor(Color.black);
        g.drawRect(x, y, getIconWidth(), getIconHeight());
      }

      @Override
      public int getIconWidth() {
        return icon.getIconWidth();
      }

      @Override
      public int getIconHeight() {
        return icon.getIconHeight();
      }
    };
  }
}