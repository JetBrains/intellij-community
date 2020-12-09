// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import java.awt.*;
import java.text.AttributedCharacterIterator;

public class EngravedTextGraphics extends Graphics2DDelegate {
  public static final Color SHADOW_COLOR = Gray._250.withAlpha(140);
  private static final boolean ALLOW_ENGRAVEMENT = false;
  private final Color myShadowColor;
  private final int myXOffset;
  private final int myYOffset;

  public EngravedTextGraphics(Graphics2D g2d) {
    this(g2d, 0, 1, SHADOW_COLOR);
  }

  public EngravedTextGraphics(Graphics2D g2d, int xoffset, int yoffset, Color shadowColor) {
    super(g2d);
    myXOffset = xoffset;
    myYOffset = yoffset;
    myShadowColor = shadowColor;
  }

  @Override
  public Graphics create() {
    return new EngravedTextGraphics((Graphics2D)myDelegate.create(), myXOffset, myYOffset, myShadowColor);
  }

  @Override
  public void drawChars(char[] data, int offset, int length, int x, int y) {
    if (ALLOW_ENGRAVEMENT) {
      final Color color = getColor();

      if (color != myShadowColor) {
        setColor(myShadowColor);
        super.drawChars(data, offset, length, x + myXOffset, y + myYOffset);

        setColor(color);
      }
    }

    super.drawChars(data, offset, length, x, y);
  }

  @Override
  public void drawString(String str, int x, int y) {
    if (ALLOW_ENGRAVEMENT) {
      final Color color = getColor();
      if (color != myShadowColor) {
        setColor(myShadowColor);
        super.drawString(str, x + myXOffset, y + myYOffset);

        setColor(color);
      }
    }
    super.drawString(str, x, y);
  }

  @Override
  public void drawString(AttributedCharacterIterator iterator, float x, float y) {
    if (ALLOW_ENGRAVEMENT) {
      final Color color = getColor();

      if (color != myShadowColor) {
        setColor(myShadowColor);
        super.drawString(iterator, x + myXOffset, y + myYOffset);

        setColor(color);
      }
    }
    super.drawString(iterator, x, y);
  }

  @Override
  public void drawString(AttributedCharacterIterator iterator, int x, int y) {
    if (ALLOW_ENGRAVEMENT) {
      final Color color = getColor();

      if (color != myShadowColor) {
        setColor(myShadowColor);
        super.drawString(iterator, x + myXOffset, y + myYOffset);

        setColor(color);
      }
    }
    super.drawString(iterator, x, y);
  }

  @Override
  public void drawString(String s, float x, float y) {
    if (ALLOW_ENGRAVEMENT) {
      final Color color = getColor();

      if (color != myShadowColor) {
        setColor(myShadowColor);
        super.drawString(s, x + myXOffset, y + myYOffset);

        setColor(color);
      }
    }
    super.drawString(s, x, y);
  }
}
