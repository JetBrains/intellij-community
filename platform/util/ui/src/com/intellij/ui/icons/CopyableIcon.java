// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.icons;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author tav
 */
public interface CopyableIcon extends Icon {
  /**
   * Returns a copy of this icon.
   * <p>
   * The copy should be a new instance, it should preserve the original size and should paint equal to the original bitmap.
   * The subtype of the new icon is up to an implementor. It may or may not be equal the original subtype.
   */
  @NotNull
  Icon copy();

  /**
   * Returns a copy of this icon (see {@link #copy()}) trying to deep-copy composited icons when applicable.
   */
  @NotNull
  default Icon deepCopy() {
    return copy();
  }
}
