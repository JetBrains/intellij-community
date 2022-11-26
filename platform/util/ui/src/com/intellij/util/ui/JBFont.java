// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.UIResource;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class JBFont extends Font {
  JBFont(@NotNull Font font) {
    super(font);
  }

  @NotNull
  public static JBFont label() {
    return create(UIManager.getFont("Label.font"), false);
  }

  public static JBFont create(Font font) {
    return create(font, true);
  }

  @NotNull
  public static JBFont create(@NotNull Font font, boolean tryToScale) {
    if (font instanceof JBFont) {
      return ((JBFont)font);
    }
    Font scaled = font;
    if (tryToScale) {
      scaled = font.deriveFont(font.getSize() * JBUIScale.scale(1f));
    }

    if (font instanceof UIResource) {
      return new JBFontUIResource(scaled);
    }

    return new JBFont(scaled);
  }

  public JBFont asBold() {
    return deriveFont(BOLD, getSize());
  }

  public JBFont asItalic() {
    return deriveFont(ITALIC, getSize());
  }

  public JBFont asPlain() {
    return deriveFont(PLAIN, getSize());
  }

  @Override
  public JBFont deriveFont(int style, float size) {
    Font font = super.deriveFont(style, size);
    return this instanceof JBFontUIResource ? new JBFontUIResource(font) : new JBFont(font);
  }

  @Override
  public JBFont deriveFont(float size) {
    return deriveFont(getStyle(), size);
  }

  public JBFont biggerOn(float size) {
    return deriveFont(getSize() + JBUIScale.scale(size));
  }

  public JBFont lessOn(float size) {
    return deriveFont(getSize() - JBUIScale.scale(size));
  }

  static final class JBFontUIResource extends JBFont implements UIResource {
     JBFontUIResource(Font font) {
      super(font);
    }
  }

  public static JBFont h0() { return label().biggerOn(12).asBold(); }
  public static JBFont h1() { return label().biggerOn(9).asBold(); }
  public static JBFont h2() { return label().biggerOn(5); }
  public static JBFont h3() { return label().biggerOn(3); }
  public static JBFont h4() { return label().biggerOn(1).asBold(); }
  public static JBFont regular() { return label(); }
  public static JBFont medium() { return mediumAndSmallFontsAsRegular() ? label() : label().lessOn(1); }
  public static JBFont small() { return mediumAndSmallFontsAsRegular() ? label() : label().lessOn(2); }

  /**
   * In new UI {@link #small()} font should be used only in exceptional cases when the medium size won't fit at all.
   * This is a temporary method for backward compatibility with old UI, which returns {@link #medium()} for new UI
   * and {@link #small()} for old UI. Will be replaced by {@link #medium()} after full migration to new UI
   */
  @ApiStatus.Internal
  public static JBFont smallOrNewUiMedium() { return Registry.is("ide.experimental.ui") ? medium() : small(); }

  private static boolean mediumAndSmallFontsAsRegular() {
    return SystemInfo.isWindows && !Registry.is("ide.experimental.ui");
  }
}
