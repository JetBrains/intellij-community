// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame.presentation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Renders a value using default color. If you only need to show {@code value} and {@code type}
 * use {@link com.intellij.xdebugger.frame.XValueNode#setPresentation(javax.swing.Icon, String, String, boolean) setPresentation} method instead
 */
public class XRegularValuePresentation extends XValuePresentation {
  private final String myType;
  private final String myValue;
  private final String mySeparator;

  public XRegularValuePresentation(@NotNull String value, @Nullable String type) {
    this(value, type, DEFAULT_SEPARATOR);
  }

  public XRegularValuePresentation(@NotNull String value, @Nullable String type, final @NotNull String separator) {
    myValue = value;
    myType = type;
    mySeparator = separator;
  }

  @Override
  public String getType() {
    return myType;
  }

  @Override
  public @NotNull String getSeparator() {
    return mySeparator;
  }

  @Override
  public void renderValue(@NotNull XValueTextRenderer renderer) {
    renderer.renderValue(myValue);
  }
}
