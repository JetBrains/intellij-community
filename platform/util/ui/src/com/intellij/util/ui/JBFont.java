// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import javax.swing.plaf.UIResource;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class JBFont extends Font {
  private JBFont(Font font) {
    super(font);
  }

  public static JBFont create(Font font) {
    return create(font, true);
  }

  public static JBFont create(Font font, boolean tryToScale) {
    if (font instanceof JBFont) {
      return ((JBFont)font);
    }
    Font scaled = font;
    if (tryToScale) {
      scaled = font.deriveFont(font.getSize() * JBUI.scale(1f));
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
    return create(super.deriveFont(style, size), false);
  }

  @Override
  public JBFont deriveFont(float size) {
    return create(super.deriveFont(size), false);
  }

  public JBFont biggerOn(float size) {
    return deriveFont(getSize() + JBUI.scale(size));
  }

  public JBFont lessOn(float size) {
    return deriveFont(getSize() - JBUI.scale(size));
  }

  private static class JBFontUIResource extends JBFont implements UIResource {
    private JBFontUIResource(Font font) {
      super(font);
    }
  }
}
