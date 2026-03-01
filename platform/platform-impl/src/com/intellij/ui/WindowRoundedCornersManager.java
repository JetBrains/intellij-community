// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.ui.laf.intellij.IdeaPopupMenuUI;
import com.intellij.idea.AppMode;
import com.intellij.openapi.client.ClientSystemInfo;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.PopupCornerType;
import com.intellij.openapi.util.WinBuildNumber;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.system.OS;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.jetbrains.JBR;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.awt.Window;

/**
 * @author Alexander Lobas
 */
public final class WindowRoundedCornersManager {
  private static final String PARAMS_KEY = "WindowRoundedCorners.parameters";

  public static void configure(@NotNull DialogWrapper dialog) {
    if (isAvailable()) {
      if ((OS.CURRENT == OS.macOS && StartupUiUtil.INSTANCE.isDarkTheme()) || OS.CURRENT == OS.Windows) {
        setRoundedCorners(dialog.getWindow(), JBUI.CurrentTheme.Popup.borderColor(true));
        dialog.getRootPane().setBorder(PopupBorder.Factory.createEmpty());
      }
      else {
        setRoundedCorners(dialog.getWindow());
      }
    }
  }

  public static boolean isAvailable() {
    if (AppMode.isRemoteDevHost()) {
      ClientSystemInfo clientSystemInfo = ClientSystemInfo.getInstance();
      Boolean clientValue = clientSystemInfo == null ? null : clientSystemInfo.getWindowRoundedCornersManagerAvailable();
      // In case information about the client is unavailable we return 'true', as it's a more probable variant.
      return clientValue == null || clientValue;
    }
    if (!JBR.isRoundedCornersManagerSupported()) {
      return false;
    }
    if (!ExperimentalUI.isNewUI() || !Registry.is("ide.popup.rounded.corners", true)) {
      return false;
    }
    if (OS.CURRENT == OS.Windows) {
      var buildNumber = WinBuildNumber.getWinBuildNumber();
      return buildNumber != null && buildNumber >= 22000; // Windows 11 only
    }
    return true;
  }

  public static void setRoundedCorners(@NotNull Window window) {
    setRoundedCorners(window, null);
  }

  public static void setRoundedCorners(@NotNull Window window, @Nullable Object params) {
    if (OS.CURRENT == OS.macOS) {
      if (params == null) {
        params = Float.valueOf(IdeaPopupMenuUI.CORNER_RADIUS.getFloat());
      }
      else if (params instanceof PopupCornerType cornerType) {
        if (cornerType == PopupCornerType.None) {
          return;
        }
        var radius = cornerType == PopupCornerType.RoundedTooltip ? JBUI.CurrentTheme.Tooltip.CORNER_RADIUS : IdeaPopupMenuUI.CORNER_RADIUS;
        params = Float.valueOf(radius.getFloat());
      }
      else if (params instanceof Color) {
        params = new Object[]{Float.valueOf(IdeaPopupMenuUI.CORNER_RADIUS.getFloat()), Integer.valueOf(1), params};
      }
      else if (params instanceof Object[] values) {
        if (values.length != 2 || !(values[0] instanceof PopupCornerType cornerType) || !(values[1] instanceof Color)) {
          return;
        }
        var radius = cornerType == PopupCornerType.RoundedTooltip ? JBUI.CurrentTheme.Tooltip.CORNER_RADIUS : IdeaPopupMenuUI.CORNER_RADIUS;
        params = new Object[]{Float.valueOf(radius.getFloat()), Integer.valueOf(1), values[1]};
      }
      else if (!(params instanceof Float)) {
        return;
      }
    }
    else if (OS.CURRENT == OS.Windows || StartupUiUtil.isWaylandToolkit()) {
      if (params == null) {
        params = defaultCornerRadiusString();
      }
      else if (params instanceof PopupCornerType cornerType) {
        if (cornerType == PopupCornerType.None) {
          return;
        }
        params = cornerType == PopupCornerType.RoundedTooltip ? "small" : defaultCornerRadiusString();
      }
      else if (params instanceof Color) {
        params = new Object[]{defaultCornerRadiusString(), params};
      }
      else if (params instanceof Object[] values) {
        if (values.length != 2 || !(values[0] instanceof PopupCornerType cornerType) || !(values[1] instanceof Color)) {
          return;
        }
        params = new Object[]{cornerType == PopupCornerType.RoundedTooltip ? "small" : defaultCornerRadiusString(), values[1]};
      }
      else if (!(params instanceof String)) {
        return;
      }
    }

    ClientProperty.put(window, PARAMS_KEY, params);
    if (!AppMode.isRemoteDevHost()) {
      JBR.getRoundedCornersManager().setRoundedCorners(window, params);
    }
  }

  private static @NotNull String defaultCornerRadiusString() {
    return StartupUiUtil.isWaylandToolkit() ? "small" : "full";
  }

  @ApiStatus.Internal
  @Nullable
  public static Object getRoundedCornersParams(@NotNull Window window) {
    return ClientProperty.get(window, PARAMS_KEY);
  }
}
