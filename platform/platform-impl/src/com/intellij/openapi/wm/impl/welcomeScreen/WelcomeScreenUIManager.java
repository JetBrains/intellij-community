// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.plugins.newui.ListPluginComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.InputStream;
import java.net.URL;

public class WelcomeScreenUIManager {

  @NotNull
  static Font getProductFont(int size) {
    try {
      return loadFont().deriveFont((float)JBUIScale.scale(size));
    }
    catch (Throwable t) {
      Logger.getInstance(AppUIUtil.class).warn(t);
    }
    return StartupUiUtil.getLabelFont().deriveFont(JBUIScale.scale((float)size));
  }

  @NotNull
  private static Font loadFont() {
    @SuppressWarnings("SpellCheckingInspection")
    String fontPath = "/fonts/Roboto-Light.ttf";
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
    return JBColor.namedColor("WelcomeScreen.List.background", new JBColor(0xF2F2F2, 0x393939));
  }

  public static Color getProjectsBackground() {
    if (Registry.is("use.tabbed.welcome.screen")) {
      return getMainAssociatedComponentBackground();
    }
    return JBColor.namedColor("WelcomeScreen.Projects.background", new JBColor(Gray.xFF, Gray.x39));
  }

  public static Color getProjectsSelectionBackground(boolean hasFocus) {
    if (Registry.is("use.tabbed.welcome.screen")) {
      return ListPluginComponent.HOVER_COLOR; //use the same as plugins tab use
    }
    return hasFocus ? JBColor.namedColor("WelcomeScreen.Projects.selectionBackground", new JBColor(0x3875d6, 0x4b6eaf))
                    : JBColor.namedColor("WelcomeScreen.Projects.selectionInactiveBackground", new JBColor(Gray.xDD, Gray.x45));
  }

  @NotNull
  public static Color getProjectsSelectionForeground(boolean isSelected, boolean hasFocus) {
    if (Registry.is("use.tabbed.welcome.screen")) {
      return UIUtil.getListForeground(); // do not change foreground for selection
    }
    return UIUtil.getListForeground(isSelected && hasFocus, true);// do not change foreground for selection when focused
  }

  public static Color getMainAssociatedComponentBackground() {
    return JBColor.namedColor("WelcomeScreen.AssociatedComponent.background", JBColor.WHITE);
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
    return isSelected ? JBColor.namedColor("WelcomeScreen.Projects.actions.selectionBackground", new JBColor(0x4F96E8, 0X326FC1))
                      : JBColor
             .namedColor("WelcomeScreen.Projects.actions.background", new JBColor(0xDCEDFE, 0x3C5C86));
  }
}
