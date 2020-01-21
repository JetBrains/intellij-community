// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.execution.process.ColoredOutputTypeRegistry;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.jediterm.terminal.emulator.ColorPalette;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class JBTerminalSchemeColorPalette extends ColorPalette {

  private static final int ANSI_INDEXED_COLOR_COUNT = 16;

  private final EditorColorsScheme myColorsScheme;

  protected JBTerminalSchemeColorPalette(@NotNull EditorColorsScheme scheme) {
    super();
    myColorsScheme = scheme;
  }

  @Override
  public Color[] getIndexColors() {
    Color[] result = new Color[ANSI_INDEXED_COLOR_COUNT + 2];
    for (int i = 0; i < ANSI_INDEXED_COLOR_COUNT; i++) {
      result[i] = myColorsScheme.getAttributes(ColoredOutputTypeRegistry.getAnsiColorKey(i)).getForegroundColor();
    }
    result[getDefaultForegroundIndex()] = myColorsScheme.getDefaultForeground();
    result[getDefaultBackgroundIndex()] = myColorsScheme.getDefaultBackground();
    return result;
  }

  static int getDefaultForegroundIndex() {
    return ANSI_INDEXED_COLOR_COUNT;
  }

  static int getDefaultBackgroundIndex() {
    return ANSI_INDEXED_COLOR_COUNT + 1;
  }
}
