// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.render.RenderingUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public final class NamedColorUtil {
  public static @NotNull Color getErrorForeground() {
    return JBColor.namedColor("Label.errorForeground", new JBColor(new Color(0xC7222D), JBColor.RED));
  }

  public static @NotNull Color getInactiveTextColor() {
    return JBColor.namedColor("Component.infoForeground", new JBColor(Gray.x99, Gray.x78));
  }

  /**
   * @see RenderingUtil#getSelectionForeground(JList)
   */
  public static @NotNull Color getListSelectionForeground(boolean focused) {
    return JBUI.CurrentTheme.List.Selection.foreground(focused);
  }

  public static @NotNull Cursor getTextCursor(@NotNull Color backgroundColor) {
    return SystemInfoRt.isMac && ColorUtil.isDark(backgroundColor) ?
           MacUIUtil.getInvertedTextCursor() : Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
  }

  public static @NotNull Color getBoundsColor() {
    return JBColor.border();
  }
}
