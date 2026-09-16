// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.ui.Graphics2DDelegate;
import org.jetbrains.annotations.NotNull;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.font.GlyphVector;

/**
 * Rejects the character range that {@code SunGraphics2D.drawChars} rejects with "bad offset/length".
 * Text output is dropped, because the tests use a mock font service that a real graphics cannot render.
 */
final class CheckingGraphics extends Graphics2DDelegate {
  CheckingGraphics(@NotNull Graphics2D delegate) {
    super(delegate);
  }

  @Override
  public @NotNull Graphics create() {
    return new CheckingGraphics((Graphics2D)myDelegate.create());
  }

  @Override
  public void drawChars(char[] data, int offset, int length, int x, int y) {
    if (offset < 0 || length < 0 || offset + length > data.length) {
      throw new AssertionError("bad offset/length: offset=" + offset + ", length=" + length + ", text length=" + data.length);
    }
  }

  @Override
  public void drawGlyphVector(GlyphVector g, float x, float y) {
  }

  @Override
  public void drawString(@NotNull String str, int x, int y) {
  }

  @Override
  public void drawString(@NotNull String str, float x, float y) {
  }
}
