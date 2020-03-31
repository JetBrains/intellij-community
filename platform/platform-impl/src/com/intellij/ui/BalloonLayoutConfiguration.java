// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBDimension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public static int MaxFullContentWidth() {
    return JBUIScale.scale(350);
  }

  @NotNull
  public static String MaxFullContentWidthStyle() {
    return "width:" + MaxFullContentWidth() + "px;";
  }

  public static int MinWidth() {
    return JBUIScale.scale(100);
  }

  private static final int RawWidth;
  private static final int RawStyleWidth;

  static {
    int width;

    if (SystemInfo.isMac) {
      width = 360;
      RawStyleWidth = 240;
    }
    else if (SystemInfo.isLinux) {
      width = 410;
      RawStyleWidth = 270;
    }
    else {
      width = 330;
      RawStyleWidth = 205;
    }

    width += AllIcons.Ide.Shadow.Left.getIconWidth();
    width += AllIcons.Ide.Shadow.Right.getIconWidth();

    RawWidth = width;
  }

  public static int FixedWidth() {
    return JBUIScale.scale(RawWidth);
  }

  public static int MaxWidth() {
    return JBUIScale.scale(RawWidth - 60);
  }

  public static String MaxWidthStyle() {
    return "width:" + JBUIScale.scale(RawStyleWidth) + "px;";
  }

  @NotNull
  public static BalloonLayoutConfiguration create(@NotNull Notification notification,
                                                  @NotNull BalloonLayoutData layoutData,
                                                  boolean actions) {
    boolean hasTitle = notification.hasTitle();
    boolean hasContent = notification.hasContent();
    if (hasTitle && hasContent && actions) {
      return treeLines();
    }
    if (hasContent && NotificationsManagerImpl.calculateContentHeight(hasTitle || actions ? 1 : 2) < layoutData.fullHeight) {
      return treeLines();
    }
    return twoLines();
  }

  @NotNull
  public BalloonLayoutConfiguration replace(int topSpaceHeight, int bottomSpaceHeight) {
    return new BalloonLayoutConfiguration(iconPanelWidth, iconOffset, topSpaceHeight, titleContentSpaceHeight, contentActionsSpaceHeight,
                                          titleActionsSpaceHeight, bottomSpaceHeight, actionGap, null, 0, 0, 0);
  }

  @NotNull
  private static BalloonLayoutConfiguration twoLines() {
    return new BalloonLayoutConfiguration(new JBDimension(10, 11),
                                          JBUIScale.scale(11), JBUIScale.scale(5), JBUIScale.scale(5), JBUIScale.scale(5),
                                          JBUIScale.scale(14));
  }

  @NotNull
  private static BalloonLayoutConfiguration treeLines() {
    return new BalloonLayoutConfiguration(new JBDimension(10, 7),
                                          JBUIScale.scale(7), JBUIScale.scale(3), JBUIScale.scale(7), 0, JBUIScale.scale(8));
  }

  private BalloonLayoutConfiguration(@NotNull Dimension iconOffset,
                                     int topSpaceHeight,
                                     int titleContentSpaceHeight,
                                     int contentActionsSpaceHeight,
                                     int titleActionsSpaceHeight,
                                     int bottomSpaceHeight) {
    this(JBUIScale.scale(32), iconOffset,
         topSpaceHeight, titleContentSpaceHeight, contentActionsSpaceHeight, titleActionsSpaceHeight, bottomSpaceHeight,
         JBUIScale.scale(16),
         new JBDimension(8, 6), JBUIScale.scale(7), JBUIScale.scale(5), JBUIScale.scale(15));
  }

  private BalloonLayoutConfiguration(int iconPanelWidth,
                                     @NotNull Dimension iconOffset,
                                     int topSpaceHeight,
                                     int titleContentSpaceHeight,
                                     int contentActionsSpaceHeight,
                                     int titleActionsSpaceHeight,
                                     int bottomSpaceHeight,
                                     int actionGap,
                                     @Nullable Dimension rightActionsOffset,
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

    if (rightActionsOffset == null) {
      this.rightActionsOffset = new Dimension();
      this.closeOffset = 0;
      this.gearCloseSpace = 0;
      this.allActionsOffset = 0;
      this.beforeGearSpace = 0;
    }
    else {
      this.rightActionsOffset = rightActionsOffset;
      this.closeOffset = beforeCloseSpace + AllIcons.Ide.Notification.Close.getIconWidth() + rightActionsOffset.width;
      this.gearCloseSpace = afterGearSpace + beforeCloseSpace;
      this.allActionsOffset = closeOffset + afterGearSpace + AllIcons.Ide.Notification.Gear.getIconWidth();
      this.beforeGearSpace = beforeGearSpace;
    }
  }
}