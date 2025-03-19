// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.ui.laf.intellij.IdeaPopupMenuUI;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.PopupCornerType;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBValue;
import com.intellij.util.ui.StartupUiUtil;
import com.jetbrains.JBR;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public final class WindowRoundedCornersManager {
  public static void configure(@NotNull DialogWrapper dialog) {
    if (isAvailable()) {
      if ((SystemInfoRt.isMac && StartupUiUtil.INSTANCE.isDarkTheme()) || SystemInfoRt.isWindows) {
        setRoundedCorners(dialog.getWindow(), JBUI.CurrentTheme.Popup.borderColor(true));
        dialog.getRootPane().setBorder(PopupBorder.Factory.createEmpty());
      }
      else {
        setRoundedCorners(dialog.getWindow());
      }
    }
  }

  public static boolean isAvailable() {
    if (!JBR.isRoundedCornersManagerSupported()) {
      return false;
    }
    if (!ExperimentalUI.isNewUI() || !Registry.is("ide.popup.rounded.corners", true)) {
      return false;
    }
    if (SystemInfoRt.isWindows) {
      Long buildNumber = SystemInfo.getWinBuildNumber();
      return buildNumber != null && buildNumber.longValue() >= 22000; // Windows 11 only
    }
    return true;
  }

  public static void setRoundedCorners(@NotNull Window window) {
    setRoundedCorners(window, null);
  }

  public static void setRoundedCorners(@NotNull Window window, @Nullable Object params) {
    if (SystemInfoRt.isMac) {
      if (params == null) {
        params = Float.valueOf(IdeaPopupMenuUI.CORNER_RADIUS.getFloat());
      }
      else if (params instanceof PopupCornerType cornerType) {
        if (cornerType == PopupCornerType.None) {
          return;
        }
        JBValue radius =
          cornerType == PopupCornerType.RoundedTooltip ? JBUI.CurrentTheme.Tooltip.CORNER_RADIUS : IdeaPopupMenuUI.CORNER_RADIUS;
        params = Float.valueOf(radius.getFloat());
      }
      else if (params instanceof Color) {
        params = new Object[]{Float.valueOf(IdeaPopupMenuUI.CORNER_RADIUS.getFloat()), Integer.valueOf(1), params};
      }
      else if (params instanceof Object[] values) {
        if (values.length != 2 || !(values[0] instanceof PopupCornerType cornerType) || !(values[1] instanceof Color)) {
          return;
        }
        JBValue radius =
          cornerType == PopupCornerType.RoundedTooltip ? JBUI.CurrentTheme.Tooltip.CORNER_RADIUS : IdeaPopupMenuUI.CORNER_RADIUS;
        params = new Object[]{Float.valueOf(radius.getFloat()), Integer.valueOf(1), values[1]};
      }
      else if (!(params instanceof Float)) {
        return;
      }
    }
    else if (SystemInfo.isWindows) {
      if (params == null) {
        params = "full";
      }
      else if (params instanceof PopupCornerType cornerType) {
        if (cornerType == PopupCornerType.None) {
          return;
        }
        params = cornerType == PopupCornerType.RoundedTooltip ? "small" : "full";
      }
      else if (params instanceof Color) {
        params = new Object[]{"full", params};
      }
      else if (params instanceof Object[] values) {
        if (values.length != 2 || !(values[0] instanceof PopupCornerType cornerType) || !(values[1] instanceof Color)) {
          return;
        }
        params = new Object[]{cornerType == PopupCornerType.RoundedTooltip ? "small" : "full", values[1]};
      }
      else if (!(params instanceof String)) {
        return;
      }
    }

    JBR.getRoundedCornersManager().setRoundedCorners(window, params);
  }
}