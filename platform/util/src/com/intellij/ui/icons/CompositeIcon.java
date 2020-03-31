// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.icons;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * An icon composing and painting a number of icons.
 */
public interface CompositeIcon extends Icon {
  /**
   * Returns the icon count.
   */
  int getIconCount();

  /**
   * Returns a composed icon by its {@code index} in the range {@code [0..count-1]}.
   *
   * @param index the icon number
   */
  @Nullable
  Icon getIcon(int index);
}
