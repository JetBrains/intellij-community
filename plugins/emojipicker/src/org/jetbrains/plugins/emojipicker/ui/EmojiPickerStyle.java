// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.emojipicker.ui;

import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBValue;

import java.awt.*;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;


class EmojiPickerStyle {
  private static final Font EMOJI_FONT = Stream.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts())
    .filter(f -> f.getName().toLowerCase(Locale.ENGLISH).contains("emoji"))
    .findFirst()
    .orElse(null);

  static boolean isEmojiFontAvailable() {
    return EMOJI_FONT != null;
  }

  final Font myFont = JBUI.Fonts.label().deriveFont(Font.PLAIN, JBUIScale.scale(13F));
  final Font myEmojiFont = Objects.requireNonNull(EMOJI_FONT, "No emoji font available").deriveFont(JBUIScale.scale(22F));

  final Color myBackgroundColor = JBUI.CurrentTheme.BigPopup.searchFieldBackground();
  final Color myToolbarColor = JBColor.namedColor("ToolTip.background", JBUI.CurrentTheme.Popup.toolbarPanelColor());
  final Color myHoverBackgroundColor = new JBColor(0xEDF6FE, 0x464A4D);
  final Color myBorderColor = JBColor.namedColor("Borders.ContrastBorderColor", JBUI.CurrentTheme.BigPopup.searchFieldBorderColor());
  final Color mySelectedCategoryColor = new JBColor(0x389FD6, 0x389FD6);
  final Color myFocusBorderColor = new JBColor(0x97C3F3, 0x97C3F3);
  final Color myTextColor = JBUI.CurrentTheme.Label.foreground();
  final Color myNoEmojiFoundTextColor = new JBColor(0x808080, 0xBBBBBB);

  final JBValue myBorder = new JBValue.Float(1);
}
