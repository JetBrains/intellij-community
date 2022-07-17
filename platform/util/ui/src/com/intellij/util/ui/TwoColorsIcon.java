// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Vassiliy Kudryashov
 * @author Konstantin Bulenkov
 *
 * @deprecated use {@link ColorsIcon} instead
 */
@Deprecated(forRemoval = true)
public class TwoColorsIcon extends ColorsIcon {
  public TwoColorsIcon(int size, @Nullable Color color1, @Nullable Color secondColor) {
    super(size, color1, secondColor);
  }

  protected TwoColorsIcon(TwoColorsIcon icon) {
    super(icon);
  }

  @NotNull
  @Override
  public TwoColorsIcon copy() {
    return new TwoColorsIcon(this);
  }
}
