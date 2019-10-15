// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Interface to work with ANSI-defined terminal colors.
 */
interface AnsiTerminalColor {
  /**
   * @return command part for serializing this color.
   * @implSpec this method MUST NOT return an escape sequence. Only the part that should be wrapped to {@link AnsiCommands#SGR_COMMAND_FG_COLOR_ENCODED} or
   * {@link AnsiCommands#SGR_COMMAND_BG_COLOR_ENCODED}. E.g. {@code 2;255;255;255 }
   */
  @NotNull
  String getAnsiEncodedColor();

  /**
   * @return a {@link Color color} representing this one or {@code null} if this is a pre-set configurable color 0-15.
   */
  @Nullable
  Color getColor();

  /**
   * @return a color index for this terminal color if available. Returns {@code -1} for pure RGB encoded color.
   */
  int getColorIndex();
}
