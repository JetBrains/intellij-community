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
package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class BalloonLayoutConfiguration {
  public final int iconPanelWidth;
  public final Dimension iconOffset;

  public final int topSpaceHeight;
  public final int titleContentSpaceHeight;
  public final int contentActionsSpaceHeight;
  public final int titleActionsSpaceHeight;
  public final int bottomSpaceHeight;

  public final int actionGap;

  public final Dimension rightActionsOffset;
  public final int closeOffset;
  public final int gearCloseSpace;
  public final int allActionsOffset;
  public final int beforeGearSpace;

  public static final int FixedWidth;
  public static final int MaxWidth;

  public static final int MinWidth = JBUI.scale(100);

  public static final String MaxWidthStyle;

  static {
    int width;
    int styleWidth;

    if (SystemInfo.isMac) {
      width = 360;
      styleWidth = 240;
    }
    else if (SystemInfo.isLinux) {
      width = 410;
      styleWidth = 270;
    }
    else {
      width = 330;
      styleWidth = 205;
    }

    width += AllIcons.Ide.Notification.Shadow.Left.getIconWidth();
    width += AllIcons.Ide.Notification.Shadow.Right.getIconWidth();

    FixedWidth = JBUI.scale(width);
    MaxWidth = JBUI.scale(width - 60);
    MaxWidthStyle = "width:" + JBUI.scale(styleWidth) + "px;";
  }

  public static final int NotificationSpace = JBUI.scale(10);

  @NotNull
  public static BalloonLayoutConfiguration create(@NotNull Notification notification, @NotNull BalloonLayoutData layoutData) {
    boolean title = notification.isTitle();
    boolean content = notification.isContent();
    boolean actions = !notification.getActions().isEmpty();
    if (title && content && actions) {
      return treeLines();
    }
    if (content && NotificationsManagerImpl.calculateContentHeight(title || actions ? 1 : 2) < layoutData.fullHeight) {
      return treeLines();
    }
    return twoLines();
  }

  @NotNull
  private static BalloonLayoutConfiguration twoLines() {
    return new BalloonLayoutConfiguration(new JBDimension(10, 11),
                                          JBUI.scale(11), JBUI.scale(5), JBUI.scale(5), JBUI.scale(5), JBUI.scale(14));
  }

  @NotNull
  private static BalloonLayoutConfiguration treeLines() {
    return new BalloonLayoutConfiguration(new JBDimension(10, 7),
                                          JBUI.scale(7), JBUI.scale(3), JBUI.scale(7), 0, JBUI.scale(8));
  }

  private BalloonLayoutConfiguration(@NotNull Dimension iconOffset,
                                     int topSpaceHeight,
                                     int titleContentSpaceHeight,
                                     int contentActionsSpaceHeight,
                                     int titleActionsSpaceHeight,
                                     int bottomSpaceHeight) {
    this(JBUI.scale(32), iconOffset,
         topSpaceHeight, titleContentSpaceHeight, contentActionsSpaceHeight, titleActionsSpaceHeight, bottomSpaceHeight,
         JBUI.scale(16),
         new JBDimension(8, 6), JBUI.scale(7), JBUI.scale(5), JBUI.scale(15));
  }

  private BalloonLayoutConfiguration(int iconPanelWidth,
                                     @NotNull Dimension iconOffset,
                                     int topSpaceHeight,
                                     int titleContentSpaceHeight,
                                     int contentActionsSpaceHeight,
                                     int titleActionsSpaceHeight,
                                     int bottomSpaceHeight,
                                     int actionGap,
                                     @NotNull Dimension rightActionsOffset,
                                     int afterGearSpace,
                                     int beforeCloseSpace,
                                     int beforeGearSpace) {
    this.iconPanelWidth = iconPanelWidth;
    this.iconOffset = iconOffset;
    this.topSpaceHeight = topSpaceHeight;
    this.titleContentSpaceHeight = titleContentSpaceHeight;
    this.contentActionsSpaceHeight = contentActionsSpaceHeight;
    this.titleActionsSpaceHeight = titleActionsSpaceHeight;
    this.bottomSpaceHeight = bottomSpaceHeight;
    this.actionGap = actionGap;
    this.rightActionsOffset = rightActionsOffset;

    this.closeOffset = beforeCloseSpace + AllIcons.Ide.Notification.Close.getIconWidth() + rightActionsOffset.width;
    this.gearCloseSpace = afterGearSpace + beforeCloseSpace;
    this.allActionsOffset = closeOffset + afterGearSpace + AllIcons.Ide.Notification.Gear.getIconWidth();
    this.beforeGearSpace = beforeGearSpace;
  }
}