// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Eugene Zhuravlev
 */
public class ValueMarkup {
  private final String myText;
  private final Color myColor;
  private final @Nullable String myToolTipText;

  public ValueMarkup(final String text, final Color color, @Nullable String toolTipText) {
    myText = text;
    myColor = color;
    myToolTipText = toolTipText;
  }

  public @NotNull @NlsSafe String getText() {
    return myText;
  }

  public Color getColor() {
    return myColor;
  }

  public @Nullable String getToolTipText() {
    return myToolTipText;
  }
}
