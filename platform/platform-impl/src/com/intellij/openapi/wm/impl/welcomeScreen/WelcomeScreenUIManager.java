// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.plugins.newui.ListPluginComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.InputStream;
import java.net.URL;

public final class WelcomeScreenUIManager {

  static @NotNull Font getProductFont(int size) {
    try {
      return loadFont().deriveFont((float)JBUIScale.scale(size));
    }
    catch (Throwable t) {
      Logger.getInstance(AppUIUtil.class).warn(t);
    }
    return StartupUiUtil.getLabelFont().deriveFont(JBUIScale.scale((float)size));
  }

  private static @NotNull Font loadFont() {
    @NonNls String fontPath = "/fonts/Roboto-Light.ttf";
    URL url = AppUIUtil.class.getResource(fontPath);
    if (url == null) {
      Logger.getInstance(AppUIUtil.class).warn("Resource missing: " + fontPath);
    }
    else {
      try (InputStream is = url.openStream()) {
        return Font.createFont(Font.TRUETYPE_FONT, is);
      }
      catch (Throwable t) {
        Logger.getInstance(AppUIUtil.class).warn("Cannot load font: " + url, t);
      }
    }
    return StartupUiUtil.getLabelFont();
  }

  public static Color getMainBackground() {
    return JBColor.namedColor("WelcomeScreen.background", new JBColor(0xf7f7f7, 0x45474a));
  }

  public static Color getMainTabListBackground() {
    return JBColor.namedColor("WelcomeScreen.SidePanel.background", new JBColor(0xF2F2F2, 0x3C3F41));
  }

  public static Color getProjectsBackground() {
    return getMainAssociatedComponentBackground();
  }

  public static Color getProjectsSelectionBackground(boolean hasFocus) {
    return ListPluginComponent.SELECTION_COLOR; //use the same as plugins tab use
  }

  public static @NotNull Color getProjectsSelectionForeground(boolean isSelected, boolean hasFocus) {
    return UIUtil.getListForeground(); // do not change foreground for selection
  }

  public static Color getMainAssociatedComponentBackground() {
    return JBColor.namedColor("WelcomeScreen.Details.background", new JBColor(Color.white, new Color(0x313335)));
  }

  public static Color getLinkNormalColor() {
    return new JBColor(Gray._0, Gray.xBB);
  }

  public static Color getActionLinkSelectionColor() {
    return new JBColor(0xdbe5f5, 0x485875);
  }

  public static JBColor getSeparatorColor() {
    return JBColor.namedColor("WelcomeScreen.separatorColor", new JBColor(Gray.xEC, new Color(72, 75, 78)));
  }

  public static JBColor getActionsButtonBackground(boolean isSelected) {
    return isSelected
           ? JBColor.namedColor("WelcomeScreen.Projects.actions.selectionBackground", new JBColor(0x3587E5, 0X326FC1))
           : JBColor.namedColor("WelcomeScreen.Projects.actions.background", new JBColor(0xDCEDFE, 0x3C5C86));
  }

  public static JBColor getActionsButtonSelectionBorder() {
    return JBColor.namedColor("WelcomeScreen.Projects.actions.selectionBorderColor", new JBColor(0x3574F0, 0x3574F0));
  }
}
