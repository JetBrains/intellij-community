/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.terminal;

import com.intellij.execution.process.ColoredOutputTypeRegistry;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.jediterm.terminal.emulator.ColorPalette;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author traff
 */
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
