// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.ui.laf.intellij.IdeaPopupMenuUI;
import com.intellij.openapi.ui.popup.PopupCornerType;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBValue;
import com.jetbrains.JBR;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public final class WindowRoundedCornersManager {
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
    if (SystemInfo.isMac) {
      if (params == null) {
        params = Float.valueOf(IdeaPopupMenuUI.CORNER_RADIUS.getFloat());
      }
      else if (params instanceof PopupCornerType) {
        PopupCornerType cornerType = (PopupCornerType)params;
        if (cornerType == PopupCornerType.None) {
          return;
        }
        JBValue radius =
          cornerType == PopupCornerType.RoundedTooltip ? JBUI.CurrentTheme.Tooltip.CORNER_RADIUS : IdeaPopupMenuUI.CORNER_RADIUS;
        params = Float.valueOf(radius.getFloat());
      }
      else if (!(params instanceof Float)) {
        return;
      }
    }
    else if (SystemInfo.isWindows) {
      if (params == null) {
        params = "full";
      }
      else if (params instanceof PopupCornerType) {
        PopupCornerType cornerType = (PopupCornerType)params;
        if (cornerType == PopupCornerType.None) {
          return;
        }
        params = cornerType == PopupCornerType.RoundedTooltip ? "small" : "full";
      }
      else if (!(params instanceof String)) {
        return;
      }
    }

    JBR.getRoundedCornersManager().setRoundedCorners(window, params);
  }
}