// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;

import java.awt.*;

public final class WelcomeScreenColors {

  // These two for the topmost "Welcome to <product name>"
  static final Color WELCOME_HEADER_BACKGROUND = JBColor.namedColor("WelcomeScreen.headerBackground", new JBColor(Gray._220, Gray._75));
  static final Color WELCOME_HEADER_FOREGROUND = JBColor.namedColor("WelcomeScreen.headerForeground", new JBColor(Gray._80, Gray._197));

  // This is for border around recent projects, action cards and also lines separating header and footer from main contents.
  static final Color BORDER_COLOR = JBColor.namedColor("WelcomeScreen.borderColor", new JBColor(Gray._190, Gray._85));

  // This is for circle around hovered (next) icon
  static final Color GROUP_ICON_BORDER_COLOR = JBColor.namedColor("WelcomeScreen.groupIconBorderColor", new JBColor(Gray._190, Gray._55));

  // These two are for footer (Full product with build #, small letters)
  static final Color FOOTER_BACKGROUND = JBColor.namedColor("WelcomeScreen.footerBackground", new JBColor(Gray._210, Gray._75));
  static final Color FOOTER_FOREGROUND = JBColor.namedColor("WelcomeScreen.footerForeground", new JBColor(Gray._0, Gray._197));

  // There two are for caption of Recent Project and Action Cards
  static final Color CAPTION_BACKGROUND = JBColor.namedColor("WelcomeScreen.captionBackground", new JBColor(Gray._210, Gray._75));
  static final Color CAPTION_FOREGROUND = JBColor.namedColor("WelcomeScreen.captionForeground", new JBColor(Gray._0, Gray._197));

  private WelcomeScreenColors() {}
}
